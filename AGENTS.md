# AGENTS.md — wArchi MCP

Guidance for AI coding agents working in this repository.

## Project Overview

**warchi-mcp** is a remote MCP server that lets AI clients (Cursor, etc.) read and write wArchi architectural models via arepos-server REST API.

- **No local database** — identity, API keys, scopes, Cerbos authz live in arepos-server
- **Auth:** inbound API key → `POST /api/v1/auth/api-keys/exchange` → short-lived JWT (`mcp_access`) → REST calls
- **Transport:** Spring AI Streamable HTTP (`/mcp`)

## Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin 2.2.x |
| Framework | Spring Boot 3.5.x |
| MCP | Spring AI 1.1.x (`spring-ai-starter-mcp-server-webmvc`) |
| JDK | 24–25 |
| Build | Gradle (Kotlin DSL) |
| HTTP client | Spring `RestClient` |

## Project Structure

```text
src/main/kotlin/ru/kavader/warchimcp/
├── WarchiMcpApplication.kt
├── auth/
│   ├── ApiKeyAuthFilter.kt     # captures Authorization / X-Api-Key
│   └── ApiKeyContext.kt        # ThreadLocal for current request key
├── client/
│   ├── AreposAuthClient.kt     # exchange + JWT cache
│   ├── AreposApiClient.kt      # authenticated REST helper
│   ├── AreposClientException.kt
│   └── AreposRestClientConfig.kt
├── config/
│   └── WarchiMcpProperties.kt
└── tools/
    ├── ModelReadTools.kt
    ├── ModelWriteTools.kt
    └── ToolResult.kt           # ok/error JSON envelope for tools
```

## Commands

```bash
./gradlew build
./gradlew test
./gradlew bootRun
./gradlew bootJar
```

Env: `AREPOS_BASE_URL` (required in real use), `PORT` (default `8090`).

## Conventions

- Tool methods return **JSON strings** via `ToolResult.ok` / `ToolResult.error` (agents parse structured results)
- Never log API keys or JWTs
- Prefer curated tools over exposing raw REST 1:1
- Write tools must surface arepos conflict/lock codes without silent overwrite
- Keep `docs/auth*.md` and `docs/tools*.md` in sync with behavior changes
- Dual license: AGPL-3.0-or-later + commercial (same family as arepos/wArchi)

## Related Repos

- `arepos-server` — API keys, exchange, JWT scopes/allowlist, Cerbos
- `warchi` — Profile UI for creating/revoking keys

Feature work that spans components uses a shared branch name, e.g. `feat/warchi-mcp-api-keys`.

## Out of Scope (v1)

- Notation CRUD, shares, OEF import, admin tools
- Stdio transport
- Own persistence for keys or sessions
- `layout_diagram`, graph neighbors, relation-rules enforce on create, `delete_diagram`

Convenience write path for landscapes: `search_notation` → notation-aware `create_node` / `ensure_link` → `create_diagram` → `add_diagram_instances` (arepos-backed). `batch_save_model` remains the escape hatch.
