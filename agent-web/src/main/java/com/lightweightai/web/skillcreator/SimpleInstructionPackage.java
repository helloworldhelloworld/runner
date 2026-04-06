package com.lightweightai.web.skillcreator;

import com.lightweightai.kernel.instruction.InstructionPackage;

import java.nio.file.Path;
import java.util.Map;

/**
 * 简单的 InstructionPackage 实现，用于 Pipeline 发布的 Skill 注册到 InstructionRegistry。
 */
public class SimpleInstructionPackage implements InstructionPackage {

    private final String name;
    private final String description;
    private final String instructions;

    public SimpleInstructionPackage(String name, String description, String instructions) {
        this.name = name;
        this.description = description;
        this.instructions = instructions != null ? instructions : "";
    }

    @Override public String getName() { return name; }
    @Override public String getDescription() { return description; }
    @Override public String getInstructions() { return instructions; }
    @Override public Map<String, String> getMetadata() { return Map.of("source", "skill-pipeline"); }
    @Override public Map<String, byte[]> getResources() { return Map.of(); }
    @Override public Path getSourcePath() { return null; }
    @Override public boolean hasResource(String resourceName) { return false; }
    @Override public byte[] getResource(String resourceName) { return null; }
    @Override public String getResourceAsString(String resourceName) { return null; }
    @Override public String toPromptContext() { return "## " + name + "\n" + instructions; }
    @Override public String getFormat() { return "skill-pipeline"; }
}
