package com.lightweightai.kernel.agent.directive;

import java.util.List;

/**
 * 运行时为 {@link DirectiveDescriptor} 提供额外别名的外部源。
 *
 * <p>Java 注解的属性是编译期常量，无法直接注入运行时配置；本接口在
 * {@link DirectiveDescriptor#from(com.lightweightai.kernel.agent.annotation.ClientTool)}
 * 这一唯一转换入口处合并，使得"注解 aliases + 外部配置 aliases"对调用方透明。</p>
 *
 * <p>实现可以是 properties / yaml / Spring `@Value` / Nacos / Apollo……</p>
 */
@FunctionalInterface
public interface ClientToolAliasSource {

    /**
     * @param primaryToolName 注解上的 {@code toolName()}
     * @return 该工具的额外别名列表；返回 null 或空表示无别名
     */
    List<String> aliasesFor(String primaryToolName);
}
