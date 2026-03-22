package com.lightweightai.kernel.agent;

import java.util.function.Predicate;

/**
 * SPI 扫描 Provider — 包装现有 ToolScanner + ToolSource SPI 逻辑。
 *
 * 零行为变更，只是把 registry.scanAndRegister() 包进 ToolSourceProvider 接口。
 */
public class LegacyScanProvider implements ToolSourceProvider {

    private final Predicate<Tool> filter;

    public LegacyScanProvider() {
        this(null);
    }

    public LegacyScanProvider(Predicate<Tool> filter) {
        this.filter = filter;
    }

    @Override
    public String sourceType() {
        return "spi-scan";
    }

    @Override
    public void start(ToolRegistry registry) {
        if (filter != null) {
            registry.scanAndRegister(filter);
        } else {
            registry.scanAndRegister();
        }
    }
}
