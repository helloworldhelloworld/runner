package com.lightweightai.kernel.trace;

/**
 * Span 类型，对齐 OpenTelemetry 语义。
 */
public enum SpanKind {

    /** 服务端处理请求 */
    SERVER,

    /** 客户端发起请求（LLM API 调用、MCP HTTP 调用） */
    CLIENT,

    /** 内部操作（Agent Loop 迭代、记忆检索、Prompt 构建） */
    INTERNAL,

    /** 异步消息生产者 */
    PRODUCER,

    /** 异步消息消费者 */
    CONSUMER
}
