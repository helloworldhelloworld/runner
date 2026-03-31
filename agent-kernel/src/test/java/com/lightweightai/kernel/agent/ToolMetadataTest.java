package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolMetadata - 工具元数据默认值")
class ToolMetadataTest {

    /**
     * 匿名实现只使用默认方法，验证所有默认值符合预期
     */
    private final ToolMetadata defaultMetadata = new ToolMetadata() {};

    @Test
    @DisplayName("默认 category 为 default")
    void shouldHaveDefaultCategory() {
        assertEquals("default", defaultMetadata.getCategory());
    }

    @Test
    @DisplayName("默认 tags 为空列表")
    void shouldHaveEmptyTags() {
        assertTrue(defaultMetadata.getTags().isEmpty());
    }

    @Test
    @DisplayName("默认 version 为 1.0.0")
    void shouldHaveDefaultVersion() {
        assertEquals("1.0.0", defaultMetadata.getVersion());
    }

    @Test
    @DisplayName("默认 author 为空字符串")
    void shouldHaveEmptyAuthor() {
        assertEquals("", defaultMetadata.getAuthor());
    }

    @Test
    @DisplayName("默认 extraMetadata 为空 map")
    void shouldHaveEmptyExtraMetadata() {
        assertTrue(defaultMetadata.getExtraMetadata().isEmpty());
    }

    @Test
    @DisplayName("默认 isReadOnly 为 false")
    void shouldNotBeReadOnlyByDefault() {
        assertFalse(defaultMetadata.isReadOnly());
    }

    @Test
    @DisplayName("默认 isDestructive 为 false")
    void shouldNotBeDestructiveByDefault() {
        assertFalse(defaultMetadata.isDestructive());
    }

    @Test
    @DisplayName("默认 isIdempotent 为 false")
    void shouldNotBeIdempotentByDefault() {
        assertFalse(defaultMetadata.isIdempotent());
    }

    @Test
    @DisplayName("默认 isOpenWorld 为 true")
    void shouldBeOpenWorldByDefault() {
        assertTrue(defaultMetadata.isOpenWorld());
    }

    @Test
    @DisplayName("默认 isClientSide 为 false")
    void shouldNotBeClientSideByDefault() {
        assertFalse(defaultMetadata.isClientSide());
    }

    @Test
    @DisplayName("可以覆盖所有默认值")
    void shouldAllowOverridingDefaults() {
        ToolMetadata custom = new ToolMetadata() {
            @Override public String getCategory() { return "math"; }
            @Override public boolean isReadOnly() { return true; }
            @Override public boolean isIdempotent() { return true; }
            @Override public boolean isOpenWorld() { return false; }
            @Override public String getVersion() { return "2.0.0"; }
        };

        assertEquals("math", custom.getCategory());
        assertTrue(custom.isReadOnly());
        assertTrue(custom.isIdempotent());
        assertFalse(custom.isOpenWorld());
        assertEquals("2.0.0", custom.getVersion());
        // Non-overridden defaults still work
        assertFalse(custom.isDestructive());
        assertFalse(custom.isClientSide());
    }
}
