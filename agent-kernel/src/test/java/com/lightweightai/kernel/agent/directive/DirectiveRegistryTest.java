package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DirectiveRegistry - ClientTool注册表")
class DirectiveRegistryTest {

    private DirectiveRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new DirectiveRegistry();
    }

    private DirectiveDescriptor makeDescriptor(String tool) {
        return new DirectiveDescriptor(tool, "down." + tool, "up." + tool, "TestNS", 30000, "1.0");
    }

    @Nested
    @DisplayName("register / get")
    class RegisterGet {

        @Test
        @DisplayName("注册后可查询")
        void shouldGetRegisteredDescriptor() {
            DirectiveDescriptor desc = makeDescriptor("Camera.TakePhoto");
            registry.register(desc);

            Optional<DirectiveDescriptor> found = registry.get("Camera.TakePhoto");
            assertTrue(found.isPresent());
            assertEquals("Camera.TakePhoto", found.get().getTool());
        }

        @Test
        @DisplayName("查询未注册的工具返回empty")
        void shouldReturnEmptyForUnregistered() {
            assertTrue(registry.get("nonexistent").isEmpty());
        }

        @Test
        @DisplayName("重复注册覆盖旧值")
        void shouldOverrideOnDuplicateRegister() {
            DirectiveDescriptor first = new DirectiveDescriptor("myTool", "down1", "up1", "NS", 1000, "1.0");
            DirectiveDescriptor second = new DirectiveDescriptor("myTool", "down2", "up2", "NS", 2000, "2.0");

            registry.register(first);
            registry.register(second);

            assertEquals(1, registry.size());
            assertEquals("2.0", registry.get("myTool").orElseThrow().getVersion());
        }
    }

    @Nested
    @DisplayName("has")
    class Has {

        @Test
        @DisplayName("已注册返回true")
        void shouldReturnTrueForRegistered() {
            registry.register(makeDescriptor("myTool"));
            assertTrue(registry.has("myTool"));
        }

        @Test
        @DisplayName("未注册返回false")
        void shouldReturnFalseForUnregistered() {
            assertFalse(registry.has("nonexistent"));
        }
    }

    @Nested
    @DisplayName("unregister")
    class Unregister {

        @Test
        @DisplayName("注销后不可查询")
        void shouldNotFindAfterUnregister() {
            registry.register(makeDescriptor("myTool"));
            assertTrue(registry.has("myTool"));

            registry.unregister("myTool");
            assertFalse(registry.has("myTool"));
            assertTrue(registry.get("myTool").isEmpty());
        }

        @Test
        @DisplayName("注销不存在的工具不报错")
        void shouldNotThrowForMissingTool() {
            assertDoesNotThrow(() -> registry.unregister("nonexistent"));
        }

        @Test
        @DisplayName("注销不影响其他工具")
        void shouldNotAffectOtherTools() {
            registry.register(makeDescriptor("tool1"));
            registry.register(makeDescriptor("tool2"));

            registry.unregister("tool1");

            assertFalse(registry.has("tool1"));
            assertTrue(registry.has("tool2"));
        }
    }

    @Nested
    @DisplayName("getAll")
    class GetAll {

        @Test
        @DisplayName("空注册表返回空列表")
        void shouldReturnEmptyListWhenEmpty() {
            assertTrue(registry.getAll().isEmpty());
        }

        @Test
        @DisplayName("返回所有注册的描述符")
        void shouldReturnAllDescriptors() {
            registry.register(makeDescriptor("tool1"));
            registry.register(makeDescriptor("tool2"));
            registry.register(makeDescriptor("tool3"));

            List<DirectiveDescriptor> all = registry.getAll();
            assertEquals(3, all.size());
        }

        @Test
        @DisplayName("返回的列表是副本，修改不影响注册表")
        void shouldReturnDefensiveCopy() {
            registry.register(makeDescriptor("tool1"));

            List<DirectiveDescriptor> all = registry.getAll();
            all.clear();

            assertEquals(1, registry.size());
        }
    }

    @Nested
    @DisplayName("size")
    class Size {

        @Test
        @DisplayName("空注册表size为0")
        void shouldBeZeroWhenEmpty() {
            assertEquals(0, registry.size());
        }

        @Test
        @DisplayName("注册后size增加")
        void shouldIncrementOnRegister() {
            registry.register(makeDescriptor("t1"));
            assertEquals(1, registry.size());
            registry.register(makeDescriptor("t2"));
            assertEquals(2, registry.size());
        }

        @Test
        @DisplayName("注销后size减少")
        void shouldDecrementOnUnregister() {
            registry.register(makeDescriptor("t1"));
            registry.unregister("t1");
            assertEquals(0, registry.size());
        }
    }

    @Nested
    @DisplayName("clear")
    class Clear {

        @Test
        @DisplayName("清空后size为0")
        void shouldClearAll() {
            registry.register(makeDescriptor("t1"));
            registry.register(makeDescriptor("t2"));
            assertEquals(2, registry.size());

            registry.clear();

            assertEquals(0, registry.size());
            assertTrue(registry.getAll().isEmpty());
        }

        @Test
        @DisplayName("清空空注册表不报错")
        void shouldNotThrowOnClearEmpty() {
            assertDoesNotThrow(() -> registry.clear());
        }
    }

    @Nested
    @DisplayName("并发安全")
    class ConcurrentAccess {

        @Test
        @DisplayName("并发注册和查询不丢失数据")
        void shouldHandleConcurrentAccess() throws InterruptedException {
            int numThreads = 10;
            int opsPerThread = 100;
            ExecutorService executor = Executors.newFixedThreadPool(numThreads);
            CountDownLatch latch = new CountDownLatch(numThreads);
            AtomicInteger errors = new AtomicInteger(0);

            for (int t = 0; t < numThreads; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < opsPerThread; i++) {
                            String toolName = "tool-" + threadId + "-" + i;
                            registry.register(makeDescriptor(toolName));
                            assertTrue(registry.has(toolName));
                            assertTrue(registry.get(toolName).isPresent());
                        }
                    } catch (Exception e) {
                        errors.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            executor.shutdown();
            assertEquals(0, errors.get());
            assertEquals(numThreads * opsPerThread, registry.size());
        }
    }
}
