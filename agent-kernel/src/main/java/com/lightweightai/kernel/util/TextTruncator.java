package com.lightweightai.kernel.util;

/**
 * 防御性字符串截断 —— 用于日志、WS 帧里的工具输出。
 *
 * 超出上限时保留前缀 + 省略标记 "…[+N chars]"，让读者知道被截了多少。
 * null-safe，maxLen &lt;= 0 视为不截断。
 */
public final class TextTruncator {

    private TextTruncator() {}

    public static String truncate(String s, int maxLen) {
        if (s == null) return null;
        if (maxLen <= 0 || s.length() <= maxLen) return s;
        int remaining = s.length() - maxLen;
        return s.substring(0, maxLen) + "…[+" + remaining + " chars]";
    }
}
