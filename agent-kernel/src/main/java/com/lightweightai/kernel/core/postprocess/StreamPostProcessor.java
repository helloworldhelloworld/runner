package com.lightweightai.kernel.core.postprocess;

import com.lightweightai.kernel.core.StreamEvent;
import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * 流式后处理器 - 可组合的流式事件转换器
 *
 * 每个处理器是一个 {@code Function<Flux<StreamEvent>, Flux<StreamEvent>>}，
 * 与 Reactor 的 {@code .transform()} 操作符天然兼容。
 *
 * 处理器可以：
 * - 转换事件（修改 TEXT_DELTA 内容）
 * - 注入新事件（追加卡片、标注）
 * - 跨事件累积状态（风控检查）
 * - 过滤/抑制事件
 */
public interface StreamPostProcessor extends Function<Flux<StreamEvent>, Flux<StreamEvent>> {

    /**
     * 处理器名称，用于日志和调试
     */
    String getName();

    /**
     * 执行优先级。数字越小越先执行。默认 100。
     */
    default int getOrder() {
        return 100;
    }
}
