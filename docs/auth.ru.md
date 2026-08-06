# Авторизация

English: [`auth.md`](auth.md)

## Обзор

`warchi-mcp` не хранит пользователей и API-ключи. Все учётные данные принадлежат **arepos-server**. Этот сервис только:

1. Принимает API-ключ от MCP-клиента
2. Обменивает его на short-lived JWT
3. Ходит в arepos REST с этим JWT

```text
MCP-клиент --(API-ключ)--> warchi-mcp --(exchange)--> arepos
                               |                        |
                               +--------(JWT REST)------+
```

Одного секрета `warchi_ak_…` на агента достаточно — даже когда доступ ограничен конкретными моделями с разными правами read/write (`mode=grants`).

## Создание ключа

В UI wArchi: **Профиль → API-ключи**

- Название
- **Область доступа** (`mode`):
  - **Все доступные модели** (`mode=all`) — глобальные scopes `models:read`, `models:write` (write подразумевает read)
  - **Выбранные модели** (`mode=grants`) — права на каждую модель (строка: модель + read/write; write подразумевает read)
- Опциональный срок действия (поле API; форма профиля в v1 может его не показывать)

Формат plaintext: `warchi_ak_<url-safe-random>`  
Показывается **один раз** при создании. arepos хранит только SHA-256 hash и короткий prefix для UI.

После создания grants и scopes **нельзя** изменить — только переименовать или отозвать и создать заново.

### Форма запроса создания

`mode=all`:

```json
{
  "name": "Cursor MCP",
  "mode": "all",
  "scopes": ["models:read", "models:write"],
  "grants": null
}
```

`mode=grants` (не более 50 grants; уникальные `modelId`; каждая модель должна существовать и быть доступна на просмотр владельцу ключа):

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

API управления (cookie/JWT сессия пользователя + CSRF):

- `GET /api/v1/api-keys`
- `POST /api/v1/api-keys`
- `PATCH /api/v1/api-keys/{id}` — **только name и/или `expiresAt`** (scopes/grants неизменяемы после создания)
- `DELETE /api/v1/api-keys/{id}` (отзыв)

Админка (`admin_panel` / gate user admin; только метаданные, без plaintext):

- `GET /api/v1/admin/users/{userId}/api-keys`
- `DELETE /api/v1/admin/users/{userId}/api-keys/{keyId}` (отзыв; тот же эффект, что у владельца)

## Клиент → MCP

Передавайте ключ на каждый HTTP-запрос MCP:

```http
Authorization: Bearer warchi_ak_...
```

или:

```http
X-Api-Key: warchi_ak_...
```

`ApiKeyAuthFilter` кладёт значение в `ApiKeyContext` на время запроса.

## Exchange

```http
POST /api/v1/auth/api-keys/exchange
Content-Type: application/json

{"apiKey":"warchi_ak_..."}
```

Ответ (`mode=all`, пример):

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

Ответ (`mode=grants`, пример):

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

- Cookie / CSRF для exchange не нужны
- Отозванный, истёкший ключ или неактивный владелец → `401`
- JWT claim `type=mcp_access`, TTL ~20 минут (`JWT_MCP_ACCESS_EXPIRATION` в arepos); claims повторяют `mode` / `scopes` / `grants`

`AreposAuthClient` кэширует JWT до истечения и делает re-exchange при `401`. Из ответа exchange ему нужны только `accessToken` / `expiresIn`.

## Деактивация пользователя

| Событие | API-ключи |
|---------|-----------|
| Пользователь деактивирован (`isActive=false`) | Ключи **не** отзываются автоматически |
| Exchange при неактивном пользователе | `401` — пользователь неактивен |
| MCP/user JWT при неактивном пользователе | Auth отклоняется при `!user.isActive` |
| Пользователь снова активирован | Те же ключи снова работают (если не отозваны явно) |

Чтобы навсегда заблокировать ключ, владелец или админ должен отозвать его явно.

## Enforcement (arepos)

Два уровня для MCP-токенов (`type=mcp_access`):

### Грубый (`McpScopeFilter`)

Ранняя отсечка до определения конкретной модели:

| Проверка | Поведение |
|----------|-----------|
| Безопасные методы (GET/HEAD/OPTIONS) | У токена должен быть **`models:read` где-то** (scopes при `mode=all` или любой grant) |
| Mutating-методы | У токена должен быть **`models:write` где-то** |
| Управление ключами | `/api/v1/api-keys` отклоняет MCP-токены (нужна пользовательская сессия) |

### Точный (`ResourceAccessService`)

Авторизация по модели после грубой проверки:

| Mode | Поведение |
|------|-----------|
| `mode=all` | Нужный scope должен быть в `scopes` ключа; затем Cerbos / ownership как у владельца ключа |
| `mode=grants` | Grant для `modelId`: нет grant → `403 model_not_allowed`; нет scope на grant → `403 missing_scope`; затем Cerbos / ownership |

Дополнительные правила:

| Путь / действие | Поведение |
|-----------------|-----------|
| `GET /models`, model hits в `search_catalog` | При `mode=grants` — только модели с read в grant |
| `search_model`, nodes/links/diagrams/wiki | Точная проверка scope по `modelId` |
| Нотации, node/link types, shapes | **Не** фильтруются grants — только owner/Cerbos (агенту нужен поиск нотаций внутри granted-моделей) |
| `POST /models` (создание корневой модели) | Разрешено при `mode=all` с write; **запрещено** для MCP-токенов с `mode=grants` |
| Cerbos / ownership | Ключ не расширяет права сверх UI владельца |

Ошибки tool result в `warchi-mcp`: `missing_scope`, `model_not_allowed`.

## Операционные заметки

- Минимальные права: `mode=grants` с read на большинстве моделей и write только где нужно; или `mode=all` с `models:read`, если запись не нужна
- Один ключ на агента — для смешанных прав по моделям используйте `mode=grants`, а не несколько секретов
- При утечке ключа сразу отзывайте его
- Не кладите ключи в git, скриншоты и CI-логи
