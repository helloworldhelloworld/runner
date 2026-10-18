package com.lightweightai.web.websocket;

import com.lightweightai.kernel.agent.*;
import com.lightweightai.web.service.AuthSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Real Python media consumer against the actual authenticated Spring WebSocket runtime. */
class BudgetVoiceSeamAcceptanceTest {
    @Test void budgetStopReachesPythonTurnAndAbortsPlayback() throws Exception {
        String root = System.getProperty("voice.gateway.root");
        assumeTrue(root != null && Files.isDirectory(Path.of(root)), "voice.gateway.root required for cross-repository probe");
        String python = System.getProperty("voice.gateway.python", "python3");
        try (var context = (ServletWebServerApplicationContext) new SpringApplicationBuilder(AuthenticatedWebSocketAcceptanceTest.Server.class)
                .properties("server.port=0", "spring.main.banner-mode=off", "logging.level.root=WARN",
                        "spring.config.location=optional:classpath:/budget-seam-unused.properties").run()) {
            context.getBean(AgentRegistry.class).register(AgentProfile.builder().agentId("default").maxBudgetTokens(1).build());
            var token = context.getBean(AuthSessionService.class).createSessionToken("alice");
            String url = "ws://127.0.0.1:"+context.getWebServer().getPort()+"/ws/chat?token="+token;
            String script = """
                    import json, sys
                    from voice_gateway.bridge import TextBridge, BudgetStoppedError
                    from voice_gateway.barge_in import BargeInSignal
                    from voice_gateway.pipeline import play_interruptible
                    from voice_gateway.player import FakePlayer
                    from voice_gateway.tts import FakeTts
                    from voice_gateway.ws_client import PersistentRunnerConnection
                    from voice_gateway.protocol import validate_frame
                    cancel, player, tts = BargeInSignal(), FakePlayer(), FakeTts()
                    with PersistentRunnerConnection(sys.argv[1]) as connection:
                        class ValidatingTransport:
                            def stream_sync(self, payload, **kwargs):
                                frames = connection.stream_sync(payload, **kwargs)
                                try:
                                    for raw in frames:
                                        frame = json.loads(raw)
                                        if frame.get('type') == 'budget_stopped':
                                            validate_frame(frame)
                                            assert frame['turnId'] == 'python-budget-turn'
                                        yield raw
                                finally:
                                    frames.close()
                        chunks = TextBridge(ValidatingTransport()).stream_transcript(
                            'budget-session', 'hello', turn_id='python-budget-turn', cancel=cancel)
                        try:
                            play_interruptible(chunks, tts, player, should_stop=lambda: False, cancel=cancel)
                        except BudgetStoppedError as stopped:
                            assert stopped.usage['maxBudgetTokens'] == 1
                            assert stopped.usage['attemptCount'] == 0
                            assert cancel.triggered and player.aborted and player.closed
                            assert not player.written and not tts.synthesized
                            print(json.dumps({'reason': str(stopped), 'usage': dict(stopped.usage), 'aborted': player.aborted}))
                        else:
                            raise AssertionError('budget stop became successful completion')
                    """;
            var builder = new ProcessBuilder(python, "-c", script, url).directory(Path.of(root).toFile()).redirectErrorStream(true);
            builder.environment().put("PYTHONPATH", Path.of(root, "src").toString());
            builder.environment().put("NO_PROXY", "127.0.0.1,localhost");
            builder.environment().put("no_proxy", "127.0.0.1,localhost");
            Path output = Files.createTempFile("runner-budget-python-", ".log");
            Process process = builder.redirectOutput(output.toFile()).start();
            try {
                assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Python budget consumer did not finish");
                String received = Files.readString(output, StandardCharsets.UTF_8);
                assertEquals(0, process.exitValue(), received);
                var result = AuthenticatedWebSocketAcceptanceTest.JSON.readTree(received.lines()
                        .filter(line -> line.startsWith("{")).reduce((a,b) -> b).orElseThrow());
                assertEquals("budget_exhausted", result.path("reason").asText());
                assertTrue(result.path("aborted").asBoolean());
                assertTrue(context.getBean(MinionVoiceTurnWebSocketTest.RemoteLlm.class).requests.isEmpty());
            } finally { if (process.isAlive()) process.destroyForcibly(); Files.deleteIfExists(output); }
        }
    }
}
