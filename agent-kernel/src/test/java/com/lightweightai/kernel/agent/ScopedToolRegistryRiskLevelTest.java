package com.lightweightai.kernel.agent;

import com.lightweightai.kernel.llm.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ScopedToolRegistry - RiskLevel 策略与 isEnabled/register 委托")
class ScopedToolRegistryRiskLevelTest {

    private ToolRegistry parent;

    @BeforeEach
    void setUp() {
        parent = new ToolRegistry();
        parent.register(toolWithRisk("read_file", RiskLevel.SAFE));
        parent.register(toolWithRisk("write_file", RiskLevel.WRITE));
        parent.register(toolWithRisk("shell_exec", RiskLevel.SYSTEM));
        parent.register(toolWithRisk("search", RiskLevel.SAFE));
    }

    @Test
    @DisplayName("byRiskLevel(WRITE) 过滤掉 SYSTEM 工具")
    void riskLevelWriteFilterSystem() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("risked")
                .toolPolicy(ToolPolicy.chain(
                        ToolPolicy.byRiskLevel(RiskLevel.WRITE),
                        ToolPolicy.allowAll()))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertTrue(scoped.has("read_file"));
        assertTrue(scoped.has("write_file"));
        assertFalse(scoped.has("shell_exec"),
                "SYSTEM 级超过 WRITE 上限应被过滤");
        assertEquals(3, scoped.getAll().size());
    }

    @Test
    @DisplayName("byRiskLevel(SAFE) 只保留 SAFE 工具")
    void riskLevelSafeOnlySafe() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("safe-only")
                .toolPolicy(ToolPolicy.chain(
                        ToolPolicy.byRiskLevel(RiskLevel.SAFE),
                        ToolPolicy.allowAll()))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertTrue(scoped.has("read_file"));
        assertTrue(scoped.has("search"));
        assertFalse(scoped.has("write_file"));
        assertFalse(scoped.has("shell_exec"));
    }

    @Test
    @DisplayName("isEnabled 组合 parent disabled + policy 许可")
    void isEnabledCombinesParentAndPolicy() {
        parent.disable("read_file");

        AgentProfile profile = AgentProfile.builder()
                .agentId("combo")
                .toolAllowList(Set.of("read_file", "search"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertFalse(scoped.isEnabled("read_file"),
                "read_file 在 parent 中被 disable,即使在 allowList 中也不应 enabled");
        assertTrue(scoped.isEnabled("search"));
    }

    @Test
    @DisplayName("register 委托给 parent — 新工具出现在两者中")
    void registerDelegatesToParent() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("delegate")
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        Tool newTool = toolWithRisk("new_tool", RiskLevel.SAFE);
        scoped.register(newTool);

        assertTrue(parent.has("new_tool"),
                "通过 ScopedToolRegistry 注册的工具应出现在 parent 中");
        assertTrue(scoped.has("new_tool"));
    }

    @Test
    @DisplayName("size 和 enabledCount 反映过滤后的数量")
    void sizeAndEnabledCountReflectFiltering() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("counted")
                .toolDenyList(Set.of("shell_exec", "write_file"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        assertEquals(2, scoped.size());
        assertEquals(2, scoped.enabledCount());
    }

    @Test
    @DisplayName("getToolDefinitions 仅包含被许可的工具定义 payload")
    void getToolDefinitionsPayload() {
        AgentProfile profile = AgentProfile.builder()
                .agentId("payload")
                .toolAllowList(Set.of("read_file"))
                .build();
        ScopedToolRegistry scoped = new ScopedToolRegistry(parent, profile);

        List<Map<String, Object>> defs = scoped.getToolDefinitions();
        assertEquals(1, defs.size());
        assertEquals("read_file", defs.get(0).get("name"));
        assertNotNull(defs.get(0).get("description"));
        assertNotNull(defs.get(0).get("input_schema"));
    }

    private static Tool toolWithRisk(String name, RiskLevel risk) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return name + " tool"; }
            @Override public ToolSchema getSchema() { return ToolSchema.empty(); }
            @Override public ToolResult execute(Map<String, Object> args) { return ToolResult.success("ok"); }
            @Override public RiskLevel riskLevel() { return risk; }
        };
    }
}
