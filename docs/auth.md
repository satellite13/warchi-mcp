# Authentication

Russian: [`auth.ru.md`](auth.ru.md)

## Overview

`warchi-mcp` does not store users or API keys. All credentials are owned by **arepos-server**. This service only:

1. Accepts an API key from the MCP client
2. Exchanges it for a short-lived JWT
3. Calls arepos REST with that JWT

```text
MCP client --(API key)--> warchi-mcp --(exchange)--> arepos
                              |                        |
                              +--------(JWT REST)------+
```

One `warchi_ak_…` secret per agent connection is enough — even when access is limited to specific models with different read/write rights (`mode=grants`).

## Creating a key

In wArchi UI: **Profile → API keys**

- Name
- **Access area** (`mode`):
  - **All accessible models** (`mode=all`) — global scopes `models:read`, `models:write` (write implies read)
  - **Selected models** (`mode=grants`) — per-model scopes (each row: model + read/write; write implies read)
- Optional expiry (API field; profile form may omit it in v1)

Plaintext format: `warchi_ak_<url-safe-random>`  
Shown **once** at creation. arepos stores only a SHA-256 hash + short prefix for UI.

After creation, grants and scopes **cannot** be edited — rename or revoke and recreate.

### Create request shape

`mode=all`:

```json
{
  "name": "Cursor MCP",
  "mode": "all",
  "scopes": ["models:read", "models:write"],
  "grants": null
}
```

`mode=grants` (at most 50 grants; distinct `modelId`; each model must exist and be viewable by the key owner):

```json
{
  "name": "Cursor MCP",
  "mode": "grants",
  "scopes": null,
  "grants": [
    { "modelId": "<uuid>", "scopes": ["models:read"] },
    { "modelId": "<uuid>", "scopes": ["models:read", "models:write"] }
  ]
}
```

Management API (cookie/JWT user session + CSRF):

- `GET /api/v1/api-keys`
- `POST /api/v1/api-keys`
- `PATCH /api/v1/api-keys/{id}` — **name and/or `expiresAt` only** (scopes/grants immutable after create)
- `DELETE /api/v1/api-keys/{id}` (revoke)

Admin (`admin_panel` / user admin gate; metadata only, never plaintext):

- `GET /api/v1/admin/users/{userId}/api-keys`
- `DELETE /api/v1/admin/users/{userId}/api-keys/{keyId}` (revoke; same effect as owner revoke)

## Client → MCP

Send the key on every MCP HTTP request:

```http
Authorization: Bearer warchi_ak_...
```

or:

```http
X-Api-Key: warchi_ak_...
```

`ApiKeyAuthFilter` stores the value in `ApiKeyContext` for the request thread.

## Exchange

```http
POST /api/v1/auth/api-keys/exchange
Content-Type: application/json

{"apiKey":"warchi_ak_..."}
```

Response (`mode=all` example):

```json
{
  "accessToken": "...",
  "expiresIn": 1200,
  "tokenType": "Bearer",
  "mode": "all",
  "scopes": ["models:read", "models:write"],
  "grants": null
}
```

Response (`mode=grants` example):

```json
{
  "accessToken": "...",
  "expiresIn": 1200,
  "tokenType": "Bearer",
  "mode": "grants",
  "scopes": null,
  "grants": [
    { "modelId": "<uuid>", "scopes": ["models:read"] },
    { "modelId": "<uuid>", "scopes": ["models:read", "models:write"] }
  ]
}
```

- No cookie / CSRF required for exchange
- Revoked, expired, or inactive-owner keys → `401`
- JWT claim `type=mcp_access`, TTL ~20 minutes (arepos `JWT_MCP_ACCESS_EXPIRATION`); claims mirror `mode` / `scopes` / `grants`

`AreposAuthClient` caches the JWT until near expiry and re-exchanges on `401`. It only needs `accessToken` / `expiresIn` from the exchange response.

## User deactivation

| Event | API keys |
|-------|----------|
| User deactivated (`isActive=false`) | Keys are **not** auto-revoked |
| Exchange while inactive | `401` — user inactive |
| MCP/user JWT while inactive | Auth refused if `!user.isActive` |
| User reactivated | Same keys work again (unless explicitly revoked) |

To permanently block a key, owner or admin must revoke it explicitly.

## Enforcement (arepos)

Two layers for MCP tokens (`type=mcp_access`):

### Coarse (`McpScopeFilter`)

Early rejection before a specific model is resolved:

| Check | Behavior |
|-------|----------|
| Safe methods (GET/HEAD/OPTIONS) | Token must have **`models:read` somewhere** (`mode=all` scopes or any grant) |
| Mutating methods | Token must have **`models:write` somewhere** |
| Key management | `/api/v1/api-keys` rejects MCP tokens (user session required) |

### Precise (`ResourceAccessService`)

Per-model authorization after coarse pass:

| Mode | Behavior |
|------|----------|
| `mode=all` | Required scope must be in key `scopes`; then Cerbos / ownership as for the key owner |
| `mode=grants` | Find grant for `modelId`: missing → `403 model_not_allowed`; scope missing on grant → `403 missing_scope`; then Cerbos / ownership |

Additional rules:

| Path / action | Behavior |
|---------------|----------|
| `GET /models`, model hits in `search_catalog` | Under `mode=grants`, only models with read in a grant |
| `search_model`, nodes/links/diagrams/wiki | Precise scope check on `modelId` |
| Notations, node/link types, shapes | **Not** filtered by grants — owner/Cerbos only (agents need notation discovery inside granted models) |
| `POST /models` (create root model) | Allowed for `mode=all` with write; **forbidden** for `mode=grants` MCP tokens |
| Cerbos / ownership | Keys never elevate beyond the key owner's UI rights |

Tool errors surfaced by `warchi-mcp`: `missing_scope`, `model_not_allowed`.

## Operational notes

- Prefer least privilege: `mode=grants` with read-only on most models and write only where needed; or `mode=all` with `models:read` when write is not required
- One key per agent — use `mode=grants` for mixed per-model rights instead of multiple secrets
- Revoke immediately if a key leaks
- Do not put keys in git, screenshots, or CI logs
