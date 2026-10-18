package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.agent.AgentRegistry;
import com.lightweightai.kernel.gateway.*;
import com.lightweightai.kernel.memory.MemoryProvider;
import java.util.*;

/** History, summary, clearing and tool memory use the same native store and key mapping. */
public final class NativeSessionManager implements SessionManager {
    private final MemoryProvider source;
    private final AgentRegistry agents;
    private final ChatHandler handler;
    private final String principalId;
    private final String agentId;

    public NativeSessionManager(MemoryProvider source, AgentRegistry agents, ChatHandler handler) {
        this(source, agents, handler, null, agents.getDefault().getAgentId());
    }
    private NativeSessionManager(MemoryProvider source, AgentRegistry agents, ChatHandler handler, String principalId, String agentId) {
        this.source = source; this.agents = agents; this.handler = handler; this.principalId = principalId; this.agentId = agentId;
    }
    @Override public SessionManager forScope(String principal, String agent) {
        String selected = agent == null ? agents.getDefault().getAgentId() : agent;
        if (agents.get(selected).isEmpty()) throw new IllegalArgumentException("Unknown agent: " + selected);
        return new NativeSessionManager(source, agents, handler, principal, selected);
    }
    @Override public MemoryProvider getMemoryProvider() { return source.forScope(principalId, agentId); }
    private String key(String session) { return SessionKeys.main(principalId, agentId, session); }
    @Override public List<Map<String, String>> getSessionHistory(String session) {
        return getMemoryProvider().getHistory(key(session), Integer.MAX_VALUE).stream()
                .map(message -> Map.of("role", message.getRole(), "content", message.getContent())).toList();
    }
    @Override public Map<String, Object> getSessionSummary(String session) {
        var history = getSessionHistory(session);
        return Map.of("sessionId", session, "agentId", agentId, "messageCount", history.size(),
                "lastMessage", history.isEmpty() ? "" : history.get(history.size() - 1).get("content"));
    }
    @Override public void clearSession(String session) {
        if (handler instanceof Orchestrator orchestrator) {
            orchestrator.clearExecutionScope(principalId, agentId, session);
        } else {
            handler.interrupt(principalId, session);
        }
        source.clearExecutionTree(principalId, agentId, key(session));
    }
}
