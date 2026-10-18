package com.lightweightai.kernel.prompt;

/** Source of versioned prompt content; snapshots must not follow later publication. */
@FunctionalInterface
public interface PromptSource {
    PromptSnapshot snapshot();
}
