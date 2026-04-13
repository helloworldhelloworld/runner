package com.lightweightai.kernel.prompt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptSection - modular prompt fragments with conditional activation")
class PromptSectionTest {

    // ==================== unconditional sections ====================

    @Test
    @DisplayName("of creates always-active section")
    void ofCreatesAlwaysActive() {
        PromptSection section = PromptSection.of("identity", 0, "You are a helpful assistant.");

        assertEquals("identity", section.getId());
        assertEquals(0, section.getOrder());
        assertEquals("You are a helpful assistant.", section.getContent());

        // Always active for any request
        PromptRequest request = PromptRequest.builder()
            .userMessage("hello")
            .build();
        assertTrue(section.isActive(request));
    }

    // ==================== conditional sections ====================

    @Test
    @DisplayName("conditional creates section active only when condition is met")
    void conditionalActivatesWhenConditionMet() {
        PromptSection section = PromptSection.conditional(
            "debug-mode", 10,
            req -> req.getContext().containsKey("debug"),
            "Debug mode is enabled."
        );

        PromptRequest withDebug = PromptRequest.builder()
            .userMessage("test")
            .context("debug", true)
            .build();
        assertTrue(section.isActive(withDebug));

        PromptRequest withoutDebug = PromptRequest.builder()
            .userMessage("test")
            .build();
        assertFalse(section.isActive(withoutDebug));
    }

    @Test
    @DisplayName("conditional section preserves id, order, and content")
    void conditionalPreservesFields() {
        PromptSection section = PromptSection.conditional(
            "plan-mode", 5, req -> true, "Plan mode instructions"
        );

        assertEquals("plan-mode", section.getId());
        assertEquals(5, section.getOrder());
        assertEquals("Plan mode instructions", section.getContent());
    }

    // ==================== ordering ====================

    @Test
    @DisplayName("sections can be sorted by order")
    void sectionsCanBeSortedByOrder() {
        PromptSection s1 = PromptSection.of("first", 0, "First");
        PromptSection s2 = PromptSection.of("second", 10, "Second");
        PromptSection s3 = PromptSection.of("third", 5, "Third");

        var sorted = java.util.stream.Stream.of(s3, s1, s2)
            .sorted(java.util.Comparator.comparingInt(PromptSection::getOrder))
            .toList();

        assertEquals("first", sorted.get(0).getId());
        assertEquals("third", sorted.get(1).getId());
        assertEquals("second", sorted.get(2).getId());
    }
}
