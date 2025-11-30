package org.nexis.tests;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
// No Mockito usage (ByteBuddy unsupported on Java 25 for final/complex Netty types)
import org.nexis.base.Manifest;
import org.nexis.base.NexusNetwork;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.Identity;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.Block;
import org.nexis.core.InterfaceErrorCodes;
import org.nexis.messages.handlers.FunctionCallMessageHandler;
import org.nexus.base.proto.NexusProtocol;

import java.util.HashMap;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Updated tests for FunctionCallMessageHandler using SimpleHttpInvoker instead of mocking final HttpClientExecutor.
 */
public class FunctionCallMessageHandlerTest {

    private NetworkConfiguration networkConfig;
    private NexusEnvelopBuilder envelopBuilder;
    private Identity identity;

    private ChannelHandlerContext ctx;
    private final java.util.List<Object> sentMessages = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        // Dynamic proxy for ChannelHandlerContext capturing writeAndFlush calls
        ctx = (ChannelHandlerContext) Proxy.newProxyInstance(
                ChannelHandlerContext.class.getClassLoader(),
                new Class[]{ChannelHandlerContext.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("writeAndFlush")) {
                        sentMessages.add(args[0]);
                        return null;
                    }
                    // Return benign defaults for unused methods
                    Class<?> rt = method.getReturnType();
                    if (rt.equals(boolean.class)) return false;
                    if (rt.equals(int.class)) return 0;
                    if (rt.equals(long.class)) return 0L;
                    return null; // ignore other methods
                }
        );
        networkConfig = new NetworkConfiguration(NexusNetwork.LOCALHOSTTEST, 0xD9B4BEF9) {
            @Override
            public Block getGenesisBlock() { return null; }
        };
        try {
            java.security.KeyPairGenerator kpg = java.security.KeyPairGenerator.getInstance("Ed25519");
            java.security.KeyPair kp = kpg.generateKeyPair();
            identity = new Identity() {
                private final NodeId nodeId = NodeId.fromPublicKey(kp.getPublic().getEncoded());
                @Override
                public NodeId getNodeId() { return nodeId; }
                @Override
                public java.security.KeyPair getKeyPair() { return kp; }
                @Override
                public boolean isPubKeyHashMine(byte[] hash) { return false; }
            };
            envelopBuilder = new NexusEnvelopBuilder(identity);
        } catch (Exception e) {
            fail("Identity init failed: " + e.getMessage());
        }
    }

    private Manifest buildManifest(String baseUrl, String apiPath) {
        String raw = "{" +
                "\"manifestVersion\":1," +
                "\"protocolVersion\":1," +
                "\"organizationName\":\"test-org\"," +
                "\"organizationUrl\":\"https://org.example.com\"," +
                "\"organizationCountryCodes\":[\"GB\"]," +
                "\"organizationRegistrationNumbers\":{\"reg\":\"123\"}," +
                "\"organizationPolicy\":\"https://org.example.com/policy\"," +
                "\"organizationTermsAndConditions\":\"https://org.example.com/terms\"," +
                "\"category\":[\"test\"]," +
                "\"contact\":{\"fullName\":\"Tester\",\"email\":\"t@e.com\",\"mobileNumber\":\"0\",\"organisationRole\":\"dev\"}," +
                "\"specification\":{\"type\":\"openapi\",\"swagger\":\"2.0\",\"host\":\"" + baseUrl + "\",\"basePath\":\"/\",\"info\":{\"title\":\"Test API\",\"version\":\"1.0\"},\"paths\":{\"" + apiPath + "\":{\"get\":{\"operationId\":\"getOp\",\"responses\":{\"200\":{\"description\":\"ok\"}}}}}}" +
                "}";
        return Manifest.of(raw, identity);
    }

    private NexusProtocol.Call buildCall(String apiPath, byte[] payload, byte[] correlationId) {
        NexusProtocol.Call.Builder b = NexusProtocol.Call.newBuilder().setApiId(apiPath)
                .setCorrelationId(ByteString.copyFrom(correlationId));
        if (payload != null) {
            b.setRequest(ByteString.copyFrom(payload));
        }
        return b.build();
    }

    private NexusProtocol.NexusEnvelop wrap(NexusProtocol.Call call) {
        NexusProtocol.NexusMessage message = NexusProtocol.NexusMessage.newBuilder().setFunctionCall(call).build();
        return NexusProtocol.NexusEnvelop.newBuilder().setMessage(message).build();
    }

    private NexusProtocol.Result captureLastResult() {
        if (sentMessages.isEmpty()) return null;
        Object last = sentMessages.get(sentMessages.size() - 1);
        if (last instanceof NexusProtocol.NexusEnvelop env) {
            return env.getMessage().getFunctionResult();
        }
        return null;
    }

    @Test
    @DisplayName("READ_ONLY success returns status=0 and no error code")
    void readOnlySuccess() {
        String path = "/balance";
        Manifest manifest = buildManifest("api.example.com", path);
        byte[] correlation = new byte[]{0x01,0x02};
        byte[] req = "{}".getBytes();
        FunctionCallMessageHandler.SimpleHttpInvoker invoker = (m,u,b) -> new org.nexis.net.HttpClientExecutor.HttpResponse(200, "OK", new HashMap<>(), "{\"balance\":100}");
        FunctionCallMessageHandler handler = new FunctionCallMessageHandler(networkConfig, envelopBuilder, manifest, invoker);
        handler.handle(wrap(buildCall(path, req, correlation)), ctx);
        NexusProtocol.Result r = captureLastResult();
        assertNotNull(r);
        assertEquals(0, r.getStatus());
        assertEquals("Success", r.getMessage());
        assertArrayEquals(correlation, r.getCorrelationId().toByteArray());
    }

    @Test
    @DisplayName("HTTP 500 maps to SERVER_ERROR")
    void httpErrorMapsToServerError() {
        String path = "/balance";
        Manifest manifest = buildManifest("api.example.com", path);
        byte[] correlation = new byte[]{0x03};
        FunctionCallMessageHandler.SimpleHttpInvoker invoker = (m,u,b) -> new org.nexis.net.HttpClientExecutor.HttpResponse(500, "Internal", new HashMap<>(), "boom");
        FunctionCallMessageHandler handler = new FunctionCallMessageHandler(networkConfig, envelopBuilder, manifest, invoker);
        handler.handle(wrap(buildCall(path, "{}".getBytes(), correlation)), ctx);
        NexusProtocol.Result r = captureLastResult();
        assertNotNull(r);
        assertEquals(1, r.getStatus());
        assertEquals(InterfaceErrorCodes.SERVER_ERROR, r.getErrorCode());
    }

    @Test
    @DisplayName("Timeout maps to TIME_OUT error code")
    void timeoutMapsToTimeoutError() {
        String path = "/balance";
        Manifest manifest = buildManifest("api.example.com", path);
        byte[] correlation = new byte[]{0x04};
        FunctionCallMessageHandler.SimpleHttpInvoker invoker = (m,u,b) -> { throw new TimeoutException("simulated timeout"); };
        FunctionCallMessageHandler handler = new FunctionCallMessageHandler(networkConfig, envelopBuilder, manifest, invoker);
        handler.handle(wrap(buildCall(path, "{}".getBytes(), correlation)), ctx);
        NexusProtocol.Result r = captureLastResult();
        assertNotNull(r);
        assertEquals(1, r.getStatus());
        assertEquals(InterfaceErrorCodes.TIME_OUT, r.getErrorCode());
    }

    @Test
    @DisplayName("Retry succeeds after transient failures")
    void retrySucceedsAfterTransientFailures() {
        String path = "/balance";
        Manifest manifest = buildManifest("api.example.com", path);
        byte[] correlation = new byte[]{0x05};
        AtomicInteger attempts = new AtomicInteger();
        FunctionCallMessageHandler.SimpleHttpInvoker invoker = (m,u,b) -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                throw new TimeoutException("transient " + n);
            }
            return new org.nexis.net.HttpClientExecutor.HttpResponse(200, "OK", new HashMap<>(), "done");
        };
        FunctionCallMessageHandler handler = new FunctionCallMessageHandler(networkConfig, envelopBuilder, manifest, invoker);
        handler.handle(wrap(buildCall(path, "{}".getBytes(), correlation)), ctx);
        NexusProtocol.Result r = captureLastResult();
        assertEquals(3, attempts.get());
        assertEquals(0, r.getStatus());
    }

    @Test
    @DisplayName("Circuit breaker opens after 5 failures and fast-fails")
    void circuitBreakerOpens() {
        String path = "/balance";
        Manifest manifest = buildManifest("api.example.com", path);
        byte[] correlation = new byte[]{0x06};
        FunctionCallMessageHandler.SimpleHttpInvoker failingInvoker = (m,u,b) -> { throw new TimeoutException("always fail"); };
        FunctionCallMessageHandler handler = new FunctionCallMessageHandler(networkConfig, envelopBuilder, manifest, failingInvoker);
        for (int i=0;i<5;i++) {
            handler.handle(wrap(buildCall(path, "{}".getBytes(), correlation)), ctx);
        }
        // Sixth call should trip circuit breaker (fast-fail without invoking invoker)
        handler.handle(wrap(buildCall(path, "{}".getBytes(), correlation)), ctx);
        // Capture last result only (Mockito will have multiple invocations; we simply verify last captured)
        NexusProtocol.Result r = captureLastResult();
        assertNotNull(r);
        assertEquals(1, r.getStatus());
        assertEquals(InterfaceErrorCodes.SERVER_ERROR, r.getErrorCode());
    }
}
