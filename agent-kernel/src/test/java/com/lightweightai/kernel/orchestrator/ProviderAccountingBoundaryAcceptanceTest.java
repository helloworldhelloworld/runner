package com.lightweightai.kernel.orchestrator;

import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.claude.ClaudeProvider;
import com.lightweightai.kernel.llm.openrouter.OpenRouterProvider;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class ProviderAccountingBoundaryAcceptanceTest {
    private static final List<ConversationMessage> INPUT = List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER).textContent("hello").build());

    @ParameterizedTest @ValueSource(booleans={false,true})
    void claudeCacheTokensReachTheFinalTotalInput(boolean streaming) throws Exception {
        try (var remote = new MockWebServer()) {
            remote.start();
            String usage = "{\"input_tokens\":25,\"cache_creation_input_tokens\":100,\"cache_read_input_tokens\":1000,\"output_tokens\":5}";
            remote.enqueue(streaming
                    ? new MockResponse().setHeader("Content-Type", "text/event-stream").setBody(
                        "data: {\"type\":\"message_start\",\"message\":{\"usage\":"+usage+"}}\n\n"
                        + "data: {\"type\":\"content_block_delta\",\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"done\"}}\n\n"
                        + "data: {\"type\":\"message_delta\",\"delta\":{\"stop_reason\":\"end_turn\"},\"usage\":{\"output_tokens\":7}}\n\n"
                        + "data: {\"type\":\"message_stop\"}\n\n")
                    : new MockResponse().setHeader("Content-Type", "application/json").setBody(
                        "{\"content\":[{\"type\":\"text\",\"text\":\"done\"}],\"stop_reason\":\"end_turn\",\"usage\":"+usage+"}"));
            var client = new OkHttpClient.Builder().addInterceptor(chain -> chain.proceed(chain.request().newBuilder()
                    .url(remote.url("/v1/messages")).build())).build();
            var provider = new ClaudeProvider("controlled-key", "controlled-model", client);
            var options = LLMOptions.builder().maxTokens(100).build();
            var response = streaming ? provider.completeStreamReactive(INPUT, options)
                    .filter(e -> e.getType()==StreamEvent.EventType.LLM_COMPLETE).blockLast(Duration.ofSeconds(30)).getResponse()
                    : provider.complete(INPUT, options);
            assertEquals(1125, response.getUsage().getInputTokens(), "Cache reads/writes vanished from the budget input");
            assertEquals(streaming ? 7 : 5, response.getUsage().getOutputTokens());
        }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void gatewayAuthenticationReachesHttpButNeverApplicationLogs(boolean streaming) throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(OpenRouterProvider.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try (var remote = new MockWebServer()) {
            remote.start();
            remote.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(
                    "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"},\"finish_reason\":\"stop\"}]}"));
            var provider = new OpenRouterProvider("sentinel-private-api-key", "controlled-model", remote.url("/v1").toString());
            if (streaming) provider.completeStreamReactive(INPUT, LLMOptions.builder().build()).blockLast(Duration.ofSeconds(30));
            else provider.complete(INPUT, LLMOptions.builder().build());
            var request = remote.takeRequest(5, TimeUnit.SECONDS);
            assertEquals("Bearer sentinel-private-api-key", request.getHeader("Authorization"));
            assertFalse(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("sentinel-private-api-key")), "Provider logs disclosed the credential");
        } finally { logger.detachAppender(appender); appender.stop(); }
    }
}
