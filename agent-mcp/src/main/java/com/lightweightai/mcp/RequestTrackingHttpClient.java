package com.lightweightai.mcp;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

/** Per-transport request scope; all network resources still belong to the shared client. */
final class RequestTrackingHttpClient extends HttpClient {
    private final HttpClient delegate;
    private final Set<CompletableFuture<?>> requests = new HashSet<>();
    private boolean closing;
    private boolean closed;

    RequestTrackingHttpClient(HttpClient delegate) {
        this.delegate = Objects.requireNonNull(delegate, "sharedClient");
    }

    synchronized boolean isClosing() {
        return closing;
    }

    void beginClose() {
        Set<CompletableFuture<?>> pending;
        synchronized (this) {
            closing = true;
            pending = new HashSet<>(requests);
        }
        // Cancel outside the monitor: completion may invoke SDK/user callbacks.
        pending.forEach(future -> future.cancel(true));
    }

    void finishClose() {
        Set<CompletableFuture<?>> pending;
        synchronized (this) {
            closing = true;
            closed = true;
            pending = new HashSet<>(requests);
        }
        pending.forEach(future -> future.cancel(true));
    }

    @Override
    public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                                                           HttpResponse.BodyHandler<T> handler) {
        return sendAsync(request, handler, null);
    }

    @Override
    public synchronized <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
            HttpResponse.BodyHandler<T> handler, HttpResponse.PushPromiseHandler<T> pushHandler) {
        if (closed || (closing && !"DELETE".equals(request.method()))) {
            return CompletableFuture.failedFuture(new IllegalStateException("MCP HTTP transport is closed"));
        }
        // Registration and the closing check are atomic, including the interval
        // before the HTTP implementation returns its future.
        CompletableFuture<HttpResponse<T>> future = pushHandler == null
            ? delegate.sendAsync(request, handler)
            : delegate.sendAsync(request, handler, pushHandler);
        requests.add(future);
        future.whenComplete((response, error) -> {
            synchronized (RequestTrackingHttpClient.this) {
                requests.remove(future);
            }
        });
        return future;
    }

    @Override
    public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler)
            throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<T>> future = sendAsync(request, handler);
        try {
            return future.get();
        } catch (InterruptedException e) {
            future.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof IOException io) {
                throw io;
            }
            if (e.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IOException(e.getCause());
        }
    }

    HttpClient sharedClient() { return delegate; }
    @Override public Optional<CookieHandler> cookieHandler() { return delegate.cookieHandler(); }
    @Override public Optional<Duration> connectTimeout() { return delegate.connectTimeout(); }
    @Override public Redirect followRedirects() { return delegate.followRedirects(); }
    @Override public Optional<ProxySelector> proxy() { return delegate.proxy(); }
    @Override public SSLContext sslContext() { return delegate.sslContext(); }
    @Override public SSLParameters sslParameters() { return delegate.sslParameters(); }
    @Override public Optional<Authenticator> authenticator() { return delegate.authenticator(); }
    @Override public Version version() { return delegate.version(); }
    @Override public Optional<Executor> executor() { return delegate.executor(); }
}
