package com.lightweightai.tools;

import com.lightweightai.kernel.agent.Tool;
import com.lightweightai.kernel.agent.ToolSource;
import com.lightweightai.kernel.agent.annotation.AnnotatedToolScanner;
import com.lightweightai.tools.math.MathTools;
import com.lightweightai.tools.time.TimeTools;
import com.lightweightai.tools.web.WebTools;
import java.util.ArrayList;
import java.util.List;

/**
 * agent-tools 模块的工具来源（SPI 入口）
 *
 * 通过 ToolSource SPI 自动发现，将注解方式定义的工具暴露给 ToolScanner。
 */
public class AgentToolsSource implements ToolSource {

    @Override
    public List<Tool> discoverTools() {
        List<Tool> tools = new ArrayList<>();
        tools.addAll(AnnotatedToolScanner.scan(new MathTools()));
        tools.addAll(AnnotatedToolScanner.scan(new TimeTools()));
        tools.addAll(AnnotatedToolScanner.scan(new WebTools()));
        return tools;
    }
}
