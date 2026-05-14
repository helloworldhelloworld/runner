package com.lightweightai.kernel.prompt;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PromptSection")
class PromptSectionTest {

    private PromptRequest request;

    @BeforeEach
    void setUp() {
        request = PromptRequest.builder()
            .userMessage("test")
            .build();
    }

    @Test
    @DisplayName("of() creates an always-active section")
    void ofCreatesAlwaysActiveSection() {
        PromptSection section = PromptSection.of("identity", 0, "You are helpful.");

        assertTrue(section.isActive(request), "Section created with of() should always be active");
    }

    @Test
    @DisplayName("of() fields are accessible via getters")
    void ofFieldsAreAccessible() {
        PromptSection section = PromptSection.of("identity", 5, "You are helpful.");

        assertEquals("identity", section.getId());
        assertEquals(5, section.getOrder());
        assertEquals("You are helpful.", section.getContent());
    }

    @Test
    @DisplayName("conditional() is active when condition returns true")
    void conditionalActiveWhenTrue() {
        PromptSection section = PromptSection.conditional(
            "debug", 10,
            req -> req.getUserMessage().contains("test"),
            "Debug mode instructions."
        );

        assertTrue(section.isActive(request),
            "Section should be active because request message contains 'test'");
    }

    @Test
    @DisplayName("conditional() is inactive when condition returns false")
    void conditionalInactiveWhenFalse() {
        PromptSection section = PromptSection.conditional(
            "plan-mode", 10,
            req -> req.getUserMessage().contains("plan"),
            "Plan mode instructions."
        );

        assertFalse(section.isActive(request),
            "Section should be inactive because request message does not contain 'plan'");
    }

    @Test
    @DisplayName("Order and id are accessible on conditional section")
    void orderAndIdAccessibleOnConditional() {
        PromptSection section = PromptSection.conditional(
            "safety", 99,
            req -> true,
            "Safety rules."
        );

        assertEquals("safety", section.getId());
        assertEquals(99, section.getOrder());
    }

    @Test
    @DisplayName("Content is accessible on conditional section")
    void contentAccessibleOnConditional() {
        String content = "This is the prompt content for coding mode.";
        PromptSection section = PromptSection.conditional(
            "code-mode", 20,
            req -> true,
            content
        );

        assertEquals(content, section.getContent());
    }
}
