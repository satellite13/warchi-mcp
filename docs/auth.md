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

## Creating a key

In wArchi UI: **Profile → API keys**

- Name
- Scopes: `models:read`, `models:write` (write implies read on the server side when creating)
- Optional model UUID allowlist
- Optional expiry

Plaintext format: `warchi_ak_<url-safe-random>`  
Shown **once** at creation. arepos stores only a SHA-256 hash + short prefix for UI.

Management API (cookie/JWT user session + CSRF):

- `GET /api/v1/api-keys`
- `POST /api/v1/api-keys`
- `PATCH /api/v1/api-keys/{id}`
- `DELETE /api/v1/api-keys/{id}` (revoke)

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

Response:

```json
{
  "accessToken": "...",
  "expiresIn": 1200,
  "tokenType": "Bearer",
  "scopes": ["models:read", "models:write"],
  "modelIds": null
}
```

- No cookie / CSRF required for exchange
- Revoked or expired keys → `401`
- JWT claim `type=mcp_access`, TTL ~20 minutes (arepos `JWT_MCP_ACCESS_EXPIRATION`)

`AreposAuthClient` caches the JWT until near expiry and re-exchanges on `401`.

## Enforcement (arepos)

| Check | Behavior |
|-------|----------|
| Scope | GET/HEAD → need `models:read`; mutating → need `models:write` |
| Allowlist | If `modelIds` set, model-bound resources outside the list → `403 model_not_allowed` |
| Cerbos / ownership | Same as the key owner in the UI — keys never elevate privileges |
| Key management | `/api/v1/api-keys` rejects `mcp_access` tokens (user session required) |

## Operational notes

- Prefer least-privilege scopes and allowlists for automation keys
- Revoke immediately if a key leaks
- Do not put keys in git, screenshots, or CI logs
