package com.lightweightai.kernel.trace;

/**
 * 可插拔的 Span 导出接口。
 *
 * 实现必须线程安全，因为 span 可能从不同线程完成并导出。
 */
@FunctionalInterface
public interface SpanExporter {

    /**
     * 导出一个已完成的 span
     */
    void export(Span span);

    /**
     * 导出器名称（用于调试/日志）
     */
    default String getName() {
        return getClass().getSimpleName();
    }
}
