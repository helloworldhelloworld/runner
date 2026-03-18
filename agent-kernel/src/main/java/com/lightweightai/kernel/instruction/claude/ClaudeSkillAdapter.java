package com.lightweightai.kernel.instruction.claude;

import com.lightweightai.kernel.instruction.InstructionPackage;
import com.lightweightai.kernel.skill.Skill;
import java.nio.file.Path;
import java.util.Map;

/**
 * Adapter that wraps a Claude Skill as an InstructionPackage
 *
 * This allows the existing Skill implementation to work with the new
 * provider-agnostic InstructionPackage interface.
 *
 * Backward compatibility is maintained - existing Skill code works unchanged.
 */
public class ClaudeSkillAdapter implements InstructionPackage {

    private final Skill skill;

    public ClaudeSkillAdapter(Skill skill) {
        this.skill = skill;
    }

    @Override
    public String getName() {
        return skill.getName();
    }

    @Override
    public String getDescription() {
        return skill.getDescription();
    }

    @Override
    public String getInstructions() {
        return skill.getInstructions();
    }

    @Override
    public Map<String, String> getMetadata() {
        return skill.getMetadata();
    }

    @Override
    public Map<String, byte[]> getResources() {
        return skill.getResources();
    }

    @Override
    public Path getSourcePath() {
        return skill.getSkillPath();
    }

    @Override
    public boolean hasResource(String resourceName) {
        return skill.hasResource(resourceName);
    }

    @Override
    public byte[] getResource(String resourceName) {
        return skill.getResource(resourceName);
    }

    @Override
    public String getResourceAsString(String resourceName) {
        return skill.getResourceAsString(resourceName);
    }

    @Override
    public String toPromptContext() {
        return skill.toPromptContext();
    }

    @Override
    public String getFormat() {
        return "anthropic-skill";
    }

    /**
     * Get the underlying Skill object
     */
    public Skill getSkill() {
        return skill;
    }

    /**
     * Create adapter from Skill
     */
    public static ClaudeSkillAdapter from(Skill skill) {
        return new ClaudeSkillAdapter(skill);
    }
}
