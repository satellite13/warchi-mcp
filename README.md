# warchi-mcp

Remote [Model Context Protocol](https://modelcontextprotocol.io) server for [wArchi](https://warchi.ru). It exposes architectural models, diagrams, nodes, and links as MCP tools over Streamable HTTP, authenticated with personal API keys created in the wArchi profile.

Русская версия: [`README.ru.md`](README.ru.md)

The project is built with Kotlin + Spring Boot and Spring AI MCP. It has **no database of its own**: API keys and authorization live in [arepos-server](https://gitverse.ru/ngroznykh/arepos-server); this service only exchanges keys for short-lived JWTs and proxies curated tool calls to the REST API.

## Features

- Remote MCP server (Streamable HTTP) for Cursor and other MCP clients
- Personal API key auth (`warchi_ak_…`) with scopes `models:read` / `models:write`
- Optional per-key model allowlist (enforced by arepos)
- Short-lived JWT exchange (`tokenType=mcp_access`) — keys are not sent on every REST call
- Curated read/write tools for models, diagrams, nodes, links, and notation summaries
- Structured tool errors for `LOCKED_BY_OTHER`, `BATCH_SAVE_CONFLICT`, `missing_scope`, `model_not_allowed`
- Health endpoint via Spring Actuator
- Dockerfile for container deployment

## Tech Stack

- Kotlin 2.2.x
- Spring Boot 3.5.x
- Spring AI 1.1.x (`spring-ai-starter-mcp-server-webmvc`)
- JDK 24/25
- Gradle (Kotlin DSL)

## Project Structure

```text
src/main/kotlin/ru/kavader/warchimcp/
  auth/       # API key request context + filter
  client/     # arepos RestClient, exchange, JWT cache
  config/     # configuration properties
  tools/      # MCP tool definitions (read/write)

src/main/resources/
  application.yaml

docs/         # auth & tools documentation
```

## Requirements

- JDK 24 or 25
- Running arepos-server with API keys support (migration `050-api-keys` or later)
- Docker (optional, for image build)

## Local Development

### 1) Configure environment

Defaults are in `src/main/resources/application.yaml`. See also `.env.example`.

| Variable | Default | Description |
|----------|---------|-------------|
| `AREPOS_BASE_URL` | `http://localhost:8080` | Base URL of arepos-server |
| `PORT` | `8090` | HTTP listen port |
| `AREPOS_CONNECT_TIMEOUT` | `5s` | Connect timeout to arepos |
| `AREPOS_READ_TIMEOUT` | `60s` | Read timeout to arepos |

### 2) Build and run

```bash
export AREPOS_BASE_URL=http://localhost:8080
./gradlew build
./gradlew bootRun
```

### 3) Run tests

```bash
./gradlew test
```

## Build Commands

```bash
./gradlew build       # full build + tests
./gradlew test        # unit tests
./gradlew bootJar     # fat JAR
./gradlew bootRun     # local run
docker build -t warchi-mcp:0.1.0 .
```

## MCP Endpoint

- Protocol: Streamable HTTP (`spring.ai.mcp.server.protocol=STREAMABLE`)
- Default path: `/mcp`
- Health: `GET /actuator/health`

### Client configuration (Cursor example)

```json
{
  "mcpServers": {
    "warchi": {
      "url": "https://mcp.warchi.ru/mcp",
      "headers": {
        "Authorization": "Bearer warchi_ak_YOUR_KEY"
      }
    }
  }
}
```

Create the key in wArchi → **Profile** → **API keys**. The plaintext secret is shown **once** at creation.

Also accepted: `X-Api-Key: warchi_ak_…`.

## Auth Flow

1. User creates an API key in wArchi (scopes + optional model UUIDs).
2. MCP client sends the key to `warchi-mcp`.
3. Server calls `POST /api/v1/auth/api-keys/exchange` on arepos.
4. Tools call arepos `/api/v1/*` with the short-lived JWT.
5. Revoking the key in Profile immediately blocks further exchanges.

Details: [`docs/auth.md`](docs/auth.md).

## Tools (v1)

**Read** (`models:read`): `list_models`, `get_model`, `list_diagrams`, `get_diagram`, `list_nodes`, `get_node`, `list_links`, `get_link`, `list_notations`, `get_notation_summary`

**Write** (`models:write`): `create_node`, `update_node`, `delete_node`, `create_link`, `update_link`, `delete_link`, `update_diagram`, `batch_save_model`

Tool catalogue: [`docs/tools.md`](docs/tools.md).

## Kubernetes (local / cluster)

```bash
# lint
./scripts/helmCheck.sh

# build image + helm upgrade into namespace arch
SKIP_CONFIRM=true ./scripts/deploy.sh

# point at arepos in-cluster (default)
# AREPOS_BASE_URL=http://arepos-server:8080 SKIP_CONFIRM=true ./scripts/deploy.sh
```

Chart: `charts/warchi-mcp/`. After deploy:

```bash
kubectl -n arch port-forward svc/warchi-mcp 8090:8090
# MCP: http://127.0.0.1:8090/mcp
```

## Deployment

Typical production layout:

1. Deploy arepos-server with API keys enabled.
2. Deploy wArchi with Profile → API keys UI.
3. Run `warchi-mcp` with `AREPOS_BASE_URL` pointing at arepos.
4. Terminate TLS at reverse proxy / ingress (`https://mcp.example.com` → container `:8090`).
5. Forward `Authorization` / `X-Api-Key` headers to the app.
6. Never log API keys or JWTs.

### Docker

```bash
docker build -t warchi-mcp:0.1.0 .
docker run --rm -p 8090:8090 \
  -e AREPOS_BASE_URL=https://api.example.com \
  warchi-mcp:0.1.0
```

### Smoke checklist

- [ ] `POST /api/v1/auth/api-keys/exchange` works against arepos with a test key
- [ ] MCP client can call `list_models`
- [ ] Revoked key → exchange returns 401
- [ ] Write without `models:write` → `missing_scope`
- [ ] Allowlisted key cannot access other models → `model_not_allowed`

## Open Source Guide

- [`CONTRIBUTING.md`](CONTRIBUTING.md) / [`CONTRIBUTING.ru.md`](CONTRIBUTING.ru.md)
- [`SECURITY.md`](SECURITY.md) / [`SECURITY.ru.md`](SECURITY.ru.md)
- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) / [`CODE_OF_CONDUCT.ru.md`](CODE_OF_CONDUCT.ru.md)

## Contributing

Please read [`CONTRIBUTING.md`](CONTRIBUTING.md) before opening a pull request.

## Security

Please read [`SECURITY.md`](SECURITY.md) for reporting vulnerabilities.

## License

This project uses dual licensing:

- `AGPL-3.0-or-later` for open-source usage
- Commercial license for proprietary/closed-source commercial usage

See:

- [`LICENSE`](LICENSE)
- [`LICENSE_COMMERCIAL.md`](LICENSE_COMMERCIAL.md)
