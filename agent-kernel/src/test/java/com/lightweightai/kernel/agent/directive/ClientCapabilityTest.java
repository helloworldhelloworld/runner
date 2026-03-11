package com.lightweightai.kernel.agent.directive;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

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
}
