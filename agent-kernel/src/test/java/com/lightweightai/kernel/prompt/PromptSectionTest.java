package com.lightweightai.kernel.prompt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PromptSectionTest {

    @Test
    void unconditionalSection() {
        PromptSection section = PromptSection.of("identity", 0, "You are a helper.");
        assertEquals("identity", section.getId());
        assertEquals(0, section.getOrder());
        assertEquals("You are a helper.", section.getContent());
    }

    @Test
    void unconditionalAlwaysActive() {
        PromptSection section = PromptSection.of("always", 5, "content");
        assertTrue(section.isActive(PromptRequest.builder().build()));
    }

    @Test
    void conditionalInactive() {
        PromptSection section = PromptSection.conditional("test", 0, req -> false, "content");
        assertFalse(section.isActive(PromptRequest.builder().build()));
    }

    @Test
    void conditionalActive() {
        PromptSection section = PromptSection.conditional("test", 0, req -> true, "content");
        assertTrue(section.isActive(PromptRequest.builder().build()));
    }

    @Test
    void orderComparison() {
        assertTrue(PromptSection.of("a", 0, "f").getOrder() < PromptSection.of("b", 10, "s").getOrder());
    }
}
