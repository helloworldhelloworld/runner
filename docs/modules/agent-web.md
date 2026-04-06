# agent-web

The assembly and transport module. Spring Boot REST/SSE + Vert.x WebSocket server.

## Responsibility
Wires all modules together, provides HTTP/WebSocket endpoints, Spring configuration.

## Key Packages

| Package | Purpose |
|---|---|
| `.config` | Spring Boot auto-configuration |
| `.controller` | REST controllers (gateway/chat, assessment) |
| `.gateway` | WebSocket gateway adapters |
| `.websocket` | Vert.x WebSocket server |
| `.agent` | Agent configuration and wiring |
| `.observer` | AgentObserver implementations |
| `.postprocess` | Web-specific stream post-processors |
| `.service` | Application services |
| `.skillcreator` | Skill creation tooling |

## Dependencies
Depends on ALL other modules — it is the top-level assembly.

## Rules
- Business logic does NOT go here. It belongs in agent-kernel or domain modules.
- This is the ONLY module that should depend on Spring Boot.
- WebSocket protocol handling stays here; agent logic stays in agent-kernel.

## Ports
- REST/SSE: 8080 (Spring Boot)
- WebSocket: 8081 (Vert.x)
