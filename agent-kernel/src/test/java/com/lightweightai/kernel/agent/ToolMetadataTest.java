package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolMetadata — default values and override behavior")
class ToolMetadataTest {

    @Test
    @DisplayName("default implementations return expected values")
    void defaultValues() {
        ToolMetadata metadata = new ToolMetadata() {};

        assertEquals("default", metadata.getCategory());
        assertTrue(metadata.getTags().isEmpty());
        assertEquals("1.0.0", metadata.getVersion());
        assertEquals("", metadata.getAuthor());
        assertTrue(metadata.getExtraMetadata().isEmpty());
        assertFalse(metadata.isReadOnly());
        assertFalse(metadata.isDestructive());
        assertFalse(metadata.isIdempotent());
        assertTrue(metadata.isOpenWorld());
        assertFalse(metadata.isClientSide());
    }

    @Test
    @DisplayName("overridden values take precedence")
    void overriddenValues() {
        ToolMetadata metadata = new ToolMetadata() {
            @Override public String getCategory() { return "weather"; }
            @Override public List<String> getTags() { return List.of("api", "external"); }
            @Override public String getVersion() { return "2.1.0"; }
            @Override public String getAuthor() { return "test-author"; }
            @Override public Map<String, Object> getExtraMetadata() { return Map.of("key", "val"); }
            @Override public boolean isReadOnly() { return true; }
            @Override public boolean isDestructive() { return true; }
            @Override public boolean isIdempotent() { return true; }
            @Override public boolean isOpenWorld() { return false; }
            @Override public boolean isClientSide() { return true; }
        };

        assertEquals("weather", metadata.getCategory());
        assertEquals(List.of("api", "external"), metadata.getTags());
        assertEquals("2.1.0", metadata.getVersion());
        assertEquals("test-author", metadata.getAuthor());
        assertEquals(Map.of("key", "val"), metadata.getExtraMetadata());
        assertTrue(metadata.isReadOnly());
        assertTrue(metadata.isDestructive());
        assertTrue(metadata.isIdempotent());
        assertFalse(metadata.isOpenWorld());
        assertTrue(metadata.isClientSide());
    }

    @Test
    @DisplayName("default tags list is unmodifiable or empty")
    void defaultTagsIsEmpty() {
        ToolMetadata metadata = new ToolMetadata() {};
        List<String> tags = metadata.getTags();
        assertEquals(0, tags.size());
    }

    @Test
    @DisplayName("default extra metadata is unmodifiable or empty")
    void defaultExtraMetadataIsEmpty() {
        ToolMetadata metadata = new ToolMetadata() {};
        Map<String, Object> extra = metadata.getExtraMetadata();
        assertEquals(0, extra.size());
    }
}
