/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import io.netty.handler.timeout.ReadTimeoutHandler;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLException;

/**
 * High-performance HTTP client executor built on Netty for executing RESTful
 * API calls in the Nexis blockchain network. This class provides both
 * synchronous and asynchronous HTTP execution capabilities with configurable
 * timeouts, SSL/TLS support, and robust error handling.
 *
 * <h2>Architecture</h2>
 * <p>
 * Uses Netty's non-blocking I/O model with a dedicated event loop group for
 * concurrent request processing. Each request is executed in its own channel
 * with independent timeout tracking and lifecycle management.</p>
 *
 * <h2>Timeout Handling</h2>
 * <p>
 * Implements three types of timeout detection:</p>
 * <ul>
 * <li><b>Connect Timeout:</b> Maximum time allowed to establish TCP
 * connection</li>
 * <li><b>Read Timeout:</b> Maximum time allowed waiting for server response
 * after connection</li>
 * <li><b>Channel Close:</b> Detects premature channel closure before response
 * completion</li>
 * </ul>
 *
 * <h2>SSL/TLS Support</h2>
 * <p>
 * Automatically detects HTTPS URLs and configures SSL context with
 * InsecureTrustManager (suitable for development/testing; production should use
 * proper certificate validation).</p>
 *
 * <h2>Error Propagation</h2>
 * <p>
 * All errors are propagated through CompletableFuture exceptional completion,
 * including:  {@link ConnectTimeoutException}, {@link io.netty.handler.timeout.ReadTimeoutException},
 * {@link TimeoutException} for channel closure, and HTTP-level errors.</p>
 *
 * <h2>Thread Safety</h2>
 * <p>
 * This class is thread-safe. Multiple concurrent requests can be issued safely.
 * The internal worker group manages thread pooling (default 4 threads).</p>
 *
 * <h2>Resource Management</h2>
 * <p>
 * Must call {@link #shutdown()} when done to release Netty resources
 * gracefully. Failure to shutdown will prevent JVM termination.</p>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * HttpClientExecutor executor = new HttpClientExecutor(5000, 10000);
 * try {
 *     HttpResponse response = executor.executeSync("GET",
 *         "https://api.example.com/data", null, null);
 *     if (response.isSuccess()) {
 *         String body = response.getBody();
 *         // process response
 *     }
 * } finally {
 *     executor.shutdown();
 * }
 * }</pre>
 *
 * @author daviestobialex
 * @see io.netty.bootstrap.Bootstrap
 * @see java.util.concurrent.CompletableFuture
 */
public final class HttpClientExecutor {

    private static final Logger LOGGER = Logger.getLogger(HttpClientExecutor.class.getName());

    /**
     * Netty event loop group managing I/O threads for all HTTP requests. Shared
     * across all connections from this executor instance.
     */
    private final EventLoopGroup workerGroup;

    /**
     * Maximum time in milliseconds to wait for TCP connection establishment. If
     * connection is not established within this time,
     * {@link ConnectTimeoutException} is thrown.
     */
    private final int connectTimeoutMs;

    /**
     * Maximum time in milliseconds to wait for server response after connection
     * is established. If response is not received within this time,
     * {@link io.netty.handler.timeout.ReadTimeoutException} is thrown.
     */
    private final int readTimeoutMs;

    /**
     * Creates a new HTTP client executor with default timeout values.
     * <p>
     * Default configuration:</p>
     * <ul>
     * <li>Connect timeout: 30 seconds</li>
     * <li>Read timeout: 60 seconds</li>
     * <li>Worker threads: 4</li>
     * </ul>
     *
     * <p>
     * <b>Note:</b> Suitable for most API call scenarios. For high-latency
     * networks or large response payloads, consider using the parameterized
     * constructor with custom timeouts.</p>
     */
    public HttpClientExecutor() {
        this(30000, 60000); // 30s connect, 60s read
    }

    /**
     * Creates a new HTTP client executor with custom timeout configuration.
     *
     * @param connectTimeoutMs maximum time in milliseconds to wait for
     * connection establishment. Typical values: 5000-30000ms. Lower values fail
     * faster but may be too aggressive for high-latency networks.
     * @param readTimeoutMs maximum time in milliseconds to wait for server
     * response after connection. Typical values: 10000-120000ms. Should account
     * for server processing time and response payload size.
     * @throws IllegalArgumentException if timeout values are negative (not
     * currently enforced but recommended)
     */
    public HttpClientExecutor(int connectTimeoutMs, int readTimeoutMs) {
        this.workerGroup = new NioEventLoopGroup(4);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Executes an HTTP request synchronously, blocking until response is
     * received or timeout occurs.
     *
     * <p>
     * This method delegates to
     * {@link #executeAsync(String, String, Map, String)} and blocks on the
     * returned CompletableFuture using
     * {@link CompletableFuture#get(long, TimeUnit)} with the configured read
     * timeout.</p>
     *
     * <p>
     * <b>Blocking Behavior:</b> The calling thread will block for up to
     * {@code readTimeoutMs} milliseconds. Use {@link #executeAsync} for
     * non-blocking execution.</p>
     *
     * <p>
     * <b>Timeout Resolution:</b></p>
     * <ul>
     * <li>Connect timeout: Enforced by Netty's
     * ChannelOption.CONNECT_TIMEOUT_MILLIS</li>
     * <li>Read timeout: Enforced by ReadTimeoutHandler in pipeline</li>
     * <li>Overall timeout: Enforced by CompletableFuture.get() timeout
     * parameter</li>
     * </ul>
     *
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH, etc.).
     * Case-insensitive. Must be a valid HTTP method recognized by
     * {@link HttpMethod#valueOf(String)}.
     * @param url Full URL including scheme (http:// or https://), host,
     * optional port, path, and query. Examples:
     * "https://api.example.com/v1/users?page=1", "http://localhost:8080/health"
     * @param headers Optional HTTP headers as key-value map. May be
     * {@code null}. Standard headers (Host, Connection, Accept, Content-Type,
     * Content-Length) are set automatically and may be overridden by values in
     * this map.
     * @param body Optional request body as UTF-8 encoded string. May be
     * {@code null}. Automatically sets Content-Type to "application/json" if
     * non-null.
     * @return HTTP response object containing status code, headers, and body.
     * Never {@code null}. Check {@link HttpResponse#isSuccess()} for 2xx status
     * codes.
     * @throws InterruptedException if the current thread is interrupted while
     * waiting for response. The interrupted status of the thread is preserved.
     * @throws ExecutionException if the HTTP request fails due to:
     * <ul>
     * <li>Network errors (connection refused, host unreachable)</li>
     * <li>SSL/TLS handshake failures</li>
     * <li>Write failures when sending request</li>
     * <li>Read timeout (wrapped
     * {@link io.netty.handler.timeout.ReadTimeoutException})</li>
     * <li>Connect timeout (wrapped {@link ConnectTimeoutException})</li>
     * </ul>
     * Use {@link ExecutionException#getCause()} to inspect the underlying
     * failure.
     * @throws TimeoutException if no response is received within
     * {@code readTimeoutMs} milliseconds after initiating the request. This is
     * the blocking timeout from {@link CompletableFuture#get(long, TimeUnit)},
     * distinct from Netty's read timeout (which manifests as
     * ExecutionException).
     * @see #executeAsync(String, String, Map, String)
     */
    public HttpResponse executeSync(String method, String url,
            Map<String, String> headers, String body)
            throws InterruptedException,
            ExecutionException,
            TimeoutException {

        CompletableFuture<HttpResponse> future = executeAsync(method, url, headers, body);
        return future.get(readTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Executes an HTTP request asynchronously, returning immediately with a
     * CompletableFuture.
     *
     * <p>
     * This is the core execution method. The request is processed entirely
     * asynchronously in Netty's event loop threads, allowing the calling thread
     * to continue without blocking.</p>
     *
     * <p>
     * <b>Execution Flow:</b></p>
     * <ol>
     * <li>Parse URL and determine if SSL is required</li>
     * <li>Create SSL context if HTTPS</li>
     * <li>Build HTTP request with headers and body</li>
     * <li>Bootstrap Netty channel with timeout handlers</li>
     * <li>Connect to server (async)</li>
     * <li>Send request (async)</li>
     * <li>Attach channel close listener for premature closure detection</li>
     * <li>Wait for response in HttpResponseHandler</li>
     * <li>Complete future with response or exception</li>
     * </ol>
     *
     * <p>
     * <b>Timeout Detection:</b></p>
     * <ul>
     * <li><b>Connect timeout:</b> If connection not established in
     * {@code connectTimeoutMs}, future completes exceptionally with
     * {@link ConnectTimeoutException}</li>
     * <li><b>Read timeout:</b> If no response received in {@code readTimeoutMs}
     * after connection, future completes exceptionally with
     * {@link io.netty.handler.timeout.ReadTimeoutException}</li>
     * <li><b>Channel close:</b> If channel closes before response (e.g., server
     * closes connection early), future completes exceptionally with
     * {@link TimeoutException} message "Channel closed before response
     * received"</li>
     * </ul>
     *
     * <p>
     * <b>Error Handling:</b> All errors result in exceptional completion of the
     * returned future. Use
     * {@link CompletableFuture#exceptionally}, {@link CompletableFuture#handle},
     * or {@link CompletableFuture#whenComplete} to handle errors.</p>
     *
     * <p>
     * <b>Example Usage:</b></p>
     * <pre>{@code
     * executor.executeAsync("POST", "https://api.example.com/orders",
     *         Map.of("Authorization", "Bearer token"),
     *         "{\"item\":\"widget\"}")
     *     .thenAccept(response -> {
     *         if (response.isSuccess()) {
     *             System.out.println("Order created: " + response.getBody());
     *         } else {
     *             System.err.println("Failed: " + response.getStatusCode());
     *         }
     *     })
     *     .exceptionally(ex -> {
     *         System.err.println("Request failed: " + ex.getMessage());
     *         return null;
     *     });
     * }</pre>
     *
     * @param method HTTP method (GET, POST, PUT, DELETE, PATCH, HEAD, OPTIONS,
     * TRACE). Case-insensitive.
     * @param url Full URL with scheme (http:// or https://), host, optional
     * port, path, and query string. Port defaults to 80 for HTTP and 443 for
     * HTTPS if not specified.
     * @param headers Optional map of HTTP headers. May be {@code null}. Keys
     * and values are case-sensitive per HTTP specification. Standard headers
     * are set automatically but can be overridden.
     * @param body Optional request body as UTF-8 string. May be {@code null}.
     * Automatically triggers Content-Type: application/json and Content-Length
     * headers when non-null.
     * @return CompletableFuture that completes with {@link HttpResponse} on
     * success or completes exceptionally on failure. The future is never
     * {@code null}.
     * <ul>
     * <li><b>Success:</b> Future completes with HttpResponse (any status code,
     * including 4xx/5xx)</li>
     * <li><b>Failure:</b> Future completes exceptionally with:
     * <ul>
     * <li>{@link URISyntaxException} - malformed URL</li>
     * <li>{@link SSLException} - SSL context creation failed</li>
     * <li>{@link ConnectTimeoutException} - connection timeout</li>
     * <li>{@link io.netty.handler.timeout.ReadTimeoutException} - read
     * timeout</li>
     * <li>{@link TimeoutException} - channel closed prematurely</li>
     * <li>{@link java.io.IOException} - network I/O errors</li>
     * </ul>
     * </li>
     * </ul>
     * @see #executeSync(String, String, Map, String)
     * @see HttpResponse
     */
    public CompletableFuture<HttpResponse> executeAsync(String method, String url,
            Map<String, String> headers, String body) {

        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();

            if (port == -1) {
                port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            }

            boolean ssl = "https".equalsIgnoreCase(scheme);

            LOGGER.log(Level.INFO, "Executing {0} {1} (SSL: {2})", new Object[]{method, url, ssl});

            // Create SSL context if needed
            SslContext sslContext = null;
            if (ssl) {
                sslContext = SslContextBuilder.forClient()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE)
                        .build();
            }

            // Build request
            var request = buildRequest(method, uri, headers, body);

            // Connect and execute
            connectAndExecute(host, port, ssl, sslContext, request, responseFuture);

        } catch (URISyntaxException | SSLException e) {
            responseFuture.completeExceptionally(e);
        }

        return responseFuture;
    }

    /**
     * Constructs a complete HTTP request with headers and body from provided
     * parameters.
     *
     * <p>
     * Builds a {@link FullHttpRequest} suitable for Netty HTTP pipeline
     * processing. Automatically handles:</p>
     * <ul>
     * <li>Path construction including query string</li>
     * <li>Standard HTTP headers (Host, Connection, Accept, Content-Type,
     * Content-Length)</li>
     * <li>Request body encoding (UTF-8)</li>
     * <li>Custom header merging</li>
     * </ul>
     *
     * <p>
     * <b>Automatic Headers:</b></p>
     * <ul>
     * <li><b>Host:</b> Extracted from URI</li>
     * <li><b>Connection:</b> Set to "close" (non-persistent connections)</li>
     * <li><b>Accept:</b> Set to "application/json"</li>
     * <li><b>Content-Type:</b> Set to "application/json" if body is
     * non-null</li>
     * <li><b>Content-Length:</b> Calculated from body bytes or set to 0</li>
     * </ul>
     *
     * @param method HTTP method (GET, POST, etc.) - converted to uppercase
     * @param uri parsed URI containing scheme, host, port, path, and query
     * @param headers optional custom headers to merge with automatic headers.
     * May be {@code null}. Custom headers override automatic headers if keys
     * match.
     * @param body optional request body as string. May be {@code null}. Encoded
     * as UTF-8.
     * @return fully constructed HTTP request ready for transmission. Never
     * {@code null}.
     */
    private FullHttpRequest buildRequest(String method, URI uri,
            Map<String, String> headers, String body) {

        // Build path with query
        String pathWithQuery = uri.getRawPath();
        if (uri.getRawQuery() != null) {
            pathWithQuery += "?" + uri.getRawQuery();
        }
        if (pathWithQuery.isEmpty()) {
            pathWithQuery = "/";
        }

        // Create request
        HttpMethod httpMethod = HttpMethod.valueOf(method.toUpperCase());
        ByteBuf content = body != null
                ? Unpooled.copiedBuffer(body, StandardCharsets.UTF_8) : Unpooled.EMPTY_BUFFER;

        FullHttpRequest request = new DefaultFullHttpRequest(
                HttpVersion.HTTP_1_1,
                httpMethod,
                pathWithQuery,
                content
        );

        // Set headers
        request.headers().set(HttpHeaderNames.HOST, uri.getHost());
        request.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.CLOSE);
        request.headers().set(HttpHeaderNames.ACCEPT, "application/json");

        if (body != null) {
            request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, content.readableBytes());
        } else {
            request.headers().set(HttpHeaderNames.CONTENT_LENGTH, 0);
        }

        // Add custom headers
        if (headers != null) {
            headers.forEach((k, v) -> request.headers().set(k, v));
        }

        return request;
    }

    /**
     * Establishes connection to target server and executes the HTTP request
     * asynchronously.
     *
     * <p>
     * This method orchestrates the complete Netty channel lifecycle:</p>
     * <ol>
     * <li>Bootstrap Netty channel with NIO selector</li>
     * <li>Configure channel pipeline with handlers:
     * <ul>
     * <li>SSL handler (if HTTPS)</li>
     * <li>HTTP codec (request encoder + response decoder)</li>
     * <li>Content decompressor (gzip, deflate)</li>
     * <li>HTTP aggregator (assembles chunked responses, max 1MB)</li>
     * <li>Read timeout handler</li>
     * <li>Custom response handler</li>
     * </ul>
     * </li>
     * <li>Initiate asynchronous connection with timeout</li>
     * <li>On successful connection:
     * <ul>
     * <li>Write and flush HTTP request</li>
     * <li>Attach channel close listener for premature closure detection</li>
     * </ul>
     * </li>
     * <li>On connection failure: Complete future exceptionally</li>
     * </ol>
     *
     * <p>
     * <b>Channel Close Detection:</b> A {@link ChannelFuture#closeFuture()}
     * listener is attached to detect if the channel closes before a response is
     * received. This handles cases where:
     * <ul>
     * <li>Server closes connection immediately after accepting (e.g.,
     * overload)</li>
     * <li>Network interruption occurs mid-request</li>
     * <li>Server crashes before sending response</li>
     * </ul>
     * In such cases, the response future completes exceptionally with
     * {@link TimeoutException}.</p>
     *
     * <p>
     * <b>Write Failure Handling:</b> If the request write fails (e.g., channel
     * closed during write), the future is completed exceptionally and the
     * channel is closed immediately.</p>
     *
     * <p>
     * <b>Thread Model:</b> All operations execute in Netty's event loop
     * threads. The calling thread returns immediately after initiating the
     * connection attempt.</p>
     *
     * @param host target server hostname or IP address
     * @param port target server port (typically 80 for HTTP, 443 for HTTPS)
     * @param ssl {@code true} if HTTPS/SSL should be used, {@code false} for
     * plain HTTP
     * @param sslContext SSL context for HTTPS connections, or {@code null} if
     * SSL is {@code false}. Must be non-null if {@code ssl} is {@code true}.
     * @param request fully constructed HTTP request to send
     * @param responseFuture future to complete with response or exception. Must
     * not be {@code null}.
     */
    private void connectAndExecute(String host, int port, boolean ssl, SslContext sslContext,
            FullHttpRequest request, CompletableFuture<HttpResponse> responseFuture) {

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(workerGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();

                        // SSL handler
                        if (ssl && sslContext != null) {
                            pipeline.addLast(sslContext.newHandler(ch.alloc(), host, port));
                        }

                        // HTTP codec
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpContentDecompressor(0));
                        pipeline.addLast(new HttpObjectAggregator(1048576)); // 1MB max response TODO: increase here

                        // Timeout handler
                        pipeline.addLast(new ReadTimeoutHandler(readTimeoutMs, TimeUnit.MILLISECONDS));

                        // Response handler
                        pipeline.addLast(new HttpResponseHandler(responseFuture));
                    }
                });

        // Connect
        ChannelFuture connectFuture = bootstrap.connect(host, port);
        connectFuture.addListener((ChannelFutureListener) future -> {
            if (future.isSuccess()) {
                LOGGER.log(Level.INFO, "Connected to {0}:{1}", new Object[]{host, port});
                // Send request
                future.channel().writeAndFlush(request).addListener((ChannelFutureListener) writeFuture -> {
                    if (!writeFuture.isSuccess()) {
                        responseFuture.completeExceptionally(writeFuture.cause());
                        future.channel().close();
                    }
                });
                // If the channel closes before we have a response, complete exceptionally.
                future.channel().closeFuture().addListener(cf -> {
                    if (!responseFuture.isDone()) {
                        responseFuture.completeExceptionally(new TimeoutException("Channel closed before response received"));
                    }
                });
            } else {
                responseFuture.completeExceptionally(future.cause());
            }
        });
    }

    /**
     * Netty channel handler responsible for processing HTTP responses and
     * completing the response future.
     *
     * <p>
     * This handler is the final stage in the Netty pipeline and receives fully
     * aggregated HTTP responses (assembled by {@link HttpObjectAggregator}). It
     * extracts response metadata and body, constructs an {@link HttpResponse}
     * object, and completes the associated {@link CompletableFuture}.</p>
     *
     * <p>
     * <b>Lifecycle Events Handled:</b></p>
     * <ul>
     * <li><b>channelRead0:</b> Invoked when complete HTTP response is
     * received</li>
     * <li><b>exceptionCaught:</b> Invoked on any pipeline exception (timeout,
     * I/O error, codec error)</li>
     * <li><b>channelInactive:</b> Invoked when channel closes (detects
     * premature closure)</li>
     * </ul>
     *
     * <p>
     * <b>Idempotency:</b> All completion methods check
     * {@link CompletableFuture#isDone()} before attempting completion, ensuring
     * the future is completed exactly once even if multiple events fire (e.g.,
     * exception followed by channel close).</p>
     *
     * <p>
     * <b>Thread Safety:</b> All methods execute in the same Netty event loop
     * thread for this channel, so no additional synchronization is
     * required.</p>
     */
    private static class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        /**
         * Future to complete with HTTP response or exception. Shared reference
         * between handler instance and calling code.
         */
        private final CompletableFuture<HttpResponse> responseFuture;

        /**
         * Constructs a new response handler bound to the given future.
         *
         * @param responseFuture future to complete when response is received or
         * error occurs. Must not be {@code null}.
         */
        HttpResponseHandler(CompletableFuture<HttpResponse> responseFuture) {
            this.responseFuture = responseFuture;
        }

        /**
         * Handles incoming HTTP response by extracting metadata and completing
         * the response future.
         *
         * <p>
         * This method is invoked by Netty when a complete
         * {@link FullHttpResponse} has been received and aggregated by the
         * pipeline. The response is guaranteed to be complete (headers + body)
         * at this point.</p>
         *
         * <p>
         * <b>Processing Steps:</b></p>
         * <ol>
         * <li>Extract status code and reason phrase</li>
         * <li>Copy all response headers into mutable map</li>
         * <li>Decode response body from ByteBuf to UTF-8 string</li>
         * <li>Construct HttpResponse wrapper object</li>
         * <li>Complete the response future (normal completion)</li>
         * <li>Close the channel (Connection: close)</li>
         * </ol>
         *
         * <p>
         * <b>Success Semantics:</b> This method is called for ALL HTTP
         * responses, including error status codes (4xx, 5xx). HTTP-level errors
         * are not treated as exceptions; they result in normal future
         * completion with the error status in the response object. Use
         * {@link HttpResponse#isSuccess()} to check for 2xx status codes.</p>
         *
         * <p>
         * <b>Character Encoding:</b> Response body is always decoded as UTF-8.
         * For other encodings, the body bytes would need to be accessed
         * directly from {@code msg.content()}.</p>
         *
         * @param ctx channel context for this connection
         * @param msg fully aggregated HTTP response including headers and body.
         * Never {@code null}.
         */
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) {
            // Extract response
            int statusCode = msg.status().code();
            String statusMessage = msg.status().reasonPhrase();

            // Extract headers
            Map<String, String> headers = new java.util.HashMap<>();
            msg.headers().forEach(entry -> headers.put(entry.getKey(), entry.getValue()));

            // Extract body
            String body = msg.content().toString(StandardCharsets.UTF_8);

            // Create response object
            HttpResponse response = new HttpResponse(statusCode, statusMessage, headers, body);

            // Complete future
            responseFuture.complete(response);

            // Close connection
            ctx.close();
        }

        /**
         * Handles exceptions that occur anywhere in the channel pipeline during
         * request/response processing.
         *
         * <p>
         * This method is invoked when any handler in the pipeline throws an
         * exception or when Netty detects a protocol/network error. Common
         * exceptions include:</p>
         * <ul>
         * <li>{@link io.netty.handler.timeout.ReadTimeoutException} - no
         * response within read timeout</li>
         * <li>{@link io.netty.handler.codec.DecoderException} - invalid HTTP
         * response format</li>
         * <li>{@link java.io.IOException} - network I/O errors (connection
         * reset, broken pipe)</li>
         * <li>{@link javax.net.ssl.SSLException} - SSL/TLS handshake or
         * protocol errors</li>
         * </ul>
         *
         * <p>
         * The exception is propagated to the calling code by completing the
         * response future exceptionally, then the channel is closed immediately
         * to release resources.</p>
         *
         * <p>
         * <b>Logging:</b> Exception is logged at INFO level (not SEVERE) to
         * avoid polluting logs with expected timeout scenarios. Calling code is
         * responsible for deciding error severity.</p>
         *
         * @param ctx channel context for this connection
         * @param cause exception that occurred in the pipeline. Never
         * {@code null}.
         */
        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            LOGGER.log(Level.INFO, "HTTP client error {0}", cause);
            responseFuture.completeExceptionally(cause);
            ctx.close();
        }

        /**
         * Handles premature channel closure when no response has been received.
         *
         * <p>
         * This method is invoked by Netty when the channel transitions to
         * inactive state, which occurs when:</p>
         * <ul>
         * <li>Remote server closes the connection</li>
         * <li>Network connection is lost</li>
         * <li>Local channel is closed programmatically</li>
         * </ul>
         *
         * <p>
         * <b>Premature Closure Detection:</b> If the response future is not yet
         * done when this method executes, it indicates the channel closed
         * before a complete response was received. This is an error condition
         * that should fail the request.</p>
         *
         * <p>
         * The future is completed exceptionally with {@link TimeoutException}
         * to distinguish this scenario from other timeout types (connect
         * timeout, read timeout). The exception message "Channel closed before
         * response received" provides context.</p>
         *
         * <p>
         * <b>Race Condition Safety:</b> The {@code isDone()} check prevents
         * duplicate completion if a response was received just before closure,
         * or if {@link #exceptionCaught} already completed the future.</p>
         *
         * <p>
         * <b>Use Case:</b> This commonly occurs when:
         * <ul>
         * <li>Server is overloaded and refuses the connection after
         * accepting</li>
         * <li>Server crashes mid-request</li>
         * <li>Load balancer closes idle connections aggressively</li>
         * <li>Firewall or proxy terminates connection</li>
         * </ul>
         *
         * @param ctx channel context for this connection
         * @throws Exception if parent class throws (propagated per Netty
         * contract)
         */
        @Override
        public void channelInactive(ChannelHandlerContext ctx) throws Exception {
            // Channel closed without receiving a full response.
            if (!responseFuture.isDone()) {
                responseFuture.completeExceptionally(new TimeoutException("Channel closed before response received"));
            }
            super.channelInactive(ctx);
        }
    }

    /**
     * Immutable value object representing a complete HTTP response.
     *
     * <p>
     * Encapsulates all components of an HTTP response: status line, headers,
     * and body. Instances are created by {@link HttpResponseHandler} after
     * receiving a complete response from the server.</p>
     *
     * <p>
     * <b>Immutability:</b> All fields are final and the headers map is captured
     * at construction. The headers map itself is mutable (HashMap), but the
     * reference is immutable. For full immutability, callers should not modify
     * the returned headers map.</p>
     *
     * <p>
     * <b>Success Determination:</b> HTTP responses with status codes 200-299
     * are considered successful. Status codes 4xx (client error) and 5xx
     * (server error) are NOT treated as exceptions by the HTTP client; they
     * result in normal completion with this response object. Use
     * {@link #isSuccess()} to check status category.</p>
     *
     * <p>
     * <b>Character Encoding:</b> Body is stored as UTF-8 decoded string. Binary
     * responses or non-UTF-8 encodings may not be represented correctly. For
     * binary data, the client would need to provide access to raw bytes.</p>
     */
    public static class HttpResponse {

        /**
         * HTTP status code (100-599). Common values: 200 (OK), 404 (Not Found),
         * 500 (Internal Server Error).
         */
        private final int statusCode;

        /**
         * HTTP status message/reason phrase. Examples: "OK", "Not Found",
         * "Internal Server Error".
         */
        private final String statusMessage;

        /**
         * HTTP response headers as key-value pairs. Keys are case-sensitive per
         * HTTP spec.
         */
        private final Map<String, String> headers;

        /**
         * Response body as UTF-8 decoded string. May be empty string if no
         * body, never {@code null}.
         */
        private final String body;

        /**
         * Constructs a new HTTP response with the given components.
         *
         * @param statusCode HTTP status code (100-599)
         * @param statusMessage HTTP reason phrase (e.g., "OK", "Not Found")
         * @param headers response headers map (typically mutable HashMap). May
         * be empty, not {@code null}.
         * @param body response body as UTF-8 string. May be empty string, not
         * {@code null}.
         */
        public HttpResponse(int statusCode, String statusMessage,
                Map<String, String> headers, String body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = headers;
            this.body = body;
        }

        /**
         * Returns the HTTP status code.
         *
         * @return status code in range 100-599. Common values:
         * <ul>
         * <li>2xx: Success (200 OK, 201 Created, 204 No Content)</li>
         * <li>3xx: Redirection (301 Moved Permanently, 302 Found)</li>
         * <li>4xx: Client Error (400 Bad Request, 401 Unauthorized, 404 Not
         * Found)</li>
         * <li>5xx: Server Error (500 Internal Server Error, 503 Service
         * Unavailable)</li>
         * </ul>
         */
        public int getStatusCode() {
            return statusCode;
        }

        /**
         * Returns the HTTP reason phrase associated with the status code.
         *
         * @return human-readable status message, e.g., "OK", "Not Found",
         * "Internal Server Error". Never {@code null}.
         */
        public String getStatusMessage() {
            return statusMessage;
        }

        /**
         * Returns all HTTP response headers as a map.
         *
         * <p>
         * <b>Note:</b> The returned map is mutable and is the same instance
         * stored internally. Modifying this map will affect future calls to
         * this method. For safety, callers should not modify the returned
         * map.</p>
         *
         * @return map of header name to header value. Keys are case-sensitive
         * per HTTP specification. May be empty if server sent no headers. Never
         * {@code null}.
         */
        public Map<String, String> getHeaders() {
            return headers;
        }

        /**
         * Returns the response body as a UTF-8 decoded string.
         *
         * @return response body content. Empty string if no body was sent.
         * Never {@code null}.
         */
        public String getBody() {
            return body;
        }

        /**
         * Checks if this response represents a successful HTTP request.
         *
         * <p>
         * Success is defined as status code in the 2xx range (200-299), which
         * includes:</p>
         * <ul>
         * <li>200 OK</li>
         * <li>201 Created</li>
         * <li>202 Accepted</li>
         * <li>204 No Content</li>
         * <li>206 Partial Content</li>
         * </ul>
         *
         * <p>
         * All other status codes (1xx, 3xx, 4xx, 5xx) return {@code false}.</p>
         *
         * @return {@code true} if status code is 200-299, {@code false}
         * otherwise
         */
        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        /**
         * Returns a concise string representation of this HTTP response for
         * debugging and logging.
         *
         * <p>
         * Format: {@code "HTTP [code] [message]: [body preview]"}</p>
         * <p>
         * Body is truncated to first 100 characters to avoid excessive log
         * output.</p>
         *
         * @return formatted string like "HTTP 200 OK:
         * {\"status\":\"success\",...}". Never {@code null}.
         */
        @Override
        public String toString() {
            return String.format("HTTP %d %s: %s", statusCode, statusMessage,
                    body != null ? body.substring(0, Math.min(100, body.length())) : "");
        }
    }

    /**
     * Gracefully shuts down this HTTP client executor and releases all
     * associated resources.
     *
     * <p>
     * This method initiates graceful shutdown of the Netty worker group,
     * which:</p>
     * <ol>
     * <li>Stops accepting new requests (calling {@link #executeSync} or
     * {@link #executeAsync} after shutdown results in undefined behavior)</li>
     * <li>Allows in-flight requests up to 5 seconds to complete</li>
     * <li>Forcefully terminates any remaining requests after grace period</li>
     * <li>Closes all channels and releases thread pool resources</li>
     * </ol>
     *
     * <p>
     * <b>Blocking Behavior:</b> This method blocks until shutdown completes or
     * the 5-second grace period expires. The calling thread may be interrupted
     * during this wait.</p>
     *
     * <p>
     * <b>Resource Management:</b> Failure to call this method before JVM exit
     * will:</p>
     * <ul>
     * <li>Prevent JVM from terminating (non-daemon threads remain alive)</li>
     * <li>Leak native resources (file descriptors, memory buffers)</li>
     * </ul>
     *
     * <p>
     * <b>Usage Pattern:</b> Always call shutdown in a finally block or use
     * try-with-resources if implementing {@link AutoCloseable}:</p>
     * <pre>{@code
     * HttpClientExecutor executor = new HttpClientExecutor();
     * try {
     *     // use executor
     * } finally {
     *     executor.shutdown();
     * }
     * }</pre>
     *
     * <p>
     * <b>Thread Interruption:</b> If the calling thread is interrupted during
     * shutdown, the interrupted status is preserved via
     * {@link Thread#interrupt()}, allowing calling code to detect and handle
     * interruption.</p>
     */
    public void shutdown() {
        try {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            LOGGER.info("HTTP client executor shutdown");
        } catch (InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error shutting down HTTP client", e);
            Thread.currentThread().interrupt();
        }
    }
}
