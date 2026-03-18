package com.lightweightai.kernel.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 工具元数据接口
 *
 * 工具可选实现此接口以提供额外的元信息：
 * - 分类（用于组织工具）
 * - 标签（用于灵活查询）
 * - 版本信息
 * - 作者信息
 * - 行为注解（与 MCP ToolAnnotations 对齐）
 */
public interface ToolMetadata {

    /**
     * 获取工具分类
     *
     * 例如："weather", "math", "file", "api"
     */
    default String getCategory() {
        return "default";
    }

    /**
     * 获取工具标签
     *
     * 例如：["api", "external", "async"]
     */
    default List<String> getTags() {
        return Collections.emptyList();
    }

    /**
     * 获取工具版本
     */
    default String getVersion() {
        return "1.0.0";
    }

    /**
     * 获取工具作者
     */
    default String getAuthor() {
        return "";
    }

    /**
     * 获取额外元数据
     */
    default Map<String, Object> getExtraMetadata() {
        return Collections.emptyMap();
    }

    // ==================== MCP 行为注解 ====================

    /**
     * 工具是否只读（不修改外部状态）
     *
     * 对应 MCP ToolAnnotations.readOnlyHint
     * 例如：查询天气、读取文件内容等
     */
    default boolean isReadOnly() {
        return false;
    }

    /**
     * 工具是否具有破坏性（可能导致不可逆的更改）
     *
     * 对应 MCP ToolAnnotations.destructiveHint
     * 例如：删除文件、清空数据库等
     */
    default boolean isDestructive() {
        return false;
    }

    /**
     * 工具是否幂等（多次调用与一次调用效果相同）
     *
     * 对应 MCP ToolAnnotations.idempotentHint
     * 例如：设置配置值、PUT 请求等
     */
    default boolean isIdempotent() {
        return false;
    }

    /**
     * 工具是否与外部世界交互（非封闭环境）
     *
     * 对应 MCP ToolAnnotations.openWorldHint
     * 例如：网络请求、外部 API 调用等
     */
    default boolean isOpenWorld() {
        return true;
    }

    /**
     * 工具是否在客户端执行
     *
     * 如果为 true，工具不会在服务端调用 execute()，而是通过 WebSocket
     * 派发到客户端（浏览器/App）执行，服务端阻塞等待客户端回传结果。
     *
     * 适用于需要设备能力的工具（拍照、GPS、签名等），
     * 或虽然定义在服务端但实际逻辑需要在端上运行的工具。
     *
     * 自定义工具通过 {@code @ToolFunction(clientSide = true)} 标记；
     * MCP 工具通过配置或构造时指定。
     */
    default boolean isClientSide() {
        return false;
    }
}
