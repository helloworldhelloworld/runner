package com.lightweightai.kernel.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Skill Loader - Load Claude Skills from filesystem
 *
 * Loads skills following Anthropic's Skills specification:
 * - Reads SKILL.md with YAML frontmatter
 * - Loads all resources in the skill folder
 * - Parses metadata and instructions
 *
 * Skill folder structure:
 * <pre>
 * my-skill/
 *   ├── SKILL.md           # Required: Frontmatter + Instructions
 *   ├── example.txt        # Optional: Resource files
 *   ├── template.docx      # Optional: Templates
 *   └── data/              # Optional: Data directory
 *       └── config.json
 * </pre>
 */
public class SkillLoader {

    private static final String SKILL_MANIFEST = "SKILL.md";
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile(
        "^---\\s*$(.*?)^---\\s*$(.*)$",
        Pattern.MULTILINE | Pattern.DOTALL
    );

    /**
     * Load a single skill from a directory
     *
     * @param skillPath Path to skill directory containing SKILL.md
     * @return Loaded Skill
     */
    public static Skill loadSkill(Path skillPath) throws IOException {
        if (!Files.isDirectory(skillPath)) {
            throw new IllegalArgumentException("Skill path must be a directory: " + skillPath);
        }

        Path manifestPath = skillPath.resolve(SKILL_MANIFEST);
        if (!Files.exists(manifestPath)) {
            throw new IOException("Skill manifest not found: " + manifestPath);
        }

        // Read SKILL.md
        String manifestContent = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);

        // Parse frontmatter and instructions
        Matcher matcher = FRONTMATTER_PATTERN.matcher(manifestContent);
        if (!matcher.find()) {
            throw new IOException("Invalid SKILL.md format: missing YAML frontmatter");
        }

        String frontmatterYaml = matcher.group(1).trim();
        String instructions = matcher.group(2).trim();

        // Parse YAML frontmatter (simple key-value parser)
        Map<String, String> metadata = parseSimpleYaml(frontmatterYaml);

        // Extract required fields
        String name = metadata.get("name");
        String description = metadata.get("description");

        if (name == null || name.isEmpty()) {
            throw new IOException("Skill 'name' is required in frontmatter");
        }
        if (description == null || description.isEmpty()) {
            throw new IOException("Skill 'description' is required in frontmatter");
        }

        // Load all resources (excluding SKILL.md)
        Map<String, byte[]> resources = loadResources(skillPath);

        return Skill.builder()
            .name(name)
            .description(description)
            .instructions(instructions)
            .metadata(metadata)
            .resources(resources)
            .skillPath(skillPath)
            .build();
    }

    /**
     * Load multiple skills from a directory
     *
     * Each subdirectory should contain a SKILL.md file
     *
     * @param skillsDirectory Directory containing skill folders
     * @return Map of skill name to Skill object
     */
    public static Map<String, Skill> loadSkills(Path skillsDirectory) throws IOException {
        if (!Files.isDirectory(skillsDirectory)) {
            throw new IllegalArgumentException("Skills directory does not exist: " + skillsDirectory);
        }

        Map<String, Skill> skills = new HashMap<>();

        try (Stream<Path> stream = Files.list(skillsDirectory)) {
            List<Path> skillDirs = stream
                .filter(Files::isDirectory)
                .collect(Collectors.toList());

            for (Path skillDir : skillDirs) {
                try {
                    Skill skill = loadSkill(skillDir);
                    skills.put(skill.getName(), skill);
                    System.out.println("Loaded skill: " + skill.getName());
                } catch (IOException e) {
                    System.err.println("Failed to load skill from " + skillDir + ": " + e.getMessage());
                }
            }
        }

        return skills;
    }

    /**
     * Load all resources from skill directory
     */
    private static Map<String, byte[]> loadResources(Path skillPath) throws IOException {
        Map<String, byte[]> resources = new HashMap<>();

        try (Stream<Path> stream = Files.walk(skillPath)) {
            stream.filter(Files::isRegularFile)
                .filter(p -> !p.getFileName().toString().equals(SKILL_MANIFEST))
                .forEach(resourcePath -> {
                    try {
                        // Use relative path as resource name
                        String resourceName = skillPath.relativize(resourcePath).toString();
                        byte[] content = Files.readAllBytes(resourcePath);
                        resources.put(resourceName, content);
                    } catch (IOException e) {
                        System.err.println("Failed to load resource " + resourcePath + ": " + e.getMessage());
                    }
                });
        }

        return resources;
    }

    /**
     * Simple YAML parser for frontmatter
     *
     * Only supports key-value pairs, not complex YAML structures
     * Format: "key: value" per line
     */
    private static Map<String, String> parseSimpleYaml(String yaml) {
        Map<String, String> result = new HashMap<>();

        String[] lines = yaml.split("\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }

            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();

                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                } else if (value.startsWith("'") && value.endsWith("'")) {
                    value = value.substring(1, value.length() - 1);
                }

                result.put(key, value);
            }
        }

        return result;
    }

    /**
     * Create a sample skill for testing
     */
    public static void createSampleSkill(Path outputPath, String skillName) throws IOException {
        Path skillDir = outputPath.resolve(skillName);
        Files.createDirectories(skillDir);

        String sampleSkillMd = String.format(
            "---\n" +
            "name: %s\n" +
            "description: A sample skill for demonstration\n" +
            "version: 1.0.0\n" +
            "author: Example Author\n" +
            "---\n" +
            "\n" +
            "# %s Skill\n" +
            "\n" +
            "This is a sample skill that demonstrates the Claude Skills format.\n" +
            "\n" +
            "## Instructions\n" +
            "\n" +
            "When this skill is active:\n" +
            "1. Follow these guidelines carefully\n" +
            "2. Use the provided examples as reference\n" +
            "3. Leverage available resources\n" +
            "\n" +
            "## Examples\n" +
            "\n" +
            "Example 1: How to do something\n" +
            "Example 2: Another use case\n" +
            "\n" +
            "## Guidelines\n" +
            "\n" +
            "- Always check the resources before proceeding\n" +
            "- Follow the documented patterns\n" +
            "- Maintain consistency with examples\n",
            skillName, skillName);

        Files.write(skillDir.resolve("SKILL.md"), sampleSkillMd.getBytes(StandardCharsets.UTF_8));

        // Add a sample resource
        String sampleResource = "This is a sample resource file for the skill.";
        Files.write(skillDir.resolve("example.txt"), sampleResource.getBytes(StandardCharsets.UTF_8));

        System.out.println("Created sample skill at: " + skillDir);
    }
}
