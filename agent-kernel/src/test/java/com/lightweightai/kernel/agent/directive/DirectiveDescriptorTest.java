package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveDescriptor - Directive 元信息")
class DirectiveDescriptorTest {

    @BeforeEach
    void setUp() {
        ClientToolAliasRegistry.reset();
    }

    @AfterEach
    void tearDown() {
        ClientToolAliasRegistry.reset();
    }

    // ==================== 构造器验证 ====================

    @Test
    @DisplayName("合法参数构造成功，字段值正确")
    void validConstruction() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "Camera.TakePhoto", "take_photo", "photo_result", "Camera", 30000, "1.0");

        assertEquals("Camera.TakePhoto", desc.getTool());
        assertEquals("take_photo", desc.getDownAction());
        assertEquals("photo_result", desc.getUpAction());
        assertEquals("Camera", desc.getNamespace());
        assertEquals(30000, desc.getTimeoutMs());
        assertEquals("1.0", desc.getVersion());
    }

    @Test
    @DisplayName("tool 为空抛出异常")
    void nullToolThrows() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveDescriptor(null, "down", "up", "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("tool 为空白字符串抛出异常")
    void blankToolThrows() {
        assertThrows(IllegalArgumentException.class, () ->
            new DirectiveDescriptor("  ", "down", "up", "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("downAction 为空抛出异常")
    void nullDownActionThrows() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveDescriptor("tool", null, "up", "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("upAction 为空抛出异常")
    void nullUpActionThrows() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveDescriptor("tool", "down", null, "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("namespace 为空抛出异常")
    void nullNamespaceThrows() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveDescriptor("tool", "down", "up", null, 5000, "1.0"));
    }

    @Test
    @DisplayName("version 为空抛出异常")
    void nullVersionThrows() {
        assertThrows(NullPointerException.class, () ->
            new DirectiveDescriptor("tool", "down", "up", "ns", 5000, null));
    }

    // ==================== timeoutMs 默认值 ====================

    @Test
    @DisplayName("timeoutMs <= 0 时默认为 60000")
    void timeoutDefaultsTo60s() {
        DirectiveDescriptor desc = new DirectiveDescriptor("tool", "down", "up", "ns", 0, "1.0");
        assertEquals(60_000, desc.getTimeoutMs());

        DirectiveDescriptor negTimeout = new DirectiveDescriptor("tool", "down", "up", "ns", -1, "1.0");
        assertEquals(60_000, negTimeout.getTimeoutMs());
    }

    // ==================== 别名处理 ====================

    @Test
    @DisplayName("无别名时 aliases 为空列表")
    void noAliases() {
        DirectiveDescriptor desc = new DirectiveDescriptor("tool", "down", "up", "ns", 5000, "1.0");
        assertTrue(desc.getAliases().isEmpty());
    }

    @Test
    @DisplayName("带别名构造")
    void withAliases() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "tool", List.of("alias1", "alias2"), "down", "up", "ns", 5000, "1.0");
        assertEquals(2, desc.getAliases().size());
        assertTrue(desc.getAliases().contains("alias1"));
        assertTrue(desc.getAliases().contains("alias2"));
    }

    @Test
    @DisplayName("别名中的 null 和空白被过滤")
    void aliasesFilterNullAndBlank() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "tool", Arrays.asList("valid", null, "  ", "another"), "down", "up", "ns", 5000, "1.0");
        assertEquals(2, desc.getAliases().size());
        assertTrue(desc.getAliases().contains("valid"));
        assertTrue(desc.getAliases().contains("another"));
    }

    @Test
    @DisplayName("别名中和主名相同的被过滤")
    void aliasesFilterPrimary() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "tool", List.of("tool", "alias1"), "down", "up", "ns", 5000, "1.0");
        assertEquals(1, desc.getAliases().size());
        assertEquals("alias1", desc.getAliases().get(0));
    }

    @Test
    @DisplayName("别名去重")
    void aliasesDeduped() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "tool", List.of("a", "b", "a", "b"), "down", "up", "ns", 5000, "1.0");
        assertEquals(2, desc.getAliases().size());
    }

    @Test
    @DisplayName("别名列表不可修改")
    void aliasesAreUnmodifiable() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "tool", List.of("alias1"), "down", "up", "ns", 5000, "1.0");
        assertThrows(UnsupportedOperationException.class, () ->
            desc.getAliases().add("new_alias"));
    }

    // ==================== getAllToolNames ====================

    @Test
    @DisplayName("getAllToolNames 无别名时只含主名")
    void getAllToolNamesNoAliases() {
        DirectiveDescriptor desc = new DirectiveDescriptor("tool", "down", "up", "ns", 5000, "1.0");
        List<String> names = desc.getAllToolNames();
        assertEquals(1, names.size());
        assertEquals("tool", names.get(0));
    }

    @Test
    @DisplayName("getAllToolNames 包含主名和别名，主名在前")
    void getAllToolNamesWithAliases() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "tool", List.of("alias1", "alias2"), "down", "up", "ns", 5000, "1.0");
        List<String> names = desc.getAllToolNames();
        assertEquals(3, names.size());
        assertEquals("tool", names.get(0));
        assertTrue(names.contains("alias1"));
        assertTrue(names.contains("alias2"));
    }

    // ==================== toToolName / toDownlinkToolName ====================

    @Test
    @DisplayName("toToolName 返回主工具名")
    void toToolName() {
        DirectiveDescriptor desc = new DirectiveDescriptor("Camera.TakePhoto", "down", "up", "ns", 5000, "1.0");
        assertEquals("Camera.TakePhoto", desc.toToolName());
    }

    @Test
    @DisplayName("toDownlinkToolName 返回 downAction")
    void toDownlinkToolName() {
        DirectiveDescriptor desc = new DirectiveDescriptor("tool", "my_down_action", "up", "ns", 5000, "1.0");
        assertEquals("my_down_action", desc.toDownlinkToolName());
    }

    // ==================== 字段 trim ====================

    @Test
    @DisplayName("字段值会被 trim")
    void fieldsTrimmed() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
            "  tool  ", "  down  ", "  up  ", "  ns  ", 5000, "  1.0  ");
        assertEquals("tool", desc.getTool());
        assertEquals("down", desc.getDownAction());
        assertEquals("up", desc.getUpAction());
        assertEquals("ns", desc.getNamespace());
        assertEquals("1.0", desc.getVersion());
    }
}
