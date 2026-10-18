package com.lightweightai.kernel.prompt;

import com.lightweightai.kernel.core.ConfigurationContent;

import java.util.*;

/** Content-only skill catalog. It does not grant tools or read user memory. */
public final class PromptSnapshot {
    public record Fragment(String name, String content, List<String> triggers, int priority,
                           Map<String, Object> metadata) {
        public Fragment {
            Objects.requireNonNull(name);
            content = content == null ? "" : content;
            triggers = List.copyOf(triggers);
            metadata = ConfigurationContent.map(metadata);
        }
        boolean matches(String input) {
            String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
            return triggers.isEmpty() || triggers.stream().anyMatch(t -> normalized.contains(t.toLowerCase(Locale.ROOT)));
        }
        Map<String, Object> descriptor() {
            return Map.of("name", name, "content", content, "triggers", triggers, "priority", priority, "metadata", metadata);
        }
    }
    private final List<Fragment> fragments;
    private final String digest;

    public PromptSnapshot(List<Fragment> fragments) {
        this.fragments = fragments.stream().sorted(Comparator.comparingInt(Fragment::priority).thenComparing(Fragment::name)).toList();
        this.digest = ConfigurationContent.digest(this.fragments.stream().map(Fragment::descriptor).toList());
    }
    public static PromptSnapshot empty() { return new PromptSnapshot(List.of()); }
    public String getDigest() { return digest; }
    public List<Fragment> getFragments() { return fragments; }
    public List<Fragment> select(String input) { return fragments.stream().filter(f -> f.matches(input)).toList(); }
    public String render(String input) {
        return select(input).stream().filter(f -> !f.content().isBlank())
                .map(f -> "## " + f.name() + "\n" + f.content()).reduce("", (a, b) -> a.isEmpty() ? b : a + "\n\n" + b);
    }
    public List<Map<String, Object>> describeSelection(String input) {
        return select(input).stream().map(Fragment::descriptor).toList();
    }
}
