package com.lightweightai.kernel.agent.directive;

/**
 * Directive 编排器，用于组装下发到客户端的指令列表。
 */
public interface DirectiveManager {

    /**
     * 生成可直接下发给客户端的 Directive 消息内容（JSON 字符串）。
     *
     * @param requestId 本次请求 ID（WebSocketMessage.request_id）
     * @param primary 主指令（通常由当前工具调用转换而来）
     * @return 可直接发送的 JSON 字符串
     */
    String getResult(String requestId, Directive primary);
}
