package com.lightweightai.kernel.instruction;

import com.lightweightai.kernel.llm.ConversationMessage;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Universal Instruction Registry
 *
 * Provider-agnostic registry for managing instruction packages.
 * Works with any LLM provider through the ProviderAdapter interface.
 *
 * Features:
 * - Register instruction packages from any format
 * - Activate/deactivate packages
 * - Inject instructions using provider-specific adapters
 * - Suggest relevant packages based on queries
 */
public class InstructionRegistry {

    private final Map<String, InstructionPackage> packages = new ConcurrentHashMap<>();
    private final Set<String> activePackages = ConcurrentHashMap.newKeySet();
    private ProviderAdapter providerAdapter;

    /**
     * Create registry with a specific provider adapter
     */
    public InstructionRegistry(ProviderAdapter providerAdapter) {
        this.providerAdapter = providerAdapter;
    }

    /**
     * Create registry with default Claude adapter
     */
    public InstructionRegistry() {
        // Default to Claude for backward compatibility
        try {
            Class<?> claudeAdapterClass = Class.forName(
                "com.lightweightai.kernel.instruction.claude.ClaudeProviderAdapter"
            );
            this.providerAdapter = (ProviderAdapter) claudeAdapterClass
                .getMethod("getInstance")
                .invoke(null);
        } catch (Exception e) {
            throw new RuntimeException("Failed to load default Claude adapter", e);
        }
    }

    /**
     * Set the provider adapter
     */
    public void setProviderAdapter(ProviderAdapter adapter) {
        this.providerAdapter = adapter;
    }

    /**
     * Get current provider adapter
     */
    public ProviderAdapter getProviderAdapter() {
        return providerAdapter;
    }

    /**
     * Register an instruction package
     */
    public void registerPackage(InstructionPackage pkg) {
        if (packages.containsKey(pkg.getName())) {
            throw new IllegalArgumentException("Package already registered: " + pkg.getName());
        }
        packages.put(pkg.getName(), pkg);
    }

    /**
     * Register multiple packages
     */
    public void registerPackages(Collection<InstructionPackage> pkgs) {
        pkgs.forEach(this::registerPackage);
    }

    /**
     * Get a package by name
     */
    public InstructionPackage getPackage(String name) {
        return packages.get(name);
    }

    /**
     * Check if package exists
     */
    public boolean hasPackage(String name) {
        return packages.containsKey(name);
    }

    /**
     * Get all registered packages
     */
    public Collection<InstructionPackage> getAllPackages() {
        return new ArrayList<>(packages.values());
    }

    /**
     * Get package names
     */
    public Set<String> getPackageNames() {
        return new HashSet<>(packages.keySet());
    }

    /**
     * Activate a package
     */
    public void activatePackage(String name) {
        if (!packages.containsKey(name)) {
            throw new IllegalArgumentException("Package not found: " + name);
        }
        activePackages.add(name);
    }

    /**
     * Activate multiple packages
     */
    public void activatePackages(String... names) {
        for (String name : names) {
            activatePackage(name);
        }
    }

    /**
     * Deactivate a package
     */
    public void deactivatePackage(String name) {
        activePackages.remove(name);
    }

    /**
     * Deactivate all packages
     */
    public void deactivateAll() {
        activePackages.clear();
    }

    /**
     * Check if package is active
     */
    public boolean isActive(String name) {
        return activePackages.contains(name);
    }

    /**
     * Get all active packages
     */
    public List<InstructionPackage> getActivePackages() {
        return activePackages.stream()
            .map(packages::get)
            .filter(Objects::nonNull)
            .toList();
    }

    /**
     * Get count of registered packages
     */
    public int getPackageCount() {
        return packages.size();
    }

    /**
     * Get count of active packages
     */
    public int getActivePackageCount() {
        return activePackages.size();
    }

    /**
     * Clear all packages
     */
    public void clear() {
        packages.clear();
        activePackages.clear();
    }

    /**
     * Inject active instruction packages into conversation
     *
     * Uses the provider adapter to format instructions appropriately
     * for the target LLM provider.
     */
    public List<ConversationMessage> injectInstructions(List<ConversationMessage> messages) {
        List<InstructionPackage> active = getActivePackages();
        if (active.isEmpty()) {
            return messages;
        }

        return providerAdapter.injectInstructions(messages, active);
    }

    /**
     * Suggest relevant packages based on query
     *
     * Simple keyword matching algorithm
     */
    public List<String> suggestPackages(String userQuery) {
        String queryLower = userQuery.toLowerCase();
        List<String> suggested = new ArrayList<>();

        for (InstructionPackage pkg : packages.values()) {
            String nameLower = pkg.getName().toLowerCase().replace("-", " ");
            String descLower = pkg.getDescription().toLowerCase();

            // Keyword matching
            String[] queryWords = queryLower.split("\\s+");
            String[] nameWords = nameLower.split("\\s+");
            String[] descWords = descLower.split("\\s+");

            boolean matches = false;

            for (String queryWord : queryWords) {
                if (queryWord.length() < 3) continue;

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
                suggested.add(pkg.getName());
            }
        }

        return suggested;
    }

    /**
     * Get provider capabilities
     */
    public ProviderAdapter.ProviderCapabilities getCapabilities() {
        return providerAdapter.getCapabilities();
    }

    @Override
    public String toString() {
        return "InstructionRegistry{" +
            "provider=" + providerAdapter.getProviderName() +
            ", totalPackages=" + packages.size() +
            ", activePackages=" + activePackages.size() +
            '}';
    }
}
