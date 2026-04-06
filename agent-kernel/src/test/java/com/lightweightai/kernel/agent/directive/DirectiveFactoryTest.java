package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Directive - 云→端指令信封")
class DirectiveFactoryTest {

    @Test
    @DisplayName("fromToolName 解析 namespace.name 格式")
    void shouldParseNamespaceDotName() {
        Directive d = Directive.fromToolName("id-1", "Camera.TakePhoto", Map.of("res", "1080p"), 30000);

        assertEquals("id-1", d.getDirectiveId());
        assertEquals("Camera", d.getNamespace());
        assertEquals("TakePhoto", d.getName());
        assertEquals(Map.of("res", "1080p"), d.getPayload());
        assertEquals(30000, d.getTimeoutMs());
    }

    @Test
    @DisplayName("fromToolName 无命名空间使用 Default")
    void shouldUseDefaultNamespaceForSimpleName() {
        Directive d = Directive.fromToolName("id-2", "takePhoto", Map.of(), 5000);

        assertEquals("Default", d.getNamespace());
        assertEquals("takePhoto", d.getName());
    }

    @Test
    @DisplayName("toToolName 组合 namespace.name")
    void shouldCombineToToolName() {
        Directive d = new Directive("id", "GPS", "GetLocation", Map.of(), 10000);
        assertEquals("GPS.GetLocation", d.toToolName());
    }

    @Test
    @DisplayName("构造器 null 检查")
    void shouldThrowOnNullFields() {
        assertThrows(NullPointerException.class,
            () -> new Directive(null, "ns", "name", Map.of(), 100));
        assertThrows(NullPointerException.class,
            () -> new Directive("id", null, "name", Map.of(), 100));
        assertThrows(NullPointerException.class,
            () -> new Directive("id", "ns", null, Map.of(), 100));
    }

    @Test
    @DisplayName("null payload 变为空 map")
    void shouldDefaultNullPayload() {
        Directive d = new Directive("id", "ns", "name", null, 100);
        assertNotNull(d.getPayload());
        assertTrue(d.getPayload().isEmpty());
    }

    @Test
    @DisplayName("setter 方法可用")
    void shouldSupportSetters() {
        Directive d = new Directive("id", "ns", "name", Map.of(), 100);
        d.setDirectiveId("new-id");
        d.setNamespace("NewNs");
        d.setName("newName");
        d.setPayload(Map.of("key", "val"));
        d.setTimeoutMs(5000);

        assertEquals("new-id", d.getDirectiveId());
        assertEquals("NewNs", d.getNamespace());
        assertEquals("newName", d.getName());
        assertEquals(5000, d.getTimeoutMs());
        assertEquals("val", d.getPayload().get("key"));
    }

    @Test
    @DisplayName("默认构造器（Jackson 反序列化）")
    void shouldSupportDefaultConstructor() {
        Directive d = new Directive();
        // 不应抛异常
        assertNull(d.getDirectiveId());
        assertNull(d.getNamespace());
    }

    @Test
    @DisplayName("toString 包含关键信息")
    void shouldHaveMeaningfulToString() {
        Directive d = new Directive("id-1", "Camera", "TakePhoto", Map.of(), 30000);
        String str = d.toString();

        assertTrue(str.contains("Camera"));
        assertTrue(str.contains("TakePhoto"));
        assertTrue(str.contains("id-1"));
    }
}
