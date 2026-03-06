package com.lightweightai.mcp;

/**
 * @deprecated 已重命名为 {@link ToolClient}，请使用 {@code ToolClient.create()} 代替。
 */
@Deprecated(forRemoval = true)
public class McpToolManager {

    private McpToolManager() {
    }

    /**
     * @deprecated 请使用 {@link ToolClient#create()} 代替
     */
    @Deprecated(forRemoval = true)
    public static ToolClient.Builder create() {
        return ToolClient.create();
    }
}
