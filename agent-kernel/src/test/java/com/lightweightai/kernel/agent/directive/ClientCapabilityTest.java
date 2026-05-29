package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.DeviceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientCapability - Client-side capability declaration")
class ClientCapabilityTest {

    @Test
    void toToolName() {
        ClientCapability cap = new ClientCapability("Camera", "TakePhoto",
            "Take a photo", Map.of(), 30000);
        assertEquals("Camera.TakePhoto", cap.toToolName());
    }

    @Test
    void defaultTimeout() {
        ClientCapability cap = new ClientCapability("GPS", "GetLocation",
            "Get location", null, 0);
        assertEquals(60000, cap.getDefaultTimeoutMs());
    }

    @Test
    void customTimeout() {
        ClientCapability cap = new ClientCapability("NFC", "ReadTag",
            "Read NFC", null, 5000);
        assertEquals(5000, cap.getDefaultTimeoutMs());
    }

    @Test
    @DisplayName("Negative timeout defaults to 60000")
    void negativeTimeoutDefaultsTo60000() {
        ClientCapability cap = new ClientCapability(
            "GPS", "GetLocation", "Get GPS location", null, -1);
        assertEquals(60000, cap.getDefaultTimeoutMs());
    }

    @Test
    @DisplayName("Default constructor creates instance with null fields")
    void defaultConstructorCreatesNullFields() {
        ClientCapability cap = new ClientCapability();
        assertNull(cap.getNamespace());
        assertNull(cap.getName());
        assertNull(cap.getDescription());
        assertNull(cap.getInputSchema());
    }

    @Test
    @DisplayName("Full constructor sets all fields correctly")
    void fullConstructorSetsAllFields() {
        Map<String, Object> schema = Map.of("type", "object",
            "properties", Map.of("resolution", Map.of("type", "string")));

        ClientCapability cap = new ClientCapability(
            "Camera", "TakePhoto", "Take a photo", schema, 30000);

        assertEquals("Camera", cap.getNamespace());
        assertEquals("TakePhoto", cap.getName());
        assertEquals("Take a photo", cap.getDescription());
        assertEquals(schema, cap.getInputSchema());
        assertEquals(30000, cap.getDefaultTimeoutMs());
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("All setters update fields correctly")
        void shouldUpdateAllFields() {
            ClientCapability cap = new ClientCapability();
            cap.setNamespace("Audio");
            cap.setName("PlaySound");
            cap.setDescription("Play a sound");
            cap.setInputSchema(Map.of("type", "object"));
            cap.setDefaultTimeoutMs(10000);

            assertEquals("Audio", cap.getNamespace());
            assertEquals("PlaySound", cap.getName());
            assertEquals("Play a sound", cap.getDescription());
            assertNotNull(cap.getInputSchema());
            assertEquals(10000, cap.getDefaultTimeoutMs());
        }
    }

    @Test
    @DisplayName("toString includes namespace and name")
    void toStringShouldIncludeKeyInfo() {
        ClientCapability cap = new ClientCapability(
            "Camera", "TakePhoto", "Take a photo", null, 5000);

        String str = cap.toString();
        assertTrue(str.contains("Camera"));
        assertTrue(str.contains("TakePhoto"));
        assertTrue(str.contains("Take a photo"));
    }
}
