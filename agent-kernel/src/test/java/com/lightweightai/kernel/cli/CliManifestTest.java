package com.lightweightai.kernel.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CliManifestTest {

    @TempDir
    Path tempDir;

    @Test
    void loadValidManifest() throws IOException {
        Path manifest = tempDir.resolve("cli-manifest.json");
        Files.writeString(manifest, """
                {
                    "name": "weather-cli",
                    "version": "1.0.0",
                    "description": "Fetch weather data",
                    "entry_point": "bin/weather",
                    "output_format": "json"
                }
                """);

        CliManifest m = CliManifest.load(manifest);
        assertEquals("weather-cli", m.getName());
        assertEquals("1.0.0", m.getVersion());
        assertEquals("Fetch weather data", m.getDescription());
        assertEquals("bin/weather", m.getEntryPoint());
        assertEquals("json", m.getOutputFormat());
    }

    @Test
    void isJsonOutputTrueForJson() throws IOException {
        Path manifest = tempDir.resolve("manifest.json");
        Files.writeString(manifest, """
                {"name": "t", "entry_point": "bin/t", "output_format": "json"}
                """);

        assertTrue(CliManifest.load(manifest).isJsonOutput());
    }

    @Test
    void isJsonOutputFalseForText() throws IOException {
        Path manifest = tempDir.resolve("manifest.json");
        Files.writeString(manifest, """
                {"name": "t", "entry_point": "bin/t", "output_format": "text"}
                """);

        assertFalse(CliManifest.load(manifest).isJsonOutput());
    }

    @Test
    void isJsonOutputCaseInsensitive() throws IOException {
        Path manifest = tempDir.resolve("manifest.json");
        Files.writeString(manifest, """
                {"name": "t", "entry_point": "bin/t", "output_format": "JSON"}
                """);

        assertTrue(CliManifest.load(manifest).isJsonOutput());
    }

    @Test
    void defaultOutputFormatIsText() {
        CliManifest m = new CliManifest();
        assertEquals("text", m.getOutputFormat());
        assertFalse(m.isJsonOutput());
    }

    @Test
    void unknownFieldsIgnored() throws IOException {
        Path manifest = tempDir.resolve("manifest.json");
        Files.writeString(manifest, """
                {
                    "name": "t",
                    "entry_point": "bin/t",
                    "unknown_field": "value",
                    "another_extra": 42
                }
                """);

        CliManifest m = CliManifest.load(manifest);
        assertEquals("t", m.getName());
    }

    @Test
    void loadNonExistentFileThrowsIOException() {
        Path noFile = tempDir.resolve("does-not-exist.json");
        assertThrows(IOException.class, () -> CliManifest.load(noFile));
    }

    @Test
    void loadMalformedJsonThrowsIOException() throws IOException {
        Path manifest = tempDir.resolve("bad.json");
        Files.writeString(manifest, "not valid json {{{");
        assertThrows(Exception.class, () -> CliManifest.load(manifest));
    }

    @Test
    void toStringContainsFields() {
        CliManifest m = new CliManifest();
        m.setName("my-tool");
        m.setVersion("2.0");
        m.setDescription("A tool");

        String s = m.toString();
        assertTrue(s.contains("my-tool"));
        assertTrue(s.contains("2.0"));
        assertTrue(s.contains("A tool"));
    }

    @Test
    void settersWork() {
        CliManifest m = new CliManifest();
        m.setName("n");
        m.setVersion("v");
        m.setDescription("d");
        m.setEntryPoint("ep");
        m.setOutputFormat("json");

        assertEquals("n", m.getName());
        assertEquals("v", m.getVersion());
        assertEquals("d", m.getDescription());
        assertEquals("ep", m.getEntryPoint());
        assertEquals("json", m.getOutputFormat());
    }
}
