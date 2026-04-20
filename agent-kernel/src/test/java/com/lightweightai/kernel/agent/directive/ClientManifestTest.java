package com.lightweightai.kernel.agent.directive;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ClientManifest JSON deserialization — the protocol message
 * sent by client devices to register their capabilities.
 *
 * If deserialization fails silently, the cloud won't register device tools
 * and the LLM will never be able to invoke client-side actions.
 */
@DisplayName("ClientManifest - 端侧能力清单 JSON 协议")
class ClientManifestTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("从 JSON 反序列化完整 manifest")
    void deserializeFullManifest() throws Exception {
        String json = """
                {
                  "client_type": "android",
                  "client_version": "2.1.0",
                  "capabilities": [
                    {
                      "namespace": "Camera",
                      "name": "TakePhoto",
                      "description": "Take a photo",
                      "input_schema": {"type": "object"},
                      "default_timeout_ms": 30000
                    },
                    {
                      "namespace": "GPS",
                      "name": "GetLocation",
                      "description": "Get device location",
                      "default_timeout_ms": 10000
                    }
                  ]
                }
                """;

        ClientManifest manifest = mapper.readValue(json, ClientManifest.class);

        assertEquals("android", manifest.getClientType());
        assertEquals("2.1.0", manifest.getClientVersion());
        assertNotNull(manifest.getCapabilities());
        assertEquals(2, manifest.getCapabilities().size());

        ClientCapability camera = manifest.getCapabilities().get(0);
        assertEquals("Camera", camera.getNamespace());
        assertEquals("TakePhoto", camera.getName());
        assertEquals("Take a photo", camera.getDescription());
        assertEquals(30000, camera.getDefaultTimeoutMs());
        assertNotNull(camera.getInputSchema());

        ClientCapability gps = manifest.getCapabilities().get(1);
        assertEquals("GPS", gps.getNamespace());
        assertEquals("GetLocation", gps.getName());
        assertEquals(10000, gps.getDefaultTimeoutMs());
    }

    @Test
    @DisplayName("序列化 → 反序列化 roundtrip 保持完整")
    void serializationRoundtrip() throws Exception {
        ClientManifest original = new ClientManifest("ios", "3.0.0", List.of(
                new ClientCapability("NFC", "ReadTag", "Read NFC tag",
                        Map.of("type", "object", "properties", Map.of()), 5000)
        ));

        String json = mapper.writeValueAsString(original);
        ClientManifest deserialized = mapper.readValue(json, ClientManifest.class);

        assertEquals(original.getClientType(), deserialized.getClientType());
        assertEquals(original.getClientVersion(), deserialized.getClientVersion());
        assertEquals(1, deserialized.getCapabilities().size());
        assertEquals("NFC.ReadTag", deserialized.getCapabilities().get(0).toToolName());
    }

    @Test
    @DisplayName("空 capabilities 列表不为 null")
    void emptyCapabilitiesList() {
        ClientManifest manifest = new ClientManifest("web", "1.0", null);

        assertNotNull(manifest.getCapabilities());
        assertTrue(manifest.getCapabilities().isEmpty());
    }

    @Test
    @DisplayName("缺少 capabilities 字段时反序列化不报错")
    void missingCapabilitiesFieldDeserializes() throws Exception {
        String json = """
                {
                  "client_type": "desktop",
                  "client_version": "1.0"
                }
                """;

        ClientManifest manifest = mapper.readValue(json, ClientManifest.class);

        assertEquals("desktop", manifest.getClientType());
        assertNull(manifest.getCapabilities());
    }

    @Test
    @DisplayName("toString 包含类型和版本信息")
    void toStringIncludesTypeAndVersion() {
        ClientManifest manifest = new ClientManifest("android", "2.0", List.of(
                new ClientCapability("A", "B", "desc", null, 1000)
        ));

        String str = manifest.toString();
        assertTrue(str.contains("android"));
        assertTrue(str.contains("2.0"));
        assertTrue(str.contains("1")); // capabilities count
    }

    @Test
    @DisplayName("ClientCapability toToolName 返回 namespace.name")
    void capabilityToolNameFormat() {
        ClientCapability cap = new ClientCapability("Camera", "Capture", "desc", null, 5000);
        assertEquals("Camera.Capture", cap.toToolName());
    }

    @Test
    @DisplayName("ClientCapability defaultTimeoutMs <= 0 使用默认 60000")
    void capabilityDefaultTimeout() {
        ClientCapability cap = new ClientCapability("NS", "Tool", "desc", null, 0);
        assertEquals(60_000, cap.getDefaultTimeoutMs());

        ClientCapability cap2 = new ClientCapability("NS", "Tool", "desc", null, -1);
        assertEquals(60_000, cap2.getDefaultTimeoutMs());
    }
}
