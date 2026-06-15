package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.memory.MemoryProvider;
import com.lightweightai.kernel.memory.MemorySearchResult;
import com.lightweightai.kernel.memory.Message;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 守护"跨会话记得用户"——durable 长期记忆**每轮确定性注入系统提示**，不依赖 search/embedding
 * （mock embedding 下语义召回基本失效，曾导致新会话问"我叫什么"答不出）。只要写进 durable，
 * 下一轮(哪怕新 session)系统提示里就一定有。
 */
@DisplayName("Acceptance: durable 长期记忆确定性注入系统提示")
class DurableMemoryInjectionAcceptanceTest {

    @Test
    @DisplayName("durable 内容出现在传给 LLM 的系统消息里（不靠搜索命中）")
    void durableInjectedIntoSystemPrompt() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder()
                .agentId("minion").systemPrompt("你是小黄人").build());
        registry.setDefault("minion");

        CapturingLLMProvider spy = CapturingLLMProvider.endTurn("好的");
        // search() 故意返回空（模拟 mock embedding 召回失败）；只有 durable 有用户名字。
        MemoryProvider mem = new DurableOnlyMemory("## 用户信息\n- 用户姓名: 小杰\n");
        AgentFactory factory = new AgentFactory(spy, mem, new ToolRegistry());
        Orchestrator orchestrator =
                new Orchestrator(registry, factory, new MetadataAgentRouter(registry));

        orchestrator.chatStreamReactive(GatewayRequest.builder()
                .sessionId("new-session").message("我叫什么名字？").build())
                .collectList().block();

        ConversationMessage system = spy.lastMessages().get(0);
        assertEquals(ConversationMessage.MessageRole.SYSTEM, system.getRole());
        assertTrue(system.getTextContent().contains("小杰"),
                "durable 长期记忆应注入系统提示(确定性,不靠搜索): " + system.getTextContent());
    }

    /** search 恒空，仅 durable 有内容——证明注入靠 durable 而非搜索命中。 */
    private static class DurableOnlyMemory implements MemoryProvider {
        private final String durable;
        DurableOnlyMemory(String durable) { this.durable = durable; }
        @Override public void addMessage(String s, Message m) {}
        @Override public List<Message> getHistory(String s, int l) { return List.of(); }
        @Override public void clearSession(String s) {}
        @Override public void writeEphemeral(String c) {}
        @Override public void writeDurable(String s, String c) {}
        @Override public List<MemorySearchResult> search(String q) { return List.of(); }
        @Override public String readDurable() { return durable; }
    }
}
