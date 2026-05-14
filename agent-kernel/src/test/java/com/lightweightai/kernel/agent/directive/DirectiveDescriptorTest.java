package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DirectiveDescriptor 单元测试
 */
class DirectiveDescriptorTest {

    @BeforeEach
    void setUp() {
        ClientToolAliasRegistry.reset();
    }

    @Test
    @DisplayName("构造函数正确设置所有字段")
    void constructorSetsAllFields() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "myTool", "down", "up", "ns", 5000, "1.0");

        assertEquals("myTool", desc.getTool());
        assertEquals("down", desc.getDownAction());
        assertEquals("up", desc.getUpAction());
        assertEquals("ns", desc.getNamespace());
        assertEquals(5000, desc.getTimeoutMs());
        assertEquals("1.0", desc.getVersion());
        assertTrue(desc.getAliases().isEmpty());
    }

    @Test
    @DisplayName("tool 为空字符串时抛出 IllegalArgumentException")
    void blankToolNameThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("   ", "down", "up", "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("tool 为 null 时抛出 NullPointerException")
    void nullToolNameThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor(null, "down", "up", "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("downAction 为空字符串时抛出 IllegalArgumentException")
    void blankDownActionThrowsIAE() {
        assertThrows(IllegalArgumentException.class, () ->
                new DirectiveDescriptor("tool", "", "up", "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("upAction 为 null 时抛出 NullPointerException")
    void nullUpActionThrowsNPE() {
        assertThrows(NullPointerException.class, () ->
                new DirectiveDescriptor("tool", "down", null, "ns", 5000, "1.0"));
    }

    @Test
    @DisplayName("timeoutMs <= 0 默认为 60000")
    void timeoutMsDefaultsTo60000WhenNonPositive() {
        DirectiveDescriptor descZero = new DirectiveDescriptor(
                "tool", "down", "up", "ns", 0, "1.0");
        assertEquals(60_000, descZero.getTimeoutMs());

        DirectiveDescriptor descNeg = new DirectiveDescriptor(
                "tool", "down", "up", "ns", -100, "1.0");
        assertEquals(60_000, descNeg.getTimeoutMs());
    }

    @Test
    @DisplayName("normalizeAliases 去重 - 重复别名只保留一个")
    void normalizeAliasesDeduplicates() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", Arrays.asList("a", "b", "a", "b", "c"),
                "down", "up", "ns", 5000, "1.0");

        List<String> aliases = desc.getAliases();
        assertEquals(3, aliases.size());
        assertEquals("a", aliases.get(0));
        assertEquals("b", aliases.get(1));
        assertEquals("c", aliases.get(2));
    }

    @Test
    @DisplayName("normalizeAliases 移除空字符串和 null")
    void normalizeAliasesRemovesBlanksAndNulls() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", Arrays.asList("valid", null, "  ", "", "also-valid"),
                "down", "up", "ns", 5000, "1.0");

        List<String> aliases = desc.getAliases();
        assertEquals(2, aliases.size());
        assertEquals("valid", aliases.get(0));
        assertEquals("also-valid", aliases.get(1));
    }

    @Test
    @DisplayName("normalizeAliases 移除与主名相同的别名")
    void normalizeAliasesRemovesAliasMatchingPrimary() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "tool", Arrays.asList("tool", "alias1", "tool"),
                "down", "up", "ns", 5000, "1.0");

        List<String> aliases = desc.getAliases();
        assertEquals(1, aliases.size());
        assertEquals("alias1", aliases.get(0));
    }

    @Test
    @DisplayName("getAllToolNames 返回主名 + 别名的完整列表")
    void getAllToolNamesReturnsPrimaryPlusAliases() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "primary", Arrays.asList("alias1", "alias2"),
                "down", "up", "ns", 5000, "1.0");

        List<String> allNames = desc.getAllToolNames();
        assertEquals(3, allNames.size());
        assertEquals("primary", allNames.get(0));
        assertEquals("alias1", allNames.get(1));
        assertEquals("alias2", allNames.get(2));
    }

    @Test
    @DisplayName("getAllToolNames 无别名时返回仅包含主名的单元素列表")
    void getAllToolNamesReturnsSingletonWhenNoAliases() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "onlyTool", "down", "up", "ns", 5000, "1.0");

        List<String> allNames = desc.getAllToolNames();
        assertEquals(1, allNames.size());
        assertEquals("onlyTool", allNames.get(0));
    }

    @Test
    @DisplayName("字段值在构造时被 trim - 前后空格被移除")
    void fieldsAreTrimmedOnConstruction() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "  myTool  ", Arrays.asList("  alias1  "),
                "  down  ", "  up  ", "  ns  ", 5000, "  1.0  ");

        assertEquals("myTool", desc.getTool());
        assertEquals("down", desc.getDownAction());
        assertEquals("up", desc.getUpAction());
        assertEquals("ns", desc.getNamespace());
        assertEquals("1.0", desc.getVersion());
        // Aliases are also trimmed via normalizeAliases
        assertEquals(1, desc.getAliases().size());
        assertEquals("alias1", desc.getAliases().get(0));
    }

    @Test
    @DisplayName("toToolName 返回主工具名")
    void toToolNameReturnsPrimaryToolName() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "myTool", "down", "up", "ns", 5000, "1.0");

        assertEquals("myTool", desc.toToolName());
    }

    @Test
    @DisplayName("toDownlinkToolName 返回 downAction")
    void toDownlinkToolNameReturnsDownAction() {
        DirectiveDescriptor desc = new DirectiveDescriptor(
                "myTool", "myDown", "up", "ns", 5000, "1.0");

        assertEquals("myDown", desc.toDownlinkToolName());
    }
}
