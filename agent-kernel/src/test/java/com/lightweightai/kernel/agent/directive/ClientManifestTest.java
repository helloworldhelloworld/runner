package com.lightweightai.kernel.agent.directive;

import com.lightweightai.kernel.agent.DeviceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ClientManifest - Client capability declaration")
class ClientManifestTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("Full constructor sets all fields")
        void shouldSetAllFields() {
            ClientCapability cap = new ClientCapability();
            cap.setNamespace("Camera");
            cap.setName("TakePhoto");

            ClientManifest manifest = new ClientManifest(
                "android", "2.1.0", List.of(cap));

            assertEquals("android", manifest.getClientType());
            assertEquals("2.1.0", manifest.getClientVersion());
            assertEquals(1, manifest.getCapabilities().size());
            assertEquals("Camera", manifest.getCapabilities().get(0).getNamespace());
        }

        @Test
        @DisplayName("Default constructor creates instance with null fields")
        void defaultConstructorShouldCreateInstance() {
            ClientManifest manifest = new ClientManifest();

            assertNull(manifest.getClientType());
            assertNull(manifest.getClientVersion());
            assertNull(manifest.getCapabilities());
        }

        @Test
        @DisplayName("Null capabilities defaults to empty list")
        void nullCapabilitiesShouldDefaultToEmpty() {
            ClientManifest manifest = new ClientManifest("android", "1.0.0", null);

            assertNotNull(manifest.getCapabilities());
            assertTrue(manifest.getCapabilities().isEmpty());
        }
    }

    @Nested
    @DisplayName("Setters")
    class Setters {

        @Test
        @DisplayName("Setters update fields correctly")
        void shouldUpdateFields() {
            ClientManifest manifest = new ClientManifest();
            manifest.setClientType("ios");
            manifest.setClientVersion("3.0.0");
            manifest.setCapabilities(Collections.emptyList());

            assertEquals("ios", manifest.getClientType());
            assertEquals("3.0.0", manifest.getClientVersion());
            assertNotNull(manifest.getCapabilities());
            assertTrue(manifest.getCapabilities().isEmpty());
        }
    }

    @Nested
    @DisplayName("DeviceContext integration")
    class DeviceContextIntegration {

        @Test
        @DisplayName("DeviceContext.fromManifest extracts type and version")
        void shouldCreateDeviceContextFromManifest() {
            ClientManifest manifest = new ClientManifest("car", "2.5.0", List.of());

            DeviceContext context = DeviceContext.fromManifest(manifest);

            assertEquals("car", context.getDeviceType());
            assertEquals("2.5.0", context.getVersion());
            assertFalse(context.isDefault());
        }

        @Test
        @DisplayName("DeviceContext.fromManifest with null returns DEFAULT")
        void shouldReturnDefaultForNullManifest() {
            DeviceContext context = DeviceContext.fromManifest(null);

            assertTrue(context.isDefault());
        }
    }

    @Nested
    @DisplayName("toString")
    class ToStringTest {

        @Test
        @DisplayName("toString includes client type and version")
        void shouldIncludeKeyInfo() {
            ClientManifest manifest = new ClientManifest("android", "2.0.0", List.of());

            String str = manifest.toString();
            assertTrue(str.contains("android"));
            assertTrue(str.contains("2.0.0"));
            assertTrue(str.contains("0")); // capabilities count
        }

        @Test
        @DisplayName("toString handles null capabilities gracefully")
        void shouldHandleNullCapabilities() {
            ClientManifest manifest = new ClientManifest();
            manifest.setClientType("ios");

            String str = manifest.toString();
            assertTrue(str.contains("ios"));
            assertTrue(str.contains("0"));
        }
    }
}
