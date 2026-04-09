package com.lightweightai.kernel.gateway;

import java.util.List;
import java.util.Map;

/**
 * 业务无关的会话管理接口
 *
 * Gateway 通过此接口暴露会话操作，具体存储和语义由业务模块实现。
 *
 * 生命周期（参考 Claude Code SessionStart/Stop + OpenClaw Gateway session 模型）：
 * - onSessionStart: 会话首次创建或恢复时调用
 * - onSessionStop:  会话主动关闭或超时清理时调用
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

    // ==================== 生命周期 ====================

    /**
     * 会话开始时回调 — 首次创建或恢复
     *
     * 实现可用于：加载环境变量、预热上下文、初始化用户 profile 等。
     *
     * @param sessionId 会话 ID
     * @param context   启动上下文（设备信息、渠道来源、用户 ID 等）
     */
    default void onSessionStart(String sessionId, SessionContext context) {}

    /**
     * 会话结束时回调 — 主动关闭或超时淘汰
     *
     * 实现可用于：持久化摘要、释放资源、写入审计日志等。
     *
     * @param sessionId 会话 ID
     */
    default void onSessionStop(String sessionId) {}

    /**
     * 判断会话是否存活
     */
    default boolean isSessionAlive(String sessionId) {
        return true;
    }

    // ==================== 会话上下文 ====================

    /**
     * 会话启动上下文
     */
    class SessionContext {
        private final String userId;
        private final String deviceId;
        private final String channel;
        private final Map<String, Object> extra;

        public SessionContext(String userId, String deviceId, String channel, Map<String, Object> extra) {
            this.userId = userId;
            this.deviceId = deviceId;
            this.channel = channel;
            this.extra = extra != null ? Map.copyOf(extra) : Map.of();
        }

        public static SessionContext simple(String userId) {
            return new SessionContext(userId, null, null, null);
        }

        public String getUserId() { return userId; }
        public String getDeviceId() { return deviceId; }
        public String getChannel() { return channel; }
        public Map<String, Object> getExtra() { return extra; }
    }
}
