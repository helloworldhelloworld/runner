package com.lightweightai.kernel.orchestrator;

import com.fasterxml.jackson.databind.*;
import com.lightweightai.kernel.core.StreamEvent;
import com.lightweightai.kernel.llm.*;
import com.lightweightai.kernel.llm.openrouter.OpenAIWebSocketProvider;
import okhttp3.*;
import okhttp3.mockwebserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketUsageAcceptanceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<ConversationMessage> INPUT = List.of(ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.USER).textContent("hello").build());
    private static final String MESSAGE = "\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"done\"},\"finish_reason\":\"stop\"}]";

    @ParameterizedTest @ValueSource(strings={"{\"prompt_tokens\":2}", "{\"prompt_tokens\":2,\"completion_tokens\":-1}"})
    void incompleteUsageCannotBecomeKnownZero(String usage) throws Exception {
        try (var remote = new MockWebServer()) {
            var sent = enqueue(remote, List.of("{"+MESSAGE+",\"usage\":"+usage+"}")); remote.start();
            var provider = provider(remote);
            var actual = provider.completeAsync(INPUT, LLMOptions.builder().maxTokens(77).build()).get(30, TimeUnit.SECONDS);
            assertEquals(77, sent.get(5, TimeUnit.SECONDS).path("max_tokens").asInt());
            assertNull(actual.getUsage(), "Incomplete or invalid usage must not refund a conservative reservation");
        }
    }

    @Test void streamingUsageAndOutputLimitReachConsumersWithoutLoggingCredential() throws Exception {
        var logger = (Logger) LoggerFactory.getLogger(OpenAIWebSocketProvider.class);
        var appender = new ListAppender<ILoggingEvent>(); appender.start(); logger.addAppender(appender);
        try (var remote = new MockWebServer()) {
            var sent = enqueue(remote, List.of("{"+MESSAGE+",\"usage\":{\"prompt_tokens\":23,\"completion_tokens\":4}}"));
            remote.start();
            var events = provider(remote).completeStreamReactive(INPUT, LLMOptions.builder().maxTokens(77).build())
                    .collectList().block(Duration.ofSeconds(30));
            var actual = events.stream().filter(e -> e.getType()==StreamEvent.EventType.LLM_COMPLETE)
                    .findFirst().orElseThrow().getResponse();
            assertEquals(77, sent.get(5, TimeUnit.SECONDS).path("max_tokens").asInt());
            assertEquals("ws-sentinel-private-key", sent.get().path("api_key").asText());
            assertEquals("ws-sentinel-private-key", remote.takeRequest(5, TimeUnit.SECONDS).getHeader("api_key"));
            assertNotNull(actual.getUsage());
            assertEquals(23, actual.getUsage().getInputTokens());
            assertEquals(4, actual.getUsage().getOutputTokens());
            assertFalse(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .anyMatch(message -> message.contains("ws-sentinel-private-key")));
        } finally { logger.detachAppender(appender); appender.stop(); }
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void streamErrorReachesConsumerBeforeOrAfterPartialText(boolean partial) throws Exception {
        try (var remote = new MockWebServer()) {
            List<String> frames = new ArrayList<>();
            if (partial) frames.add("{\"choices\":[{\"delta\":{\"content\":\"partial\"}}]}");
            frames.add("{\"error\":{\"message\":\"peer rejected\"}}");
            enqueue(remote, frames); remote.start();
            var deltas = new CopyOnWriteArrayList<String>();
            var failure = assertThrows(RuntimeException.class, () -> provider(remote)
                    .completeStreamReactive(INPUT, LLMOptions.builder().build())
                    .doOnNext(e -> { if (e.getType()==StreamEvent.EventType.TEXT_DELTA) deltas.add(e.getTextDelta()); })
                    .blockLast(Duration.ofSeconds(30)));
            assertTrue(causes(failure).contains("peer rejected"));
            assertEquals(partial ? List.of("partial") : List.of(), deltas);
            assertEquals(1, remote.getRequestCount());
        }
    }

    private static OpenAIWebSocketProvider provider(MockWebServer remote) {
        return new OpenAIWebSocketProvider(remote.url("/chat").toString(), "controlled-model", "ws-sentinel-private-key");
    }
    private static CompletableFuture<JsonNode> enqueue(MockWebServer remote, List<String> frames) {
        var sent = new CompletableFuture<JsonNode>();
        remote.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
            @Override public void onMessage(WebSocket ws, String message) {
                try { sent.complete(JSON.readTree(message)); }
                catch (Exception e) { sent.completeExceptionally(e); }
                frames.forEach(ws::send); ws.close(1000, "complete");
            }
            @Override public void onClosing(WebSocket ws, int code, String reason) { ws.close(code, reason); }
        }));
        return sent;
    }
    private static String causes(Throwable error) {
        StringBuilder text = new StringBuilder();
        for (Throwable e=error;e!=null;e=e.getCause()) text.append(e.getMessage()).append('\n');
        return text.toString();
    }
}
