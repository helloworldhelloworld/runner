package com.lightweightai.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.util.context.Context;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("McpRequestMetaContext Reactor Context 往返")
class McpRequestMetaContextTest {

    @Test
    @DisplayName("of() 写入 context，current() 读回相同内容")
    void roundTrip() {
        Map<String, String> meta = Map.of("x-trace-id", "t-1", "x-device-id", "d-1");
        Context ctx = McpRequestMetaContext.of(meta).apply(Context.empty());

        assertEquals(meta, McpRequestMetaContext.current(ctx));
    }

    @Test
    @DisplayName("未写入时 current() 返回空 map（不为 null）")
    void emptyWhenAbsent() {
        assertTrue(McpRequestMetaContext.current(Context.empty()).isEmpty());
    }
}
