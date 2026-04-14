package com.lightweightai.kernel.cli;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI 工具描述清单 — 对应 cli-manifest.json
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CliManifest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private String name;
    private String version;
    private String description;

    @JsonProperty("entry_point")
    private String entryPoint;

    @JsonProperty("output_format")
    private String outputFormat = "text";  // text | json | auto

    // Getters/Setters
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getEntryPoint() { return entryPoint; }
    public void setEntryPoint(String entryPoint) { this.entryPoint = entryPoint; }
    public String getOutputFormat() { return outputFormat; }
    public void setOutputFormat(String outputFormat) { this.outputFormat = outputFormat; }

    public boolean isJsonOutput() {
        return "json".equalsIgnoreCase(outputFormat);
    }

    /**
     * 从文件加载 manifest
     */
    public static CliManifest load(Path manifestPath) throws IOException {
        try (InputStream is = Files.newInputStream(manifestPath)) {
            return MAPPER.readValue(is, CliManifest.class);
        }
    }

    @Override
    public String toString() {
        return "CliManifest{name='" + name + "', version='" + version + "', description='" + description + "'}";
    }
}
