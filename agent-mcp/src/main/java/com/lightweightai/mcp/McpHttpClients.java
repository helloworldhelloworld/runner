package com.lightweightai.mcp;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/**
 * 进程级共享 JDK {@link HttpClient}，供 MCP HTTP / SSE / WS transport 复用。
 *
 * <p>MCP SDK 0.17.0 的 streamable HTTP / SSE transport 只接受 {@link HttpClient.Builder}，
 * 并在 {@code build()} 时调用 {@code clientBuilder.connectTimeout(d).build()}。
 * {@link #sharing(HttpClient)} 返回的 builder 忽略后续配置，始终交出已构建的实例，
 * 避免每条 transport 新建 {@code HttpClient} + {@code SelectorManager} 线程
 * （issue #200 / ADR-015）。
 *
 * <p>共享实例使用 HTTP/1.1，对齐 SDK 默认；JDK 默认 HTTP/2 会打坏部分 MCP 握手。
 * {@link ToolClient#close()} 不关闭本实例。
 */
public final class McpHttpClients {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(30);

    private static final HttpClient SHARED = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(DEFAULT_CONNECT_TIMEOUT)
        .build();

    private McpHttpClients() {
    }

    /**
     * 进程级共享 {@link HttpClient}。生命周期随 JVM，调用方不应 {@code close}/{@code shutdown}。
     */
    public static HttpClient shared() {
        return SHARED;
    }

    /**
     * 给 MCP SDK {@code clientBuilder(...)} 用的 builder：{@code build()} 返回 {@code client}。
     *
     * @throws IllegalArgumentException if {@code client} is null
     */
    public static HttpClient.Builder sharing(HttpClient client) {
        if (client == null) {
            throw new IllegalArgumentException("shared HttpClient must not be null");
        }
        return new SharedHttpClientBuilder(client);
    }

    /**
     * JDK 21 {@link HttpClient.Builder} 全量方法（含 {@code localAddress}）。
     * 配置全部 no-op：共享实例在创建时已定死。
     */
    private static final class SharedHttpClientBuilder implements HttpClient.Builder {

        private final HttpClient shared;

        private SharedHttpClientBuilder(HttpClient shared) {
            this.shared = shared;
        }

        @Override
        public HttpClient.Builder cookieHandler(CookieHandler cookieHandler) {
            return this;
        }

        @Override
        public HttpClient.Builder connectTimeout(Duration duration) {
            return this;
        }

        @Override
        public HttpClient.Builder sslContext(SSLContext sslContext) {
            return this;
        }

        @Override
        public HttpClient.Builder sslParameters(SSLParameters sslParameters) {
            return this;
        }

        @Override
        public HttpClient.Builder executor(Executor executor) {
            return this;
        }

        @Override
        public HttpClient.Builder followRedirects(HttpClient.Redirect policy) {
            return this;
        }

        @Override
        public HttpClient.Builder version(HttpClient.Version version) {
            return this;
        }

        @Override
        public HttpClient.Builder priority(int priority) {
            return this;
        }

        @Override
        public HttpClient.Builder proxy(ProxySelector proxySelector) {
            return this;
        }

        @Override
        public HttpClient.Builder authenticator(Authenticator authenticator) {
            return this;
        }

        @Override
        public HttpClient.Builder localAddress(InetAddress localAddr) {
            return this;
        }

        @Override
        public HttpClient build() {
            return shared;
        }
    }
}
