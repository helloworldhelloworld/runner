package com.lightweightai.kernel.llm;

import com.lightweightai.kernel.agent.ToolMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolMetadata - 工具元数据接口默认值")
class ToolMetadataTest {

    private final ToolMetadata metadata = new ToolMetadata() {};

    @Test
    @DisplayName("默认分类为 default")
    void defaultCategory() {
        assertEquals("default", metadata.getCategory());
    }

    @Test
    @DisplayName("默认标签为空列表")
    void defaultTagsEmpty() {
        List<String> tags = metadata.getTags();
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    @DisplayName("默认版本为 1.0.0")
    void defaultVersion() {
        assertEquals("1.0.0", metadata.getVersion());
    }

    @Test
    @DisplayName("默认作者为空字符串")
    void defaultAuthorEmpty() {
        assertEquals("", metadata.getAuthor());
    }

    @Test
    @DisplayName("默认额外元数据为空 Map")
    void defaultExtraMetadataEmpty() {
        Map<String, Object> extra = metadata.getExtraMetadata();
        assertNotNull(extra);
        assertTrue(extra.isEmpty());
    }

    @Test
    @DisplayName("MCP 行为注解默认值")
    void mcpAnnotationDefaults() {
        assertFalse(metadata.isReadOnly());
        assertFalse(metadata.isDestructive());
        assertFalse(metadata.isIdempotent());
        assertTrue(metadata.isOpenWorld());
        assertFalse(metadata.isClientSide());
    }

    @Test
    @DisplayName("自定义实现可覆盖所有默认值")
    void customImplementationOverrides() {
        ToolMetadata custom = new ToolMetadata() {
            @Override public String getCategory() { return "weather"; }
            @Override public List<String> getTags() { return List.of("api", "external"); }
            @Override public String getVersion() { return "2.0.0"; }
            @Override public boolean isReadOnly() { return true; }
            @Override public boolean isClientSide() { return true; }
        };

        assertEquals("weather", custom.getCategory());
        assertEquals(List.of("api", "external"), custom.getTags());
        assertEquals("2.0.0", custom.getVersion());
        assertTrue(custom.isReadOnly());
        assertTrue(custom.isClientSide());
    }
}
