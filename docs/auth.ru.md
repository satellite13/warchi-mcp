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

## Создание ключа

В UI wArchi: **Профиль → API-ключи**

- Название
- Scopes: `models:read`, `models:write` (при создании write на сервере также даёт read)
- Опциональный allowlist UUID моделей
- Опциональный срок действия

Формат plaintext: `warchi_ak_<url-safe-random>`  
Показывается **один раз** при создании. arepos хранит только SHA-256 hash и короткий prefix для UI.

API управления (cookie/JWT сессия пользователя + CSRF):

- `GET /api/v1/api-keys`
- `POST /api/v1/api-keys`
- `PATCH /api/v1/api-keys/{id}`
- `DELETE /api/v1/api-keys/{id}` (отзыв)

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

Ответ:

```json
{
  "accessToken": "...",
  "expiresIn": 1200,
  "tokenType": "Bearer",
  "scopes": ["models:read", "models:write"],
  "modelIds": null
}
```

- Cookie / CSRF для exchange не нужны
- Отозванный или истёкший ключ → `401`
- JWT claim `type=mcp_access`, TTL ~20 минут (`JWT_MCP_ACCESS_EXPIRATION` в arepos)

`AreposAuthClient` кэширует JWT до истечения и делает re-exchange при `401`.

## Enforcement (arepos)

| Проверка | Поведение |
|----------|-----------|
| Scope | GET/HEAD → нужен `models:read`; mutating → `models:write` |
| Allowlist | Если заданы `modelIds`, ресурсы вне списка → `403 model_not_allowed` |
| Cerbos / ownership | Те же права, что у владельца ключа в UI — ключ не расширяет привилегии |
| Управление ключами | `/api/v1/api-keys` отклоняет токены `mcp_access` (нужна пользовательская сессия) |

## Операционные заметки

- Для автоматизации берите минимальные scopes и allowlist
- При утечке ключа сразу отзывайте его
- Не кладите ключи в git, скриншоты и CI-логи
