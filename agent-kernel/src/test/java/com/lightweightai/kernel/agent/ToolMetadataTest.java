package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolMetadata - 接口默认值验证")
class ToolMetadataTest {

    @Test
    @DisplayName("默认 category 为 'default'")
    void defaultCategory() {
        ToolMetadata meta = new ToolMetadata() {};
        assertEquals("default", meta.getCategory());
    }

    @Test
    @DisplayName("默认 tags 为空列表")
    void defaultTagsEmpty() {
        ToolMetadata meta = new ToolMetadata() {};
        List<String> tags = meta.getTags();
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    @DisplayName("默认 version 为 '1.0.0'")
    void defaultVersion() {
        ToolMetadata meta = new ToolMetadata() {};
        assertEquals("1.0.0", meta.getVersion());
    }

    @Test
    @DisplayName("默认 author 为空字符串")
    void defaultAuthor() {
        ToolMetadata meta = new ToolMetadata() {};
        assertEquals("", meta.getAuthor());
    }

    @Test
    @DisplayName("默认 extraMetadata 为空 Map")
    void defaultExtraMetadata() {
        ToolMetadata meta = new ToolMetadata() {};
        Map<String, Object> extra = meta.getExtraMetadata();
        assertNotNull(extra);
        assertTrue(extra.isEmpty());
    }

    @Test
    @DisplayName("MCP 行为注解默认值：非只读、非破坏、非幂等、开放世界、非客户端")
    void mcpAnnotationDefaults() {
        ToolMetadata meta = new ToolMetadata() {};
        assertFalse(meta.isReadOnly());
        assertFalse(meta.isDestructive());
        assertFalse(meta.isIdempotent());
        assertTrue(meta.isOpenWorld());
        assertFalse(meta.isClientSide());
    }

    @Test
    @DisplayName("自定义实现覆盖默认值")
    void customImplementationOverridesDefaults() {
        ToolMetadata custom = new ToolMetadata() {
            @Override public String getCategory() { return "weather"; }
            @Override public List<String> getTags() { return List.of("api", "external"); }
            @Override public boolean isReadOnly() { return true; }
            @Override public boolean isOpenWorld() { return false; }
            @Override public boolean isClientSide() { return true; }
        };

        assertEquals("weather", custom.getCategory());
        assertEquals(List.of("api", "external"), custom.getTags());
        assertTrue(custom.isReadOnly());
        assertFalse(custom.isOpenWorld());
        assertTrue(custom.isClientSide());
    }
}
