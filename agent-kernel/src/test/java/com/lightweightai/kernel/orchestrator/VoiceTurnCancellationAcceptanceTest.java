package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentProfile;
import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.gateway.Gateway;
import com.lightweightai.kernel.gateway.GatewayRequest;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.memory.InMemoryProvider;
import com.lightweightai.kernel.testsupport.CapturingLLMProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.List;

import static com.lightweightai.kernel.core.StreamEvent.EventType.*;
import static org.junit.jupiter.api.Assertions.*;

/** Real Gateway → Orchestrator → AgentLoop; cancellation precedes LLM subscription. */
class VoiceTurnCancellationAcceptanceTest {
    private final CapturingLLMProvider provider = CapturingLLMProvider.endTurn("B reply");
    private Orchestrator orchestrator;
    private Gateway gateway;

    @BeforeEach
    void setUp() {
        AgentRegistry registry = new AgentRegistry();
        registry.register(AgentProfile.builder().agentId("minion").systemPrompt("Assistant").build());
        registry.setDefault("minion");
        orchestrator = new Orchestrator(registry,
                new AgentFactory(provider, new InMemoryProvider(), new ToolRegistry()),
                new MetadataAgentRouter(registry));
        gateway = Gateway.builder().chatHandler(orchestrator).build();
    }

    @AfterEach
    void close() {
        orchestrator.shutdown();
    }

    @ParameterizedTest(name = "cancel after {0} prefix events")
    @ValueSource(ints = {0, 1, 2})
    void cancelledPrefixDoesNotLeaveAnActiveRunOrPolluteTheNextRequest(int prefixEvents) {
        Flux<StreamEvent> a = gateway.handleStreamReactive(request("cancelled A"));
        if (prefixEvents > 0) {
            List<StreamEvent> prefix = a.take(prefixEvents).collectList().block(Duration.ofSeconds(3));
            assertNotNull(prefix);
            assertEquals(prefixEvents, prefix.size());
            assertEquals(TRACE, prefix.get(0).getType());
            if (prefixEvents == 2) assertEquals(AGENT_ROUTE, prefix.get(1).getType());
        }

        List<StreamEvent> b = gateway.handleStreamReactive(request("完整的 B 输入"))
                .collectList().block(Duration.ofSeconds(3));
        assertNotNull(b);
        assertEquals(List.of("完整的 B 输入"), provider.lastMessages().stream()
                .filter(m -> m.getRole() == ConversationMessage.MessageRole.USER)
                .map(ConversationMessage::getTextContent).toList());
        assertEquals(1, provider.callCount(), "Only B may reach the LLM");
        assertFalse(b.stream().anyMatch(e -> e.getType() == AGENT_INTERRUPT
                        || e.getType() == SPEECH_INTERRUPTED || e.getType() == AGENT_RESUME),
                "B must not receive interrupt/resume events for an unsubscribed A: "
                        + b.stream().map(StreamEvent::getType).toList());
    }

    private GatewayRequest request(String message) {
        return GatewayRequest.builder().sessionId("prefix-cancel").message(message).build();
    }
}
