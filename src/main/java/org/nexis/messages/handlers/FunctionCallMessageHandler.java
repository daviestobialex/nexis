/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.InterfaceErrorCodes;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.internal.MessageHandler;
import org.nexis.messages.FunctionCallResponseMessage;
import org.nexis.net.CircuitBreaker;
import org.nexis.net.HttpClientExecutor;
import org.nexis.net.RetryPolicy;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class FunctionCallMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(FunctionCallMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final Manifest manifest;
    private final CircuitBreaker circuitBreaker;
    private final RetryPolicy retryPolicy;

    // Optional override for tests to inject custom HTTP behavior
    @FunctionalInterface
    public interface SimpleHttpInvoker {

        HttpClientExecutor.HttpResponse execute(String method, String url, String body) throws Exception;
    }
    private final SimpleHttpInvoker httpInvoker;

    public FunctionCallMessageHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest) {
        this(params, builder, manifest, null);
    }

    public FunctionCallMessageHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest,
            SimpleHttpInvoker overrideInvoker) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.httpInvoker = overrideInvoker;
        // Initialize circuit breaker: 5 failures, 30s cooldown
        this.circuitBreaker = new CircuitBreaker("ReadOnlyApiCircuit", 5, java.time.Duration.ofSeconds(30));
        // Initialize retry policy: 3 attempts, 1s initial delay, 2x backoff, 10s max delay
        this.retryPolicy = new RetryPolicy(3, 1000, 2.0, 10000);
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasFunctionCall();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NexusProtocol.NexusMessage message = envelop.getMessage();
        NexusProtocol.Call call = message.getFunctionCall();

        // Extract call details
        String apiId = call.getApiId();
        byte[] requestPayload = call.getRequest().toByteArray();
        byte[] correlationId = call.getCorrelationId().toByteArray();

        // Get API metadata from factory
        org.nexis.base.ApiMetadataFactory metadataFactory = new org.nexis.base.ApiMetadataFactory(manifest);
        org.nexis.base.ApiMetadata metadata = metadataFactory.getApiMetadata(apiId);

        if (metadata == null) {
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SYSTEM_ERROR);
            return;
        }

        // Route based on API type
        switch (metadata.getType()) {
            case READ_ONLY ->
                handleReadOnlyApi(metadata, requestPayload, correlationId, ctx);
            case NON_TRANSACTIONAL ->
                handleNonTransactionalApi(metadata, requestPayload, correlationId, ctx);
            case TRANSACTIONAL ->
                handleTransactionalApi(metadata, requestPayload, correlationId, ctx, envelop);
            case PAYMENT_REQUIRED ->
                handlePaymentRequiredApi(metadata, requestPayload, correlationId, ctx, envelop);
            default ->
                sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SYSTEM_ERROR);
        }

    }

    /**
     * Handle READ_ONLY API calls with retry, exponential backoff, and circuit
     * breaker.
     *
     * <p>
     * This method implements resilience patterns:</p>
     * <ul>
     * <li><b>Circuit Breaker:</b> Fast-fail when downstream service is known to
     * be unhealthy</li>
     * <li><b>Retry with Exponential Backoff:</b> Automatic retry for transient
     * failures</li>
     * <li><b>Timeout Handling:</b> Proper detection and categorization of
     * timeout types</li>
     * </ul>
     *
     * <p>
     * No blockchain transaction is created - just forward the request and
     * return response.</p>
     *
     * @param metadata API metadata containing endpoint details
     * @param requestPayload request body bytes (may be null)
     * @param correlationId unique identifier for request-response correlation
     * @param ctx Netty channel context for sending response
     */
    private void handleReadOnlyApi(org.nexis.base.ApiMetadata metadata, byte[] requestPayload,
            byte[] correlationId, ChannelHandlerContext ctx) {

        // Check circuit breaker before attempting request
        if (!circuitBreaker.allowRequest()) {
            LOGGER.warning(String.format(
                    "Circuit breaker is OPEN for API '%s', rejecting request immediately",
                    metadata.getId()));
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SERVER_ERROR);
            return;
        }

        try {
            // Build URL from manifest base URL and endpoint path
            String baseUrl = manifest.getBaseUrl();
            String path = metadata.getId();
            String url = normalizeUrl(baseUrl, path);
            String requestBody = requestPayload != null ? new String(requestPayload) : null;

            // Execute with retry policy and exponential backoff
            HttpClientExecutor.HttpResponse httpResponse = retryPolicy.executeWithRetry(() -> {
                if (httpInvoker != null) {
                    return httpInvoker.execute(metadata.getHttpMethod(), url, requestBody);
                } else {
                    return manifest.getClientExecutor().executeSync(
                            metadata.getHttpMethod(),
                            url, null, requestBody);
                }
            }, String.format("%s %s", metadata.getHttpMethod(), path));

            if (httpResponse.isSuccess()) {
                // Success - record with circuit breaker and send response
                circuitBreaker.recordSuccess();
                sendSuccessResult(ctx, correlationId, httpResponse.getBody().getBytes());
                LOGGER.fine(String.format(
                        "Successfully executed READ_ONLY API: %s %s (status: %d)",
                        metadata.getHttpMethod(), path, httpResponse.getStatusCode()));
            } else {
                // Treat non-2xx as server error
                circuitBreaker.recordFailure();
                LOGGER.warning(String.format(
                        "Downstream returned HTTP %d for READ_ONLY API %s %s; sending error code",
                        httpResponse.getStatusCode(), metadata.getHttpMethod(), path));
                sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SERVER_ERROR);
            }

        } catch (TimeoutException e) {
            // Timeout from CompletableFuture.get() - all retries exhausted
            circuitBreaker.recordFailure();
            LOGGER.severe(String.format(
                    "Blocking timeout waiting for response after %d retry attempts for API '%s'",
                    retryPolicy.getMaxAttempts(), metadata.getId()));
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.TIME_OUT);

        } catch (ExecutionException e) {
            circuitBreaker.recordFailure();
            Throwable cause = e.getCause();

            if (cause instanceof io.netty.handler.timeout.ReadTimeoutException) {
                LOGGER.severe(String.format(
                        "Read timeout after %d retry attempts for API '%s'",
                        retryPolicy.getMaxAttempts(), metadata.getId()));
                sendErrorResult(ctx, correlationId, InterfaceErrorCodes.TIME_OUT);
            } else if (cause instanceof io.netty.channel.ConnectTimeoutException) {
                LOGGER.severe(String.format(
                        "Connection timeout after %d retry attempts for API '%s'",
                        retryPolicy.getMaxAttempts(), metadata.getId()));
                sendErrorResult(ctx, correlationId, InterfaceErrorCodes.TIME_OUT);
            } else {
                LOGGER.log(Level.SEVERE, String.format(
                        "Downstream API error after %d retry attempts for API '%s': %s",
                        retryPolicy.getMaxAttempts(), metadata.getId(),
                        cause != null ? cause.getMessage() : "unknown"), e);
                sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SERVER_ERROR);
            }

        } catch (InterruptedException e) {
            // Don't record as circuit breaker failure - thread interruption is external signal
            Thread.currentThread().interrupt();
            LOGGER.warning(String.format(
                    "Thread interrupted while executing API '%s'", metadata.getId()));
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SYSTEM_ERROR);

        } catch (Exception e) {
            // Catch any other unexpected exceptions from retry mechanism
            circuitBreaker.recordFailure();
            LOGGER.log(Level.SEVERE, String.format(
                    "Unexpected error executing API '%s': %s",
                    metadata.getId(), e.getMessage()), e);
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SYSTEM_ERROR);
        }
    }

    private String normalizeUrl(String baseUrl, String path) {
        if (baseUrl == null) {
            baseUrl = "";
        }
        if (path == null) {
            path = "";
        }
        boolean baseEndsWithSlash = baseUrl.endsWith("/");
        boolean pathStartsWithSlash = path.startsWith("/");
        if (baseEndsWithSlash && pathStartsWithSlash) {
            return baseUrl + path.substring(1);
        } else if (!baseEndsWithSlash && !pathStartsWithSlash) {
            return baseUrl + "/" + path;
        } else {
            return baseUrl + path;
        }
    }

    /**
     * Handle TRANSACTIONAL API calls. Creates blockchain transaction with
     * request/response as proof-of-service. No payment required, but activity
     * is recorded on-chain.
     */
    private void handleTransactionalApi(org.nexis.base.ApiMetadata metadata, byte[] requestPayload,
            byte[] correlationId, ChannelHandlerContext ctx,
            NexusProtocol.NexusEnvelop envelop) {
        try {
            // TODO: Execute actual HTTP call using manifest.getClientExecutor()
            byte[] responsePayload = "Transactional API executed".getBytes();

            // Send immediate response to caller via network
            sendSuccessResult(ctx, correlationId, responsePayload);

            // Asynchronously create blockchain transaction for proof-of-service
            createProofOfServiceTransaction(metadata, requestPayload, responsePayload, envelop);

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Error executing transactional API", e);
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SERVER_ERROR);
        }
    }

    /**
     * Handle NON_TRANSACTIONAL API calls. These process requests but are not
     * recorded on-chain (no payment/fees, off-chain business processing).
     */
    private void handleNonTransactionalApi(org.nexis.base.ApiMetadata metadata, byte[] requestPayload,
            byte[] correlationId, ChannelHandlerContext ctx) {
        try {
            // TODO: Execute actual HTTP call using manifest.getClientExecutor()
            LOGGER.log(java.util.logging.Level.INFO,
                    "Non-transactional API call: {0} {1} bytes",
                    new Object[]{metadata.getId(), requestPayload != null ? requestPayload.length : 0});

            // No blockchain record; simply return the response
            sendSuccessResult(ctx, correlationId, "Non-transactional API executed".getBytes());

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Error executing non-transactional API", e);
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SERVER_ERROR);
        }
    }

    /**
     * Handle PAYMENT_REQUIRED API calls. 1. Validate caller has sufficient
     * funds 2. Create payment transaction (debit from caller to callee) 3.
     * Execute API call 4. Write transaction to blockchain with request/response
     * 5. Split fees with governance nodes
     */
    private void handlePaymentRequiredApi(org.nexis.base.ApiMetadata metadata, byte[] requestPayload,
            byte[] correlationId, ChannelHandlerContext ctx,
            NexusProtocol.NexusEnvelop envelop) {
        try {
            org.nexis.base.Coin cost = metadata.getCost();

            // TODO: Get caller's identity/address from envelop
            // TODO: Validate caller has sufficient balance (check UTXO set)
            // For now, assume validation passes
            // Create payment transaction BEFORE executing API
            // This ensures caller pays upfront
            org.nexis.core.Transaction paymentTx = createPaymentTransaction(
                    metadata, requestPayload, cost, envelop);

            if (paymentTx == null) {
                sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SERVER_ERROR);
                return;
            }

            // Execute HTTP request to downstream API (caller has already paid)
            // TODO: Execute actual HTTP call using manifest.getClientExecutor()
            byte[] responsePayload = "Payment-required API executed".getBytes();

            // Send immediate response to caller via network
            sendSuccessResult(ctx, correlationId, responsePayload);

            // Update transaction with response payload and broadcast to network
            // TODO: Add response as transaction output
            // TODO: Broadcast transaction to network for mining
            LOGGER.log(java.util.logging.Level.INFO,
                    "Payment-required API executed. Cost: {0}, EnvPayload: {1}",
                    new Object[]{cost, envelop.getMessage().getPayloadCase().name()});

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Error executing payment-required API", e);
            sendErrorResult(ctx, correlationId, InterfaceErrorCodes.SERVER_ERROR);
        }
    }

    /**
     * Create a proof-of-service transaction for transactional APIs. Transaction
     * has zero value but records the API interaction on-chain.
     */
    private void createProofOfServiceTransaction(org.nexis.base.ApiMetadata metadata,
            byte[] requestPayload,
            byte[] responsePayload,
            NexusProtocol.NexusEnvelop envelop) {
        try {
            // TODO: Create transaction with:
            // - Input: caller's identity/signature
            // - Output 1: Request payload (value = 0, manifest metadata)
            // - Output 2: Response payload (value = 0)
            // - Broadcast to network for inclusion in blockchain

            LOGGER.log(java.util.logging.Level.INFO,
                    "Created proof-of-service tx for API: {0} (req {1}B, res {2}B, payload {3})",
                    new Object[]{metadata.getId(),
                        requestPayload != null ? requestPayload.length : 0,
                        responsePayload != null ? responsePayload.length : 0,
                        envelop.getMessage().getPayloadCase().name()});

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to create proof-of-service transaction", e);
        }
    }

    /**
     * Create a payment transaction for payment-required APIs. Debits cost from
     * caller and credits to callee (service provider). Splits fees with
     * governance nodes that approved the callee's manifest.
     */
    private org.nexis.core.Transaction createPaymentTransaction(org.nexis.base.ApiMetadata metadata,
            byte[] requestPayload,
            org.nexis.base.Coin cost,
            NexusProtocol.NexusEnvelop envelop) {
        try {
            // TODO: Get caller's address from envelop
            // TODO: Find caller's UTXOs with sufficient balance
            // TODO: Create transaction:
            // - Input: caller's UTXO (value >= cost)
            // - Output 1: Payment to service provider (cost - fees)
            // - Output 2: Fee split to governance nodes (governance fee)
            // - Output 3: Change back to caller (if UTXO value > cost)
            // - Output 4: Request payload (manifest metadata)
            // TODO: Sign transaction with caller's key
            // TODO: Validate and return

            LOGGER.log(java.util.logging.Level.INFO,
                    "Created payment transaction for API: {0}, Cost: {1}, RequestBytes: {2}, Payload: {3}",
                    new Object[]{metadata.getId(), cost, requestPayload != null ? requestPayload.length : 0,
                        envelop.getMessage().getPayloadCase().name()});

            return null; // Placeholder

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE,
                    "Failed to create payment transaction", e);
            return null;
        }
    }

    /**
     * Send success result back to caller
     *
     * @param ctx
     * @param correlationId
     * @param responseData
     */
    private void sendSuccessResult(ChannelHandlerContext ctx, byte[] correlationId, byte[] responseData) {
        try {
            NexusProtocol.Result result = NexusProtocol.Result.newBuilder()
                    .setStatus(0) // 0 = OK
                    .setMessage("Success")
                    .setResponseData(com.google.protobuf.ByteString.copyFrom(responseData))
                    .setCorrelationId(com.google.protobuf.ByteString.copyFrom(correlationId))
                    .build();

            FunctionCallResponseMessage message
                    = new FunctionCallResponseMessage(
                            NexusNetworkConfiguration.of(params.getNetwork()),
                            result, correlationId);

            ctx.writeAndFlush(builder.build(message));

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to send success result", e);
        }
    }

    /**
     * Send error code result back to caller
     *
     * @param ctx
     * @param correlationId
     * @param errorCode
     */
    private void sendErrorResult(ChannelHandlerContext ctx, byte[] correlationId, int errorCode) {
        try {
            NexusProtocol.Result result = NexusProtocol.Result.newBuilder()
                    .setStatus(1) // 1 = Error
                    .setErrorCode(errorCode)
                    .setCorrelationId(com.google.protobuf.ByteString.copyFrom(correlationId))
                    .build();

            FunctionCallResponseMessage message
                    = new FunctionCallResponseMessage(
                            NexusNetworkConfiguration.of(params.getNetwork()),
                            result, correlationId);

            ctx.writeAndFlush(builder.build(message));

        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Failed to send error result", e);
        }
    }

    @Override
    public void sendMessage() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
