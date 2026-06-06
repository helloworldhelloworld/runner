package com.lightweightai.mcp;

import com.lightweightai.kernel.agent.RiskLevel;
import io.modelcontextprotocol.spec.McpSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 守护 ADR-007：风险等级跨 MCP 边界恢复。
 *
 * 缺口（修复前）：{@link McpToolWrapper} 不覆写 {@code riskLevel()}，所有远程工具被
 * 风险闸当作默认 SAFE —— fail-open，收紧策略挡不住远程物理动作。
 *
 * 契约（修复后）：_meta 显式风险 > 标准 annotations 推导 > 兜底 SAFE。
 */
@DisplayName("McpToolWrapper: 风险等级跨 MCP 恢复（ADR-007）")
class McpToolWrapperRiskLevelTest {

    private static final String META_RISK_KEY = "com.lightweightai.kernel/riskLevel";

    private static McpToolWrapper wrap(McpSchema.ToolAnnotations annotations, Map<String, Object> meta) {
        McpSchema.Tool tool = new McpSchema.Tool(
                "move", null, "做一个动作", null, null, annotations, meta);
        return new McpToolWrapper(null, tool, "minion-body");
    }

    private static McpSchema.ToolAnnotations annotations(Boolean readOnly, Boolean destructive) {
        return new McpSchema.ToolAnnotations(null, readOnly, destructive, null, null, null);
    }

    @Test
    @DisplayName("无注解无 _meta → 兜底 SAFE（向后兼容）")
    void defaultsToSafe() {
        assertEquals(RiskLevel.SAFE, wrap(null, null).riskLevel());
    }

    @Test
    @DisplayName("readOnlyHint=true → SAFE（只读/抓帧，如 look）")
    void readOnlyMapsToSafe() {
        assertEquals(RiskLevel.SAFE, wrap(annotations(true, null), null).riskLevel());
    }

    @Test
    @DisplayName("destructiveHint=true → SYSTEM（物理动作，如 move）")
    void destructiveMapsToSystem() {
        assertEquals(RiskLevel.SYSTEM, wrap(annotations(false, true), null).riskLevel());
    }

    @Test
    @DisplayName("annotations 存在但非只读非破坏 → WRITE（如 set_eyes 改显示）")
    void sideEffectingButNonDestructiveMapsToWrite() {
        assertEquals(RiskLevel.WRITE, wrap(annotations(false, false), null).riskLevel());
    }

    @Test
    @DisplayName("_meta 显式风险优先于 annotations")
    void explicitMetaWinsOverAnnotations() {
        // annotations 说只读(SAFE)，但 _meta 显式声明 SYSTEM → 以 _meta 为准
        McpToolWrapper w = wrap(annotations(true, null), Map.of(META_RISK_KEY, "SYSTEM"));
        assertEquals(RiskLevel.SYSTEM, w.riskLevel());
    }

    @Test
    @DisplayName("_meta 风险大小写不敏感")
    void explicitMetaCaseInsensitive() {
        assertEquals(RiskLevel.WRITE, wrap(null, Map.of(META_RISK_KEY, "write")).riskLevel());
    }

    @Test
    @DisplayName("_meta 非法值 → 回退到 annotations 推导")
    void invalidMetaFallsBackToAnnotations() {
        McpToolWrapper w = wrap(annotations(false, true), Map.of(META_RISK_KEY, "bogus"));
        assertEquals(RiskLevel.SYSTEM, w.riskLevel());
    }
}
