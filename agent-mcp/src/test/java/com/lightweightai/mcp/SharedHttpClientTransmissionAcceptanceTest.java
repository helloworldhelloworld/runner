package com.lightweightai.mcp;

import io.modelcontextprotocol.spec.McpClientTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.http.HttpClient;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * 生产路径 {@link ToolClient.Builder#createTransport} 必须把共享 HttpClient 传到
 * SDK / WS transport 实际持有的字段上（payload，不是“方法被调用”）。
 *
 * <p>这是 issue #200 / ADR-015 的跨层传输断言：3 参数工厂默认共享；4 参数把调用方实例烤进去。
 */
@DisplayName("Shared HttpClient 经 createTransport 传到下游 transport")
class SharedHttpClientTransmissionAcceptanceTest {

    @Test
    @DisplayName("两个 HTTP transport 经 3 参数 createTransport 持有同一共享 HttpClient")
    void twoHttpTransportsShareProcessClient() {
        McpConfiguration.ServerConfig a =
            McpConfiguration.ServerConfig.streamableHttp("http://host-a:8080/mcp");
        McpConfiguration.ServerConfig b =
            McpConfiguration.ServerConfig.streamableHttp("http://host-b:8080/mcp");

        McpClientTransport t1 = ToolClient.Builder.createTransport("a", a, null);
        McpClientTransport t2 = ToolClient.Builder.createTransport("b", b, null);

        HttpClient c1 = extractHttpClient(t1);
        HttpClient c2 = extractHttpClient(t2);
        assertSame(McpHttpClients.shared(), c1);
        assertSame(c1, c2);
    }

    @Test
    @DisplayName("两个 SSE transport 经 3 参数 createTransport 持有同一共享 HttpClient")
    void twoSseTransportsShareProcessClient() {
        McpConfiguration.ServerConfig a = new McpConfiguration.ServerConfig();
        a.setTransport("sse");
        a.setUrl("http://host-a:8080");
        McpConfiguration.ServerConfig b = new McpConfiguration.ServerConfig();
        b.setTransport("sse");
        b.setUrl("http://host-b:8080");

        HttpClient c1 = extractHttpClient(ToolClient.Builder.createTransport("a", a, null));
        HttpClient c2 = extractHttpClient(ToolClient.Builder.createTransport("b", b, null));
        assertSame(McpHttpClients.shared(), c1);
        assertSame(c1, c2);
    }

    @Test
    @DisplayName("4 参数 createTransport 把调用方 HttpClient 烤进 HTTP transport")
    void fourArgCreateTransportInjectsCallerClient() {
        HttpClient dedicated = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        McpConfiguration.ServerConfig http =
            McpConfiguration.ServerConfig.streamableHttp("http://host:8080/mcp");

        McpClientTransport transport =
            ToolClient.Builder.createTransport("h", http, null, dedicated);

        assertSame(dedicated, extractHttpClient(transport));
    }

    @Test
    @DisplayName("WS transport 未注入时默认持有 McpHttpClients.shared()")
    void wsTransportDefaultsToSharedClient() {
        McpConfiguration.ServerConfig ws =
            McpConfiguration.ServerConfig.ws("ws://host:6646/mcp");
        McpClientTransport transport = ToolClient.Builder.createTransport("s", ws, null);

        assertSame(McpHttpClients.shared(), extractHttpClient(transport));
    }

    @Test
    @DisplayName("WS 4 参数 createTransport 把调用方 HttpClient 烤进 transport")
    void wsFourArgInjectsCallerClient() {
        HttpClient dedicated = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .build();
        McpConfiguration.ServerConfig ws =
            McpConfiguration.ServerConfig.ws("ws://host:6646/mcp");

        assertSame(dedicated, extractHttpClient(
            ToolClient.Builder.createTransport("s", ws, null, dedicated)));
    }

    /**
     * SDK 的 {@code HttpClientStreamableHttpTransport} / {@code HttpClientSseClientTransport}
     * 把 client 存在私有字段 {@code httpClient} 上——这就是下游真正消费的实例。
     */
    static HttpClient extractHttpClient(McpClientTransport transport) {
        Class<?> type = transport.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("httpClient");
                field.setAccessible(true);
                return (HttpClient) field.get(transport);
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot read httpClient from " + transport.getClass(), e);
            }
        }
        throw new IllegalStateException(
            "No httpClient field on " + transport.getClass().getName());
    }
}
