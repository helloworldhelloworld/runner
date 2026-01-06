package com.lightweightai.kernel.skill;

import java.nio.file.Path;
import java.util.*;

/**
 * Claude Skill - Instruction-based task package
 *
 * Based on Anthropic's Skills specification: https://github.com/anthropics/skills
 *
 * Skills are folders containing:
 * - SKILL.md: Frontmatter (YAML) + Instructions (Markdown)
 * - Resources: Scripts, templates, examples, data files
 *
 * Unlike tool calling (function-based), Skills are instruction-based:
 * Claude reads the instructions and follows them contextually.
 */
public class Skill {

    private final String name;
    private final String description;
    private final String instructions;
    private final Map<String, String> metadata;
    private final Map<String, byte[]> resources;
    private final Path skillPath;

    private Skill(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.instructions = builder.instructions;
        this.metadata = builder.metadata;
        this.resources = builder.resources;
        this.skillPath = builder.skillPath;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getInstructions() {
        return instructions;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public Map<String, byte[]> getResources() {
        return resources;
    }

    public Path getSkillPath() {
        return skillPath;
    }

    /**
     * Check if skill has a specific resource
     */
    public boolean hasResource(String resourceName) {
        return resources.containsKey(resourceName);
    }

    /**
     * Get resource content
     */
    public byte[] getResource(String resourceName) {
        return resources.get(resourceName);
    }

    /**
     * Get resource as string (for text files)
     */
    public String getResourceAsString(String resourceName) {
        byte[] content = resources.get(resourceName);
        return content != null ? new String(content) : null;
    }

    /**
     * Convert skill to prompt context for Claude
     *
     * This creates a context block that Claude can use to understand
     * how to perform the task described in the skill.
     */
    public String toPromptContext() {
        StringBuilder context = new StringBuilder();

        context.append("# Skill: ").append(name).append("\n\n");

        if (description != null && !description.isEmpty()) {
            context.append("**Description**: ").append(description).append("\n\n");
        }

        // Add metadata
        if (!metadata.isEmpty()) {
            context.append("## Metadata\n");
            metadata.forEach((key, value) -> {
                if (!key.equals("name") && !key.equals("description")) {
                    context.append("- **").append(key).append("**: ").append(value).append("\n");
                }
            });
            context.append("\n");
        }

        // Add instructions
        context.append(instructions);

        // Add resource references
        if (!resources.isEmpty()) {
            context.append("\n\n## Available Resources\n");
            resources.keySet().forEach(resourceName -> {
                context.append("- `").append(resourceName).append("`\n");
            });
        }

        return context.toString();
    }

    /**
     * Convert to system message format
     */
    public String toSystemMessage() {
        return String.format(
            "You have access to the '%s' skill. %s\n\n%s",
            name,
            description,
            instructions
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private String description;
        private String instructions;
        private Map<String, String> metadata = new HashMap<>();
        private Map<String, byte[]> resources = new HashMap<>();
        private Path skillPath;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder instructions(String instructions) {
            this.instructions = instructions;
            return this;
        }

        public Builder metadata(Map<String, String> metadata) {
            this.metadata = new HashMap<>(metadata);
            return this;
        }

        public Builder addMetadata(String key, String value) {
            this.metadata.put(key, value);
            return this;
        }

        public Builder resources(Map<String, byte[]> resources) {
            this.resources = new HashMap<>(resources);
            return this;
        }

        public Builder addResource(String name, byte[] content) {
            this.resources.put(name, content);
            return this;
        }

        public Builder addResource(String name, String content) {
            this.resources.put(name, content.getBytes());
            return this;
        }

        public Builder skillPath(Path path) {
            this.skillPath = path;
            return this;
        }

        public Skill build() {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Skill name is required");
            }
            if (description == null || description.trim().isEmpty()) {
                throw new IllegalArgumentException("Skill description is required");
            }
            if (instructions == null || instructions.trim().isEmpty()) {
                throw new IllegalArgumentException("Skill instructions are required");
            }

            return new Skill(this);
        }
    }

    @Override
    public String toString() {
        return "Skill{" +
            "name='" + name + '\'' +
            ", description='" + description + '\'' +
            ", resourceCount=" + resources.size() +
            '}';
    }
}
