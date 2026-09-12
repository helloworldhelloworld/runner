package com.lightweightai.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link McpHttpClients} 的契约：sharing builder 必须让 SDK 的
 * {@code clientBuilder.connectTimeout(d).build()} 拿到同一共享实例。
 */
@DisplayName("McpHttpClients — 共享 HttpClient 与 sharing builder")
class McpHttpClientsTest {

    @Test
    @DisplayName("shared() 多次调用返回同一实例")
    void sharedReturnsSameInstance() {
        assertSame(McpHttpClients.shared(), McpHttpClients.shared());
        assertNotNull(McpHttpClients.shared());
    }

    @Test
    @DisplayName("sharing(client).connectTimeout(d).build() 返回传入实例（模拟 SDK build）")
    void sharingBuilderIgnoresConnectTimeoutAndReturnsShared() {
        HttpClient client = McpHttpClients.shared();
        HttpClient built = McpHttpClients.sharing(client)
            .connectTimeout(Duration.ofSeconds(3))
            .version(HttpClient.Version.HTTP_2)
            .build();
        assertSame(client, built);
    }

    @Test
    @DisplayName("sharing(null) 拒绝")
    void sharingRejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> McpHttpClients.sharing(null));
    }
}
