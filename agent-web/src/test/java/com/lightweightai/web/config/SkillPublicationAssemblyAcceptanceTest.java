package com.lightweightai.web.config;

import com.lightweightai.kernel.core.DefaultTestExecutor;
import com.lightweightai.kernel.prompt.PromptEngine;
import com.lightweightai.kernel.agent.ToolRegistry;
import com.lightweightai.web.skillcreator.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/** Real SQLite, test execution, publication and Spring/Provider HTTP, with only the network peer controlled. */
class SkillPublicationAssemblyAcceptanceTest {
    @TempDir Path directory;

    @Test void editingTheDraftAfterTestingCannotPublishTheOldReport() throws Exception {
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(PromptEngine.builder().build())) {
            var data = new Data(directory.resolve("skills.db"), f);
            SkillDraft draft = data.createAndTest("published", "tested-content");
            assertTrue(f.forInput("new work").get(0).path("messages").toString().contains("tested-content"));
            draft.setSystemPrompt("untested-content");
            data.skills.save(draft);
            assertThrows(IllegalStateException.class, () -> data.publisher.stage(draft.getId(), 1.0));
            assertTrue(data.versions.findBySkillId(draft.getId()).isEmpty());
        }
    }

    @Test void invalidActivationPreservesThePreviousActiveVersion() throws Exception {
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(PromptEngine.builder().build())) {
            var data = new Data(directory.resolve("skills.db"), f);
            var draft = data.createAndTest("published", "tested-content");
            var active = data.publisher.stage(draft.getId(), 1.0);
            data.publisher.activate(active.getId());
            var invalid = new SkillVersion();
            invalid.setSkillId(draft.getId());
            invalid.setVersionTag("2.0.0");
            invalid.setTestRunId(active.getTestRunId());
            invalid.setSnapshotJson("{invalid-json");
            invalid.setStatus("staged");
            data.versions.save(invalid);
            assertThrows(RuntimeException.class, () -> data.publisher.activate(invalid.getId()));
            assertEquals(active.getId(), data.versions.findActiveBySkillId(draft.getId()).orElseThrow().getId());
            assertEquals("staged", data.versions.findById(invalid.getId()).orElseThrow().getStatus());
        }
    }

    @Test void rollbackCannotActivateAnotherSkillsVersion() throws Exception {
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(PromptEngine.builder().build())) {
            var data = new Data(directory.resolve("skills.db"), f);
            var a = data.createAndTest("skill-a", "content-a");
            var b = data.createAndTest("skill-b", "content-b");
            var other = data.publisher.stage(b.getId(), 1.0);
            assertThrows(IllegalArgumentException.class, () -> data.publisher.rollback(a.getId(), other.getId()));
            assertTrue(data.versions.findActiveBySkillId(b.getId()).isEmpty());
        }
    }

    @Test void restartRestoresThePublishedContentInTheActualModelRequest() throws Exception {
        Path database = directory.resolve("restart.db");
        SkillVersion active;
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(PromptEngine.builder().build())) {
            var data = new Data(database, f);
            var draft = data.createAndTest("published", "durable-content");
            active = data.publisher.stage(draft.getId(), 1.0);
            data.publisher.activate(active.getId());
        }
        var engine = PromptEngine.builder().build();
        String path = database.toString();
        var restored = new SkillPublishService(new SkillRepository(path), new SkillVersionRepository(path),
                new TestRunRepository(path), engine, new ToolRegistry(), null, new SkillTestCaseRepository(path));
        assertFalse(engine.hasSkill("published"), "a fresh process has no compatibility mirror");
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(engine, null, restored)) {
            f.start("restored", "new work", true).get(5, TimeUnit.SECONDS);
            assertTrue(f.forInput("new work").get(0).path("messages").toString().contains("durable-content"));
            assertEquals(active.getId(), restored.snapshot().select("new work").get(0).metadata().get("publishedVersionId"));
        }
    }

    @Test void changedCriteriaRejectActivationAndKeepThePreviousVersion() throws Exception {
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(PromptEngine.builder().build())) {
            var data = new Data(directory.resolve("criteria.db"), f);
            var draft = data.createAndTest("published", "tested-content");
            var active = data.publisher.stage(draft.getId(), 1.0);
            data.publisher.activate(active.getId());
            var next = data.publisher.stage(draft.getId(), 1.0);
            var changed = data.cases.findBySkillId(draft.getId()).get(0);
            changed.setInput("different evaluation");
            data.cases.save(changed);
            assertThrows(IllegalStateException.class, () -> data.publisher.activate(next.getId()));
            assertThrows(IllegalStateException.class, () -> data.publisher.stage(draft.getId(), 1.0));
            assertEquals(active.getId(), data.versions.findActiveBySkillId(draft.getId()).orElseThrow().getId());
            assertTrue(data.publisher.snapshot().render("work").contains("tested-content"));
        }
    }

    @Test void changingPersistedResultsInvalidatesTheReport() throws Exception {
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(PromptEngine.builder().build())) {
            var data = new Data(directory.resolve("report.db"), f);
            var draft = data.createAndTest("published", "tested-content");
            var staged = data.publisher.stage(draft.getId(), 1.0);
            var result = data.runs.findCaseResultsByRunId(staged.getTestRunId()).get(0);
            result.setLlmOutput("altered evidence");
            data.runs.saveCaseResult(result);
            assertThrows(IllegalStateException.class, () -> data.publisher.activate(staged.getId()));
            assertTrue(data.versions.findActiveBySkillId(draft.getId()).isEmpty());
        }
    }

    @Test void twoPublishersWithTheSameExpectedVersionCannotBothCommit() throws Exception {
        Path database = directory.resolve("concurrent.db");
        try (var f = new ModelBindingAssemblyAcceptanceTest.Fixture(PromptEngine.builder().build())) {
            var first = new Data(database, f);
            var draft = first.createAndTest("published", "tested-content");
            var active = first.publisher.stage(draft.getId(), 1.0);
            first.publisher.activate(active.getId());
            var a = first.publisher.stage(draft.getId(), 1.0);
            var b = first.publisher.stage(draft.getId(), 1.0);
            var second = new Data(database, f);
            var start = new CompletableFuture<Void>();
            var one = start.thenApplyAsync(ignored -> conditional(first.publisher, a, active));
            var two = start.thenApplyAsync(ignored -> conditional(second.publisher, b, active));
            start.complete(null);
            boolean wonA = one.get(5, TimeUnit.SECONDS);
            boolean wonB = two.get(5, TimeUnit.SECONDS);
            assertNotEquals(wonA, wonB);
            String winner = wonA ? a.getId() : b.getId();
            assertEquals(winner, first.versions.findActiveBySkillId(draft.getId()).orElseThrow().getId());
            assertEquals(winner, second.publisher.snapshot().select("work").get(0).metadata().get("publishedVersionId"));
            assertEquals(1, first.versions.findActiveVersions().size());
        }
    }

    private static boolean conditional(SkillPublishService publisher, SkillVersion version, SkillVersion expected) {
        try { publisher.activate(version.getId(), expected.getId()); return true; }
        catch (IllegalStateException conflict) {
            assertTrue(conflict.getMessage().contains("changed"), conflict.toString());
            return false;
        }
    }

    static class Data {
        final SkillRepository skills;
        final SkillTestCaseRepository cases;
        final TestRunRepository runs;
        final SkillVersionRepository versions;
        final SkillTestExecutionService tester;
        final SkillPublishService publisher;
        Data(Path database, ModelBindingAssemblyAcceptanceTest.Fixture fixture) {
            String path = database.toString();
            skills = new SkillRepository(path);
            cases = new SkillTestCaseRepository(path);
            runs = new TestRunRepository(path);
            versions = new SkillVersionRepository(path);
            tester = new SkillTestExecutionService(new DefaultTestExecutor(fixture.context.getBean(DynamicLLMProvider.class)),
                    skills, cases, runs);
            publisher = new SkillPublishService(skills, versions, runs, fixture.context.getBean(PromptEngine.class),
                    fixture.context.getBean(ToolRegistry.class), null, cases);
        }
        SkillDraft createAndTest(String name, String content) throws Exception {
            var draft = new SkillDraft(name);
            draft.setName(name);
            draft.setDescription("Controlled publication");
            draft.setSystemPrompt(content);
            draft.setTriggers(List.of("work"));
            skills.save(draft);
            var testCase = new SkillTestCase();
            testCase.setId(name + "-case");
            testCase.setSkillId(name);
            testCase.setInput("new work");
            testCase.setDescription("Actual model request");
            cases.save(testCase);
            tester.executeAll(name).collectList().toFuture().get(5, TimeUnit.SECONDS);
            assertEquals("completed", runs.findRunsBySkillId(name).get(0).getStatus());
            return draft;
        }
    }
}
