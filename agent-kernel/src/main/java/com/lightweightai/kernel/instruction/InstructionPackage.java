package com.lightweightai.kernel.instruction;

import java.nio.file.Path;
import java.util.Map;

/**
 * Abstract Instruction Package Interface
 *
 * Represents a package of instructions, guidelines, and resources that
 * guide an LLM's behavior for specific tasks.
 *
 * This is a provider-agnostic abstraction. Different LLM providers may have
 * different formats and capabilities:
 * - Anthropic Claude: SKILL.md with YAML frontmatter
 * - OpenAI: Custom prompt templates
 * - LangChain: Prompt templates with variables
 * - Custom: Any instruction format
 *
 * Implementations should handle provider-specific details.
 */
public interface InstructionPackage {

    /**
     * Get the unique name of this instruction package
     */
    String getName();

    /**
     * Get a description of what this package does
     */
    String getDescription();

    /**
     * Get the full instructions/guidelines
     */
    String getInstructions();

    /**
     * Get metadata about this package
     */
    Map<String, String> getMetadata();

    /**
     * Get bundled resources (templates, examples, data files)
     */
    Map<String, byte[]> getResources();

    /**
     * Get the source path of this package (if loaded from filesystem)
     */
    Path getSourcePath();

    /**
     * Check if this package has a specific resource
     */
    boolean hasResource(String resourceName);

    /**
     * Get resource content as bytes
     */
    byte[] getResource(String resourceName);

    /**
     * Get resource content as string (for text files)
     */
    String getResourceAsString(String resourceName);

    /**
     * Convert this package to a prompt context string
     *
     * This is a generic representation. ProviderAdapter may format it
     * differently for specific LLMs.
     */
    String toPromptContext();

    /**
     * Get the format/type of this instruction package
     *
     * Examples: "anthropic-skill", "openai-prompt", "langchain-template"
     */
    String getFormat();

    /**
     * Get the version of this package (if specified)
     */
    default String getVersion() {
        return getMetadata().getOrDefault("version", "1.0.0");
    }

    /**
     * Get the author of this package (if specified)
     */
    default String getAuthor() {
        return getMetadata().getOrDefault("author", "Unknown");
    }
}
