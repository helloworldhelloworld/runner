# Guide: Adding a New Skill

## Steps

1. Create a `Skill` object with:
   - Name
   - Trigger words (for auto-detection from user input)
   - System prompt content
2. Register with `PromptEngine`
3. Skills auto-activate when trigger words match user input

## How Skills Work
`PromptEngine` assembles the final system prompt:
```
System Prompt = Base Prompt + Durable Memory + Active Skills + Relevant Memory Snippets
```

## Skill Files
External skills can be loaded from `.skill` files. See `example-skills/` for format.
