package com.lightweightai.web.config;

import com.lightweightai.kernel.gateway.ChatHandler;
import com.lightweightai.openclaw.OpenClawChatHandler;
import com.lightweightai.openclaw.OpenClawClient;
import com.lightweightai.openclaw.ws.WebSocketOpenClawClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 脑选择（ADR-014）：`app.brain.type` 决定哪个 {@link ChatHandler} 成为大脑平面的"脑"。
 *
 * <ul>
 *   <li>{@code native}（默认）：{@code OrchestratorConfig.orchestratorChatHandler}（runner 自研 Orchestrator）。</li>
 *   <li>{@code openclaw}：本类装配 {@link OpenClawChatHandler}（OpenClaw 完整大脑），@Primary 替换。</li>
 * </ul>
 *
 * <p>两个 {@code @Primary ChatHandler} 由互斥的 {@code @ConditionalOnProperty} 保证<b>同时只存在一个</b>，
 * 避免 NoUniqueBeanDefinition。下游 Gateway / 语音平面 / WS / 安全检测对脑实现泛型无关、零改动。
 */
@Configuration
public class BrainConfig {

    private static final Logger logger = LoggerFactory.getLogger(BrainConfig.class);

    /** OpenClaw 客户端（仅 openclaw 模式装配）。真协议为 ADR-014 阶段 3 占位骨架。 */
    @Bean
    @ConditionalOnProperty(name = "app.brain.type", havingValue = "openclaw")
    public OpenClawClient openClawClient(@Value("${app.openclaw.url:ws://127.0.0.1:18789}") String url) {
        logger.warn("Brain = OpenClaw（app.brain.type=openclaw）：WebSocketOpenClawClient 真协议为阶段3占位，"
                + "chat 落地前会报错。url={}", url);
        return new WebSocketOpenClawClient(url);
    }

    /** OpenClaw 完整大脑（@Primary 替换 native Orchestrator）。 */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.brain.type", havingValue = "openclaw")
    public ChatHandler openClawChatHandler(OpenClawClient openClawClient) {
        logger.info("Brain = OpenClaw：OpenClawChatHandler 作为 primary ChatHandler");
        return new OpenClawChatHandler(openClawClient);
    }
}
