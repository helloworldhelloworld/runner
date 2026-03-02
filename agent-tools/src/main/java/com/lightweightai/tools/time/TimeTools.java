package com.lightweightai.tools.time;

import com.lightweightai.kernel.agent.annotation.ToolFunction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具集（注解方式）
 */
public class TimeTools {

    @ToolFunction(
        name = "get_time",
        description = "Get the current date and time",
        category = "utility",
        tags = {"utility", "time"},
        readOnly = true,
        idempotent = true
    )
    public String getTime() {
        return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }
}
