# warchi-mcp

Удалённый сервер [Model Context Protocol](https://modelcontextprotocol.io) для [wArchi](https://warchi.ru). Предоставляет MCP-инструменты для работы с моделями, диаграммами, узлами и связями по Streamable HTTP. Авторизация — персональные API-ключи из профиля wArchi.

English version: [`README.md`](README.md)

Стек: Kotlin + Spring Boot + Spring AI MCP. **Своей БД нет**: ключи и авторизация живут в [arepos-server](https://gitverse.ru/ngroznykh/arepos-server); этот сервис только обменивает ключ на short-lived JWT и проксирует curated tool-вызовы в REST API.

## Возможности

- Удалённый MCP-сервер (Streamable HTTP) для Cursor и других MCP-клиентов
- Авторизация персональным API-ключом (`warchi_ak_…`) со scopes `models:read` / `models:write`
- Опциональный allowlist моделей на ключе (enforcement в arepos)
- Exchange ключа на short-lived JWT (`tokenType=mcp_access`) — ключ не уходит на каждый REST-вызов
- Curated read/write tools для моделей, диаграмм, узлов, связей и сводки нотаций
- Структурированные ошибки tool result: `LOCKED_BY_OTHER`, `BATCH_SAVE_CONFLICT`, `missing_scope`, `model_not_allowed`
- Health через Spring Actuator
- Dockerfile для контейнерного деплоя

## Технологический стек

- Kotlin 2.2.x
- Spring Boot 3.5.x
- Spring AI 1.1.x (`spring-ai-starter-mcp-server-webmvc`)
- JDK 24/25
- Gradle (Kotlin DSL)

## Структура проекта

```text
src/main/kotlin/ru/kavader/warchimcp/
  auth/       # контекст API-ключа + filter
  client/     # RestClient к arepos, exchange, кэш JWT
  config/     # свойства конфигурации
  tools/      # MCP tools (чтение/запись)

src/main/resources/
  application.yaml

docs/         # документация по auth и tools
```

## Требования

- JDK 24 или 25
- Работающий arepos-server с поддержкой API-ключей (миграция `050-api-keys` или новее)
- Docker (опционально, для сборки образа)

## Локальная разработка

### 1) Конфигурация окружения

Значения по умолчанию — в `src/main/resources/application.yaml`. См. также `.env.example`.

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `AREPOS_BASE_URL` | `http://localhost:8080` | Базовый URL arepos-server |
| `PORT` | `8090` | HTTP-порт |
| `AREPOS_CONNECT_TIMEOUT` | `5s` | Таймаут соединения с arepos |
| `AREPOS_READ_TIMEOUT` | `60s` | Таймаут чтения ответа arepos |

### 2) Сборка и запуск

```bash
export AREPOS_BASE_URL=http://localhost:8080
./gradlew build
./gradlew bootRun
```

### 3) Тесты

```bash
./gradlew test
```

## Команды сборки

```bash
./gradlew build       # полная сборка + тесты
./gradlew test        # unit-тесты
./gradlew bootJar     # fat JAR
./gradlew bootRun     # локальный запуск
docker build -t warchi-mcp:0.1.0 .
```

## MCP endpoint

- Протокол: Streamable HTTP (`spring.ai.mcp.server.protocol=STREAMABLE`)
- Путь по умолчанию: `/mcp`
- Health: `GET /actuator/health`

### Конфиг клиента (пример Cursor)

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

Ключ создаётся в wArchi → **Профиль** → **API-ключи**. Plaintext показывается **один раз** при создании.

Также принимается: `X-Api-Key: warchi_ak_…`.

## Поток авторизации

1. Пользователь создаёт API-ключ в wArchi (scopes + опционально UUID моделей).
2. MCP-клиент передаёт ключ в `warchi-mcp`.
3. Сервер вызывает `POST /api/v1/auth/api-keys/exchange` в arepos.
4. Tools ходят в arepos `/api/v1/*` с short-lived JWT.
5. Отзыв ключа в профиле сразу блокирует дальнейший exchange.

Подробнее: [`docs/auth.ru.md`](docs/auth.ru.md).

## Tools (v1)

**Чтение** (`models:read`): `list_models`, `get_model`, `list_diagrams`, `get_diagram`, `list_nodes`, `get_node`, `list_links`, `get_link`, `list_notations`, `get_notation_summary`

**Запись** (`models:write`): `create_node`, `update_node`, `delete_node`, `create_link`, `update_link`, `delete_link`, `update_diagram`, `batch_save_model`

Каталог: [`docs/tools.ru.md`](docs/tools.ru.md).

## Деплой

Типовой production-контур:

1. Задеплоить arepos-server с поддержкой API-ключей.
2. Задеплоить wArchi с UI ключей в профиле.
3. Запустить `warchi-mcp` с `AREPOS_BASE_URL` на arepos.
4. TLS на reverse proxy / ingress (`https://mcp.example.com` → контейнер `:8090`).
5. Проксировать заголовки `Authorization` / `X-Api-Key`.
6. Не логировать API-ключи и JWT.

### Docker

```bash
docker build -t warchi-mcp:0.1.0 .
docker run --rm -p 8090:8090 \
  -e AREPOS_BASE_URL=https://api.example.com \
  warchi-mcp:0.1.0
```

### Smoke-чеклист

- [ ] `POST /api/v1/auth/api-keys/exchange` работает в arepos с тестовым ключом
- [ ] MCP-клиент успешно вызывает `list_models`
- [ ] Отозванный ключ → exchange 401
- [ ] Запись без `models:write` → `missing_scope`
- [ ] Allowlist не пускает к чужим моделям → `model_not_allowed`

## Руководство по open source

- [`CONTRIBUTING.md`](CONTRIBUTING.md) / [`CONTRIBUTING.ru.md`](CONTRIBUTING.ru.md)
- [`SECURITY.md`](SECURITY.md) / [`SECURITY.ru.md`](SECURITY.ru.md)
- [`CODE_OF_CONDUCT.md`](CODE_OF_CONDUCT.md) / [`CODE_OF_CONDUCT.ru.md`](CODE_OF_CONDUCT.ru.md)

## Вклад

Перед PR прочитайте [`CONTRIBUTING.ru.md`](CONTRIBUTING.ru.md).

## Безопасность

Как сообщать об уязвимостях: [`SECURITY.ru.md`](SECURITY.ru.md).

## Лицензия

Двойное лицензирование:

- `AGPL-3.0-or-later` для open-source использования
- Коммерческая лицензия для проприетарного/закрытого коммерческого использования

См.:

- [`LICENSE`](LICENSE)
- [`LICENSE.ru.md`](LICENSE.ru.md)
- [`LICENSE_COMMERCIAL.md`](LICENSE_COMMERCIAL.md)
