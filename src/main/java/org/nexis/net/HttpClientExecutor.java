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
 * Direct HTTP client using Netty for executing API calls. No Feign dependency,
 * just raw Netty HTTP client.
 *
 * @author daviestobialex
 */
public final class HttpClientExecutor {

    private static final Logger LOGGER = Logger.getLogger(HttpClientExecutor.class.getName());

    private final EventLoopGroup workerGroup;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public HttpClientExecutor() {
        this(30000, 60000); // 30s connect, 60s read
    }

    public HttpClientExecutor(int connectTimeoutMs, int readTimeoutMs) {
        this.workerGroup = new NioEventLoopGroup(4);
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    /**
     * Execute HTTP request synchronously
     *
     * @param method HTTP method
     * @param url Full URL
     * @param headers HTTP headers
     * @param body Request body (optional)
     * @return HTTP response
     * @throws java.lang.Exception
     */
    public HttpResponse executeSync(String method, String url,
            Map<String, String> headers, String body)
            throws Exception {

        CompletableFuture<HttpResponse> future = executeAsync(method, url, headers, body);
        return future.get(readTimeoutMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Execute HTTP request asynchronously
     *
     * @param method HTTP method
     * @param url Full URL
     * @param headers HTTP headers
     * @param body Request body (optional)
     * @return CompletableFuture with response
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
     * Build HTTP request
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
     * Connect to server and execute request
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
                    protected void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        // SSL handler
                        if (ssl && sslContext != null) {
                            pipeline.addLast(sslContext.newHandler(ch.alloc(), host, port));
                        }

                        // HTTP codec
                        pipeline.addLast(new HttpClientCodec());
                        pipeline.addLast(new HttpContentDecompressor());
                        pipeline.addLast(new HttpObjectAggregator(1048576)); // 1MB max response

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
            } else {
                responseFuture.completeExceptionally(future.cause());
            }
        });
    }

    /**
     * HTTP Response handler
     */
    private static class HttpResponseHandler extends SimpleChannelInboundHandler<FullHttpResponse> {

        private final CompletableFuture<HttpResponse> responseFuture;

        HttpResponseHandler(CompletableFuture<HttpResponse> responseFuture) {
            this.responseFuture = responseFuture;
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, FullHttpResponse msg) throws Exception {
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

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
             LOGGER.log(Level.INFO,"HTTP client error {0}", cause);
            responseFuture.completeExceptionally(cause);
            ctx.close();
        }
    }

    /**
     * HTTP Response object
     */
    public static class HttpResponse {

        private final int statusCode;
        private final String statusMessage;
        private final Map<String, String> headers;
        private final String body;

        public HttpResponse(int statusCode, String statusMessage,
                Map<String, String> headers, String body) {
            this.statusCode = statusCode;
            this.statusMessage = statusMessage;
            this.headers = headers;
            this.body = body;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getStatusMessage() {
            return statusMessage;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public String getBody() {
            return body;
        }

        public boolean isSuccess() {
            return statusCode >= 200 && statusCode < 300;
        }

        @Override
        public String toString() {
            return String.format("HTTP %d %s: %s", statusCode, statusMessage,
                    body != null ? body.substring(0, Math.min(100, body.length())) : "");
        }
    }

    /**
     * Shutdown executor and release resources
     */
    public void shutdown() {
        try {
            workerGroup.shutdownGracefully(0, 5, TimeUnit.SECONDS).sync();
            LOGGER.info("HTTP client executor shutdown");
        } catch (InterruptedException e) {
             LOGGER.log(Level.SEVERE,"Error shutting down HTTP client", e);
            Thread.currentThread().interrupt();
        }
    }
}
