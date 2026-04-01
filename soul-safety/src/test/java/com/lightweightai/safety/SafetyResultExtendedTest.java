package com.lightweightai.safety;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SafetyResult - 安全检查结果扩展测试")
class SafetyResultExtendedTest {

    @Test
    @DisplayName("safe 返回 SAFE 级别")
    void safeResult() {
        SafetyResult result = SafetyResult.safe();

        assertEquals(SafetyResult.Level.SAFE, result.level());
        assertFalse(result.isCrisis());
        assertTrue(result.matchedKeywords().isEmpty());
        assertTrue(result.resources().isEmpty());
    }

    @Test
    @DisplayName("crisis 返回 CRISIS 级别并包含关键词和资源")
    void crisisResult() {
        List<String> keywords = List.of("自杀", "轻生");
        SafetyResult result = SafetyResult.crisis(keywords, CrisisResource.DEFAULTS);

        assertEquals(SafetyResult.Level.CRISIS, result.level());
        assertTrue(result.isCrisis());
        assertEquals(2, result.matchedKeywords().size());
        assertFalse(result.resources().isEmpty());
    }
}
