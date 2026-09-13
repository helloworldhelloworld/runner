package com.lightweightai.mcp.transport;

import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.InetAddress;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

/** Package-private adapter for the MCP SDK's clientBuilder hook. */
final class SharedHttpClientBuilder implements HttpClient.Builder {

    private final HttpClient client;

    SharedHttpClientBuilder(HttpClient client) {
        this.client = Objects.requireNonNull(client, "client");
    }

    @Override public HttpClient.Builder cookieHandler(CookieHandler value) { return this; }
    @Override public HttpClient.Builder connectTimeout(Duration value) { return this; }
    @Override public HttpClient.Builder sslContext(SSLContext value) { return this; }
    @Override public HttpClient.Builder sslParameters(SSLParameters value) { return this; }
    @Override public HttpClient.Builder executor(Executor value) { return this; }
    @Override public HttpClient.Builder followRedirects(HttpClient.Redirect value) { return this; }
    @Override public HttpClient.Builder version(HttpClient.Version value) { return this; }
    @Override public HttpClient.Builder priority(int value) { return this; }
    @Override public HttpClient.Builder proxy(ProxySelector value) { return this; }
    @Override public HttpClient.Builder authenticator(Authenticator value) { return this; }
    @Override public HttpClient.Builder localAddress(InetAddress value) { return this; }
    @Override public HttpClient build() { return client; }
}
