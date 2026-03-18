package com.lightweightai.kernel.instruction;

import com.lightweightai.kernel.instruction.claude.ClaudeSkillAdapter;
import com.lightweightai.kernel.llm.ConversationMessage;
import com.lightweightai.kernel.skill.Skill;
import java.util.List;

/**
 * Examples demonstrating the Instruction Abstraction Layer
 *
 * This shows how to use the provider-agnostic abstraction while
 * maintaining backward compatibility with existing Claude Skills.
 */
public class InstructionAbstractionExample {

    /**
     * Example 1: Using the new abstraction layer with Claude
     */
    public static void example1_NewAbstractionWithClaude() {
        System.out.println("=== Example 1: New Abstraction with Claude ===\n");

        // 1. Create provider adapter
        ProviderAdapter adapter = ProviderAdapterFactory.claude();
        System.out.println("Created adapter for: " + adapter.getProviderName());

        // 2. Create instruction registry
        InstructionRegistry registry = new InstructionRegistry(adapter);

        // 3. Create and register instruction packages
        InstructionPackage pkg = createSamplePackage();
        registry.registerPackage(pkg);
        System.out.println("Registered package: " + pkg.getName());

        // 4. Activate package
        registry.activatePackage(pkg.getName());
        System.out.println("Activated package: " + pkg.getName());

        // 5. Inject into conversation
        List<ConversationMessage> messages = List.of(
            ConversationMessage.builder()
                .role(ConversationMessage.MessageRole.USER)
                .textContent("Create a document")
                .build()
        );

        List<ConversationMessage> enriched = registry.injectInstructions(messages);
        System.out.println("Injected " + registry.getActivePackageCount() +
                         " packages into conversation");
        System.out.println("Total messages: " + enriched.size());
        System.out.println();
    }

    /**
     * Example 2: Backward compatibility - existing Skills work unchanged
     */
    public static void example2_BackwardCompatibility() {
        System.out.println("=== Example 2: Backward Compatibility ===\n");

        // Existing Skill
        Skill skill = Skill.builder()
            .name("legacy-skill")
            .description("Existing Claude Skill")
            .instructions("Follow these instructions...")
            .build();

        // Wrap as InstructionPackage using adapter
        InstructionPackage pkg = new ClaudeSkillAdapter(skill);

        System.out.println("Wrapped existing Skill as InstructionPackage");
        System.out.println("Name: " + pkg.getName());
        System.out.println("Format: " + pkg.getFormat());
        System.out.println("Works seamlessly with new abstraction!");
        System.out.println();
    }

    /**
     * Example 3: Switching providers (future capability)
     */
    public static void example3_SwitchingProviders() {
        System.out.println("=== Example 3: Provider Switching (Demo) ===\n");

        InstructionPackage pkg = createSamplePackage();

        // Use with Claude
        InstructionRegistry claudeRegistry = new InstructionRegistry(
            ProviderAdapterFactory.claude()
        );
        claudeRegistry.registerPackage(pkg);

        System.out.println("Registered with Claude adapter");
        System.out.println("Claude capabilities:");
        System.out.println("  - System messages: " +
            claudeRegistry.getCapabilities().supportsSystemMessages());
        System.out.println("  - Max length: " +
            claudeRegistry.getCapabilities().getMaxInstructionLength());

        // Future: Switch to OpenAI (when implemented)
        // InstructionRegistry openaiRegistry = new InstructionRegistry(
        //     ProviderAdapterFactory.create("openai")
        // );
        // openaiRegistry.registerPackage(pkg);

        System.out.println("\nFuture: Same package can work with OpenAI, Gemini, etc.");
        System.out.println();
    }

    /**
     * Example 4: Custom provider adapter
     */
    public static void example4_CustomAdapter() {
        System.out.println("=== Example 4: Custom Provider Adapter ===\n");

        // Create a minimal custom adapter
        ProviderAdapter customAdapter = new ProviderAdapter() {
            @Override
            public String getProviderName() {
                return "my-custom-llm";
            }

            @Override
            public boolean supports(String providerName) {
                return "my-custom-llm".equalsIgnoreCase(providerName);
            }

            @Override
            public ConversationMessage formatAsSystemMessage(InstructionPackage pkg) {
                return ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.SYSTEM)
                    .textContent("Custom format: " + pkg.getInstructions())
                    .build();
            }

            @Override
            public ConversationMessage formatAsUserPrefix(
                InstructionPackage pkg,
                String userMessage
            ) {
                return ConversationMessage.builder()
                    .role(ConversationMessage.MessageRole.USER)
                    .textContent(pkg.getInstructions() + "\n\n" + userMessage)
                    .build();
            }

            @Override
            public List<ConversationMessage> injectInstructions(
                List<ConversationMessage> messages,
                List<InstructionPackage> packages
            ) {
                if (packages.isEmpty()) {
                    return messages;
                }

                ConversationMessage systemMsg = formatAsSystemMessage(packages.get(0));
                List<ConversationMessage> result = new java.util.ArrayList<>();
                result.add(systemMsg);
                result.addAll(messages);
                return result;
            }

            @Override
            public ProviderCapabilities getCapabilities() {
                return ProviderCapabilities.minimal();
            }
        };

        // Register custom adapter
        ProviderAdapterFactory.registerInstance("my-custom-llm", customAdapter);

        // Use custom adapter
        InstructionRegistry registry = new InstructionRegistry(customAdapter);
        System.out.println("Created registry with custom adapter: " +
                         customAdapter.getProviderName());

        InstructionPackage pkg = createSamplePackage();
        registry.registerPackage(pkg);
        registry.activatePackage(pkg.getName());

        System.out.println("Custom adapter registered and working!");
        System.out.println();
    }

    /**
     * Example 5: Capability-aware usage
     */
    public static void example5_CapabilityAware() {
        System.out.println("=== Example 5: Capability-Aware Usage ===\n");

        ProviderAdapter adapter = ProviderAdapterFactory.claude();
        InstructionRegistry registry = new InstructionRegistry(adapter);

        ProviderAdapter.ProviderCapabilities caps = registry.getCapabilities();

        System.out.println("Provider: " + adapter.getProviderName());
        System.out.println("Capabilities:");
        System.out.println("  ✓ System messages: " + caps.supportsSystemMessages());
        System.out.println("  ✓ Instructions: " + caps.supportsInstructions());
        System.out.println("  ✓ Resources: " + caps.supportsResources());
        System.out.println("  ✓ Max length: " + caps.getMaxInstructionLength());
        System.out.println("  ✓ Multiple instructions: " +
                         caps.supportsMultipleInstructions());

        // Adapt behavior based on capabilities
        if (caps.supportsResources()) {
            System.out.println("\n✓ Can include templates and examples");
        }

        if (caps.getMaxInstructionLength() > 100000) {
            System.out.println("✓ Can use detailed, comprehensive instructions");
        } else {
            System.out.println("⚠ Should use concise instructions");
        }
        System.out.println();
    }

    /**
     * Example 6: Migration path
     */
    public static void example6_MigrationPath() {
        System.out.println("=== Example 6: Migration Path ===\n");

        // OLD WAY (still works)
        System.out.println("Old way (SkillRegistry):");
        com.lightweightai.kernel.skill.SkillRegistry oldRegistry =
            new com.lightweightai.kernel.skill.SkillRegistry();

        Skill skill = Skill.builder()
            .name("old-skill")
            .description("Using old API")
            .instructions("Old instructions")
            .build();

        oldRegistry.registerSkill(skill);
        oldRegistry.activateSkill("old-skill");
        System.out.println("  ✓ Registered and activated with SkillRegistry");

        // NEW WAY (recommended)
        System.out.println("\nNew way (InstructionRegistry):");
        InstructionRegistry newRegistry = new InstructionRegistry(
            ProviderAdapterFactory.claude()
        );

        // Same skill, adapted to new API
        InstructionPackage pkg = new ClaudeSkillAdapter(skill);
        newRegistry.registerPackage(pkg);
        newRegistry.activatePackage("old-skill");
        System.out.println("  ✓ Same skill working with InstructionRegistry");

        System.out.println("\nResult: Both APIs work with same Skill!");
        System.out.println("Migration: Gradual, no breaking changes");
        System.out.println();
    }

    // Helper methods

    private static InstructionPackage createSamplePackage() {
        return new InstructionPackage() {
            @Override
            public String getName() {
                return "sample-package";
            }

            @Override
            public String getDescription() {
                return "A sample instruction package for demonstration";
            }

            @Override
            public String getInstructions() {
                return "Follow these sample instructions:\n" +
                       "1. Be professional\n" +
                       "2. Be concise\n" +
                       "3. Be helpful";
            }

            @Override
            public java.util.Map<String, String> getMetadata() {
                return java.util.Map.of(
                    "version", "1.0.0",
                    "author", "Example Team"
                );
            }

            @Override
            public java.util.Map<String, byte[]> getResources() {
                return java.util.Map.of();
            }

            @Override
            public java.nio.file.Path getSourcePath() {
                return null;
            }

            @Override
            public boolean hasResource(String resourceName) {
                return false;
            }

            @Override
            public byte[] getResource(String resourceName) {
                return null;
            }

            @Override
            public String getResourceAsString(String resourceName) {
                return null;
            }

            @Override
            public String toPromptContext() {
                return "# " + getName() + "\n\n" +
                       getDescription() + "\n\n" +
                       "## Instructions\n" +
                       getInstructions();
            }

            @Override
            public String getFormat() {
                return "example-format";
            }
        };
    }

    /**
     * Run all examples
     */
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  Instruction Abstraction Layer - Examples         ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
        System.out.println();

        example1_NewAbstractionWithClaude();
        example2_BackwardCompatibility();
        example3_SwitchingProviders();
        example4_CustomAdapter();
        example5_CapabilityAware();
        example6_MigrationPath();

        System.out.println("╔════════════════════════════════════════════════════╗");
        System.out.println("║  All examples completed successfully!             ║");
        System.out.println("╚════════════════════════════════════════════════════╝");
    }
}
