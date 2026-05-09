package com.lightweightai.kernel.agent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ToolMetadata - default method behavior")
class ToolMetadataDefaultsTest {

    private final ToolMetadata defaults = new ToolMetadata() {};

    @Test
    @DisplayName("default category is 'default'")
    void defaultCategory() {
        assertEquals("default", defaults.getCategory());
    }

    @Test
    @DisplayName("default tags is empty list")
    void defaultTags() {
        assertNotNull(defaults.getTags());
        assertTrue(defaults.getTags().isEmpty());
    }

    @Test
    @DisplayName("default version is '1.0.0'")
    void defaultVersion() {
        assertEquals("1.0.0", defaults.getVersion());
    }

    @Test
    @DisplayName("default author is empty string")
    void defaultAuthor() {
        assertEquals("", defaults.getAuthor());
    }

    @Test
    @DisplayName("default extra metadata is empty map")
    void defaultExtraMetadata() {
        assertNotNull(defaults.getExtraMetadata());
        assertTrue(defaults.getExtraMetadata().isEmpty());
    }

    @Test
    @DisplayName("default isReadOnly is false")
    void defaultReadOnly() {
        assertFalse(defaults.isReadOnly());
    }

    @Test
    @DisplayName("default isDestructive is false")
    void defaultDestructive() {
        assertFalse(defaults.isDestructive());
    }

    @Test
    @DisplayName("default isIdempotent is false")
    void defaultIdempotent() {
        assertFalse(defaults.isIdempotent());
    }

    @Test
    @DisplayName("default isOpenWorld is true")
    void defaultOpenWorld() {
        assertTrue(defaults.isOpenWorld());
    }

    @Test
    @DisplayName("default isClientSide is false")
    void defaultClientSide() {
        assertFalse(defaults.isClientSide());
    }
}
