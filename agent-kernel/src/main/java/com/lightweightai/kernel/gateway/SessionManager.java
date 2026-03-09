package com.lightweightai.kernel.gateway;

import java.util.List;
import java.util.Map;

/**
 * 业务无关的会话管理接口
 *
 * Gateway 通过此接口暴露会话操作，具体存储和语义由业务模块实现。
 */
public interface SessionManager {

    /**
     * 获取会话历史
     */
    List<Map<String, String>> getSessionHistory(String sessionId);

    /**
     * 获取会话摘要
     */
    Map<String, Object> getSessionSummary(String sessionId);

    /**
     * 清空会话
     */
    void clearSession(String sessionId);
}
