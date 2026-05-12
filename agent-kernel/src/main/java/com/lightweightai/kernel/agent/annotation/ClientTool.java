package com.lightweightai.kernel.agent.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 客户端 Tool/Directive 元数据注解。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ClientTool {

    /**
     * 工具名（LLM 可见名的一部分）。
     */
    String toolName();

    /**
     * 额外的工具别名。多个 toolName 共享同一组 down/up action 时使用。
     *
     * <p>每个别名都会作为独立的 LLM 可见工具注册，但底层下行/上行指令保持一致。</p>
     */
    String[] aliases() default {};

    /**
     * 下行指令名（Server -> Client，对应 header.name）。
     */
    String downAction();

    /**
     * 上行结果名（Client -> Server，对应 header.name）。
     */
    String upAction();

    /**
     * 指令命名空间（如 GeoInformation / Camera）。
     */
    String namespace();

    /**
     * 超时时间（毫秒）。
     */
    long timeoutMs() default 60_000;

    /**
     * 协议版本。
     */
    String version() default "1.0";
}
