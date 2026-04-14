package com.lightweightai.web.gateway;

import com.lightweightai.kernel.gateway.SessionManager;
import com.lightweightai.web.service.SoulComfortChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Gateway 服务层 - SessionManager 实现
 *
 * ChatHandler 职责已迁移到 Orchestrator（见 OrchestratorConfig）。
 * 此类只保留 SessionManager 实现，委托给 SoulComfortChatService。
 */
@Service
public class GatewayService implements SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(GatewayService.class);

    private final SoulComfortChatService soulComfortService;

    public GatewayService(SoulComfortChatService soulComfortService) {
        this.soulComfortService = soulComfortService;
    }

    // ==================== SessionManager ====================

    @Override
    public List<Map<String, String>> getSessionHistory(String sessionId) {
        return soulComfortService.getSessionHistory(sessionId);
    }

    @Override
    public Map<String, Object> getSessionSummary(String sessionId) {
        return soulComfortService.getSessionSummary(sessionId);
    }

    @Override
    public void clearSession(String sessionId) {
        soulComfortService.clearSession(sessionId);
    }
}
