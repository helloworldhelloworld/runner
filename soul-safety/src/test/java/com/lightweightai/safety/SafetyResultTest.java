package com.lightweightai.safety;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SafetyResult - 安全检查结果")
class SafetyResultTest {

    @Test
    @DisplayName("safe()返回SAFE级别")
    void shouldCreateSafeResult() {
        SafetyResult result = SafetyResult.safe();
        assertEquals(SafetyResult.Level.SAFE, result.level());
        assertFalse(result.isCrisis());
        assertTrue(result.matchedKeywords().isEmpty());
        assertTrue(result.resources().isEmpty());
    }

    @Test
    @DisplayName("crisis()返回CRISIS级别并包含关键词和资源")
    void shouldCreateCrisisResult() {
        List<String> keywords = List.of("自杀", "不想活了");
        List<CrisisResource> resources = CrisisResource.DEFAULTS;

        SafetyResult result = SafetyResult.crisis(keywords, resources);
        assertEquals(SafetyResult.Level.CRISIS, result.level());
        assertTrue(result.isCrisis());
        assertEquals(2, result.matchedKeywords().size());
        assertFalse(result.resources().isEmpty());
    }

    @Test
    @DisplayName("isCrisis()对SAFE返回false")
    void isCrisisShouldReturnFalseForSafe() {
        assertFalse(SafetyResult.safe().isCrisis());
    }

    @Test
    @DisplayName("isCrisis()对CRISIS返回true")
    void isCrisisShouldReturnTrueForCrisis() {
        assertTrue(SafetyResult.crisis(List.of("test"), List.of()).isCrisis());
    }

    @Test
    @DisplayName("CrisisResource默认列表不为空")
    void defaultCrisisResourcesShouldNotBeEmpty() {
        assertFalse(CrisisResource.DEFAULTS.isEmpty());
        assertTrue(CrisisResource.DEFAULTS.size() >= 3);
    }

    @Test
    @DisplayName("CrisisResource包含名称、电话和描述")
    void crisisResourceShouldHaveAllFields() {
        CrisisResource resource = CrisisResource.DEFAULTS.get(0);
        assertNotNull(resource.name());
        assertNotNull(resource.phone());
        assertNotNull(resource.description());
        assertFalse(resource.name().isBlank());
        assertFalse(resource.phone().isBlank());
    }
}
