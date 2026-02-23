package com.lightweightai.kernel.agent;

import java.util.List;
import java.util.Map;
import java.util.Collections;

/**
 * 工具元数据接口
 *
 * 工具可选实现此接口以提供额外的元信息：
 * - 分类（用于组织工具）
 * - 标签（用于灵活查询）
 * - 版本信息
 * - 作者信息
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
}
