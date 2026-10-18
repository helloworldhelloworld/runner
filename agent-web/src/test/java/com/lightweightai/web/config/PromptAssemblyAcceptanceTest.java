package com.lightweightai.web.config;

import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.prompt.Skill;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Spring injects the shared management source; assertions inspect actual OpenRouter HTTP requests. */
class PromptAssemblyAcceptanceTest {
    @ParameterizedTest @ValueSource(booleans = {false, true})
    void nativeAssemblyConsumesTheCurrentSharedPromptSource(boolean reactive) throws Exception {
        var prompts = PromptEngine.builder().build();
        prompts.registerSkill(skill("instructions-v1"));
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(prompts)) {
            f.start("A", "new work", reactive).get(5, TimeUnit.SECONDS);
            assertTrue(f.forInput("new work").get(0).path("messages").toString().contains("instructions-v1"),
                    "registered prompt content must reach the actual model HTTP request");
            prompts.registerSkill(skill("instructions-v2"));
            f.start("B", "resumed work", reactive).get(5, TimeUnit.SECONDS);
            var actual = f.forInput("resumed work").get(0).path("messages").toString();
            assertTrue(actual.contains("instructions-v2"));
            assertFalse(actual.contains("instructions-v1"));
        }
    }
    @org.junit.jupiter.api.Test
    void activeExecutionAndLaterChildShareContentWhileTheNewRootUsesPublication() throws Exception {
        var prompts = PromptEngine.builder().build();
        prompts.registerSkill(skill("instructions-v1"));
        var assemblies = new java.util.concurrent.ConcurrentHashMap<String, com.lightweightai.kernel.agent.ExecutionAssembly>();
        var observer = new com.lightweightai.kernel.agent.AgentObserver() {
            public void onExecutionAssembly(String session, com.lightweightai.kernel.agent.ExecutionAssembly assembly) {
                assemblies.put(session, assembly);
            }
        };
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(prompts, observer)) {
            var root = f.start("A", "root work", true);
            assertEquals(java.util.Map.of("marker", "root work"), f.awaitActuator());
            prompts.registerSkill(skill("instructions-v2"));
            f.start("B", "new work", true).get(5, TimeUnit.SECONDS);
            f.releaseActuator();
            root.get(5, TimeUnit.SECONDS);
            String parent = f.forInput("root work").stream().filter(r -> r.toString().contains("Run ID:"))
                    .findFirst().orElseThrow().toString();
            var matcher = java.util.regex.Pattern.compile("Run ID: ([a-zA-Z0-9-]+)").matcher(parent);
            assertTrue(matcher.find());
            var runtime = f.context.getBean(com.lightweightai.kernel.orchestrator.SubagentRuntime.class);
            assertTrue(runtime.waitForCompletion(matcher.group(1), 5, TimeUnit.SECONDS));
            for (var request : f.forInput("root work")) {
                assertTrue(request.path("messages").toString().contains("instructions-v1"));
                assertFalse(request.path("messages").toString().contains("instructions-v2"));
            }
            assertTrue(f.forInput("child work").get(0).path("messages").toString().contains("instructions-v1"));
            assertTrue(f.forInput("new work").get(0).path("messages").toString().contains("instructions-v2"));
            var a = assemblies.get("agent:default:main:A");
            var b = assemblies.get("agent:default:main:B");
            assertEquals(com.lightweightai.kernel.core.ConfigurationContent.digest(a.getConfiguration()), a.getDigest());
            assertNotEquals(a.getDigest(), b.getDigest());
            assertTrue(a.getConfiguration().get("systemPrompt").toString().contains("instructions-v1"));
            assertEquals("model-v1", ((java.util.Map<?, ?>) a.getConfiguration().get("model")).get("model"));
            assertThrows(UnsupportedOperationException.class, () -> a.getConfiguration().put("systemPrompt", "changed"));
        }
    }

    private static Skill skill(String content) {
        return Skill.builder().name("published").systemPrompt(content).triggers(List.of("work")).build();
    }
}
