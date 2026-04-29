package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.gateway.GatewayRequest;
import reactor.core.publisher.Flux;

/**
 * 主动触发源 — 让 Orchestrator 不再只依赖外部 Gateway 推送请求。
 *
 * 实现示例:
 * - 定时心跳:每 N 分钟生成 maintenance request
 * - Cron 调度:按 cron 表达式触发指定 agent
 * - 外部 webhook:GitHub / Slack 事件转 GatewayRequest
 *
 * 使用:
 * <pre>
 * orchestrator.subscribeTriggerSource(new IntervalHeartbeat(...));
 * </pre>
 */
public interface TriggerSource {

    /** 用于日志与 unsubscribe 的稳定标识 */
    String name();

    /**
     * 持续推送的请求流。Flux 完成意味着该 source 不再产生 request;
     * 取消订阅(orchestrator.unsubscribeTriggerSource)亦会终止本 source。
     */
    Flux<GatewayRequest> requests();
}
