package com.lightweightai.kernel.skill;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skill Registry - Manage and apply Claude Skills
 *
 * Central registry for all loaded skills. Provides:
 * - Skill registration and lookup
 * - Skill activation/deactivation
 * - Context injection for conversations
 *
 * @deprecated This is a Claude-specific implementation. For provider-agnostic
 *             instruction management, use {@link com.lightweightai.kernel.instruction.InstructionRegistry}
 *             with {@link com.lightweightai.kernel.instruction.claude.ClaudeSkillAdapter}.
 *
 * Note: This class is maintained for backward compatibility with existing code.
 * It works perfectly for Claude-only applications. For multi-provider support,
 * migrate to the new instruction package.
 *
 * Migration example:
 * <pre>{@code
 * // Old (Claude-specific)
 * SkillRegistry registry = new SkillRegistry();
 * registry.registerSkill(skill);
 *
 * // New (Provider-agnostic)
 * InstructionRegistry registry = new InstructionRegistry(ProviderAdapterFactory.claude());
 * registry.registerPackage(new ClaudeSkillAdapter(skill));
 * }</pre>
 */
public class SkillRegistry {

    private final Map<String, Skill> skills = new ConcurrentHashMap<>();
    private final Set<String> activeSkills = ConcurrentHashMap.newKeySet();

    /**
     * Register a skill
     */
    public void registerSkill(Skill skill) {
        if (skills.containsKey(skill.getName())) {
            throw new IllegalArgumentException("Skill already registered: " + skill.getName());
        }
        skills.put(skill.getName(), skill);
    }

    /**
     * Register multiple skills
     */
    public void registerSkills(Collection<Skill> skillList) {
        skillList.forEach(this::registerSkill);
    }

    /**
     * Load and register skills from directory
     */
    public void loadSkills(Path skillsDirectory) throws IOException {
        Map<String, Skill> loadedSkills = SkillLoader.loadSkills(skillsDirectory);
        loadedSkills.values().forEach(this::registerSkill);
    }

    /**
     * Get a skill by name
     */
    public Skill getSkill(String name) {
        return skills.get(name);
    }

    /**
     * Check if skill exists
     */
    public boolean hasSkill(String name) {
        return skills.containsKey(name);
    }

    /**
     * Get all registered skills
     */
    public Collection<Skill> getAllSkills() {
        return new ArrayList<>(skills.values());
    }

    /**
     * Get skill names
     */
    public Set<String> getSkillNames() {
        return new HashSet<>(skills.keySet());
    }

    /**
     * Activate a skill for use in conversations
     */
    public void activateSkill(String name) {
        if (!skills.containsKey(name)) {
            throw new IllegalArgumentException("Skill not found: " + name);
        }
        activeSkills.add(name);
    }

    /**
     * Activate multiple skills
     */
    public void activateSkills(String... names) {
        for (String name : names) {
            activateSkill(name);
        }
    }

    /**
     * Deactivate a skill
     */
    public void deactivateSkill(String name) {
        activeSkills.remove(name);
    }

    /**
     * Deactivate all skills
     */
    public void deactivateAllSkills() {
        activeSkills.clear();
    }

    /**
     * Check if skill is active
     */
    public boolean isActive(String name) {
        return activeSkills.contains(name);
    }

    /**
     * Get all active skills
     */
    public List<Skill> getActiveSkills() {
        return activeSkills.stream()
            .map(skills::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Get count of registered skills
     */
    public int getSkillCount() {
        return skills.size();
    }

    /**
     * Get count of active skills
     */
    public int getActiveSkillCount() {
        return activeSkills.size();
    }

    /**
     * Clear all skills
     */
    public void clear() {
        skills.clear();
        activeSkills.clear();
    }

    /**
     * Build system message with active skills
     *
     * Injects all active skills into a system message that Claude can use
     * to understand what tasks it can perform and how.
     */
    public String buildSkillsSystemMessage() {
        if (activeSkills.isEmpty()) {
            return "";
        }

        StringBuilder systemMessage = new StringBuilder();
        systemMessage.append("# Active Skills\n\n");
        systemMessage.append("You have access to the following skills:\n\n");

        List<Skill> active = getActiveSkills();
        for (Skill skill : active) {
            systemMessage.append("---\n\n");
            systemMessage.append(skill.toPromptContext());
            systemMessage.append("\n\n");
        }

        return systemMessage.toString();
    }

    /**
     * Create a system message for conversation
     *
     * Convenience method to create a ConversationMessage with skills context
     */
    public ConversationMessage createSkillsSystemMessage() {
        String content = buildSkillsSystemMessage();
        if (content.isEmpty()) {
            return null;
        }

        return ConversationMessage.builder()
            .role(ConversationMessage.MessageRole.SYSTEM)
            .textContent(content)
            .build();
    }

    /**
     * Inject skills into conversation messages
     *
     * Adds active skills as system message at the beginning
     */
    public List<ConversationMessage> injectSkills(List<ConversationMessage> messages) {
        if (activeSkills.isEmpty()) {
            return messages;
        }

        ConversationMessage skillsMessage = createSkillsSystemMessage();
        if (skillsMessage == null) {
            return messages;
        }

        List<ConversationMessage> enriched = new ArrayList<>();
        enriched.add(skillsMessage);
        enriched.addAll(messages);

        return enriched;
    }

    /**
     * Auto-activate skills based on user query
     *
     * Simple keyword matching to suggest relevant skills.
     * Returns list of suggested skill names.
     */
    public List<String> suggestSkills(String userQuery) {
        String queryLower = userQuery.toLowerCase();
        List<String> suggested = new ArrayList<>();

        for (Skill skill : skills.values()) {
            String nameLower = skill.getName().toLowerCase().replace("-", " ");
            String descLower = skill.getDescription().toLowerCase();

            // Check if query contains skill name keywords or description keywords
            String[] queryWords = queryLower.split("\\s+");
            String[] nameWords = nameLower.split("\\s+");
            String[] descWords = descLower.split("\\s+");

            boolean matches = false;

            // Check if any significant query word appears in skill name or description
            for (String queryWord : queryWords) {
                if (queryWord.length() < 3) continue; // Skip short words

                for (String nameWord : nameWords) {
                    if (nameWord.contains(queryWord) || queryWord.contains(nameWord)) {
                        matches = true;
                        break;
                    }
                }

                if (!matches) {
                    for (String descWord : descWords) {
                        if (descWord.contains(queryWord) || queryWord.contains(descWord)) {
                            matches = true;
                            break;
                        }
                    }
                }

                if (matches) break;
            }

            if (matches) {
                suggested.add(skill.getName());
            }
        }

        return suggested;
    }

    @Override
    public String toString() {
        return "SkillRegistry{" +
            "totalSkills=" + skills.size() +
            ", activeSkills=" + activeSkills.size() +
            '}';
    }
}
