# Guide: Adding a New Tool

## Option A: Annotation-based (recommended)
1. Create a class in `agent-tools` under appropriate sub-package
2. Annotate methods with `@ToolFunction` and `@ToolParam`
3. Register via `AnnotatedToolScanner`

## Option B: Interface-based
1. Implement `Tool` interface in `agent-tools`
2. Register via `ToolRegistry.register(tool)`

## Option C: MCP Tool
1. Configure in `application.yml` under `app.mcp.servers`
2. Auto-discovered and registered as `McpToolWrapper`

## Rules
- NEVER implement new tools as `Plugin`/`PluginFunction` (legacy).
- Tool implementations go in `agent-tools`, NOT `agent-kernel`.
