# Рецепт ландшафта (happy-path)

English: [`landscape-recipe.md`](landscape-recipe.md)

Пошаговый сценарий создания или дополнения архитектурного ландшафта через MCP tools.
Краткий обзор также есть в [`tools.ru.md`](tools.ru.md) (раздел Happy-path).

Все tools возвращают JSON-конверт `{ "ok": true|false, ... }` — см. каталог tools.

## Цель

1. Узлы и связи в **модели** (семантический граф)
2. **Диаграмма** с размещением instances на canvas
3. Опционально — **wiki**-описание

Ключевой принцип: предпочитать **`ensure_*`** вместо **`create_*`** (`create_node` / `create_link` / `create_diagram`) — ensure-вызовы идемпотентны и безопасны при ретраях; `create_*` всегда создают новую сущность и на ретрае дают дубликаты.
`batch_save_model` остаётся escape hatch для атомарных сложных правок.

## Предусловия

| Что нужно | Зачем |
|-----------|--------|
| API-ключ `warchi_ak_…` | Клиент → MCP (`Authorization` / `X-Api-Key`) |
| Scope `models:read` | Discovery (`search_*`, `get_*`, `list_wiki`) |
| Scope `models:write` | `ensure_*`, `add_diagram_instances`, `create_wiki`, … |
| Доступ к целевой модели | `mode=all` или grant на `modelId` |
| File storage (MinIO) в arepos | Только для wiki create/update |

Подробнее об auth: [`auth.ru.md`](auth.ru.md).

## Общая схема

```text
0. Discovery
   search_catalog  →  modelId, notationId
   search_notation →  componentName / relationName  (или id)
   search_model?   →  уже существующие узлы/связи/диаграммы

1. ensure_node  ×N   (notationId + componentName|componentId)
2. ensure_link  ×M   (notationId + relationName|relationId)
3. ensure_diagram
4. add_diagram_instances  (nodes по modelNodeId, edges по modelLinkId)
5. create_wiki?  (опционально)
```

Ориентир объёма: ~5 **типов** вызовов; фактическое число растёт с N узлов и M связей.

## Фаза 0 — Discovery

### 0.1 Модель и нотация

```
search_catalog(q="<имя>", kinds="models,notations", limit=20)
```

Сохранить `modelId` и `notationId`. Если ids уже известны — шаг можно пропустить.

`kinds` по умолчанию — оба (`models,notations`). `limit` по умолчанию 20 (макс. 50).

### 0.2 Типы элементов нотации

```
search_notation(notationId, q="<component>", kinds="components")
search_notation(notationId, q="<relation>", kinds="relations")
```

Hit’ы компактные: `{kind,id,name,version,nodeTypeId|linkTypeId}` — без полного attrs.

**Не** использовать `get_notation_summary` для discovery (тяжёлый ответ).

При неоднозначном имени — `409` / `AMBIGUOUS_NOTATION_ELEMENT` с `candidates`:
уточнить `q` или дальше передавать `componentId` / `relationId` вместо имени.

### 0.3 Существующие элементы модели (опционально)

```
search_model(modelId, q="<имя>", kinds="nodes,links,diagrams")
```

Связи ищутся по именам source/target (у link нет собственного name).
Hit’ы без attrs и canvas — для деталей только `get_*` по выбранным id.

## Фаза 1 — Семантический граф

### 1.1 Узлы

```
ensure_node(
  modelId,
  name,
  notationId,
  componentName   // или componentId; nodeTypeId нужен только без binding
  [, parentNodeId]
)
```

Ответ: `{ node, created }` (`created: true|false`).

| Правило | Деталь |
|---------|--------|
| Ключ матчинга | `modelId + parentNodeId + name` (case-insensitive) |
| Notation binding | Применяется **только при create**; hit не мутирует узел |
| Неоднозначность | Несколько совпадений → `409` / `AMBIGUOUS_NODE` + `candidates` |
| Гонки | Нет DB unique constraint — параллельные dual-ensure могут создать дубли |

Сохранить `node.id` для связей и canvas.

### 1.2 Связи

После узлов:

```
ensure_link(
  modelId,
  sourceId,
  targetId,
  notationId,
  relationName   // или relationId; linkTypeId нужен только без binding
)
```

Ответ: `{ link, created }`.

| Правило | Деталь |
|---------|--------|
| Ключ матчинга | `modelId + sourceId + targetId + linkTypeId` (direction-strict) |
| Resolve типа | `notationId` + `relationName`/`relationId` → `linkTypeId` + `attrs.notationRelations` |
| Гонки | Как у узлов — concurrent dual-create может race |

Сохранить `link.id` для `edgesJson`.

## Фаза 2 — Диаграмма и canvas

### 2.1 Диаграмма

```
ensure_diagram(modelId, name, notationId [, nodeId] [, version] [, attrs])
```

Ответ: `{ diagram, created }`.

| Правило | Деталь |
|---------|--------|
| Ключ матчинга | `modelId + name` → latest non-deleted версия |
| Create defaults | `version=1.0.0`, пустой canvas `{"instances":{"nodes":[],"edges":[]}}` |
| Hit | Поля **не** обновляются |
| Гонки | Dual concurrent ensure может race |

Сохранить `diagram.id` и `diagram.updatedAt` (для optimistic concurrency).

### 2.2 Instances на canvas

```
add_diagram_instances(
  diagramId,
  nodesJson='[{"modelNodeId":"<uuid>","x":100,"y":200,"width":120,"height":60}, ...]',
  edgesJson='[{"modelLinkId":"<uuid>"}, ...]',
  baseUpdatedAt="<ISO-8601>"   // рекомендуется
)
```

| Правило | Деталь |
|---------|--------|
| Merge узлов | По `modelNodeId` (upsert координат / размера) |
| Merge рёбер | По `modelLinkId`; source/target instance ids резолвятся автоматически |
| Удаление | Не трогает instances, которых нет в запросе |
| Конфликт | Устаревший `baseUpdatedAt` → `409` / `DIAGRAM_CONFLICT` |

При `DIAGRAM_CONFLICT`: `get_diagram` → свежий `updatedAt` → повторить merge.

В v1 нет `layout_diagram` — координаты задавать вручную (сетка / эвристика агента).

Для инкрементального canvas предпочитать `add_diagram_instances`, а не полную замену через `update_diagram`.

## Фаза 3 — Wiki (опционально)

```
create_wiki(
  entityKind="diagram",   // model|diagram|node|component|notation|nodeType|linkType
  entityId,
  content,
  [, modelId] [, notationId] [, filename]
)
```

Требует `models:write` и file storage в arepos.
Для diagram/node при необходимости передайте `modelId`; для component — `notationId`.

Обновление существующей страницы:

```
list_wiki(diagramId=...) → get_wiki(fileId=...) → update_wiki(fileId, content)
```

## Обработка ошибок

| Код / статус | Когда | Действие |
|--------------|-------|----------|
| `AMBIGUOUS_NOTATION_ELEMENT` | Несколько component/relation с одним именем | Уточнить поиск или передать id |
| `AMBIGUOUS_NODE` | Несколько узлов по model+parent+name | Выбрать id из `candidates` или уникальное имя |
| `DIAGRAM_CONFLICT` | Устарел `baseUpdatedAt` | Перечитать диаграмму и повторить merge |
| `BATCH_SAVE_CONFLICT` | Конфликт в batch-save | Без silent overwrite, кроме явного `force=true` |
| `409` на `update_diagram` | Не latest по имени или дубль name+version | `ensure_diagram` + `add_diagram_instances` |
| `missing_scope` / `model_not_allowed` | Нет прав | Новый API-ключ с нужными scopes/grants |
| `arepos_error` | Прочая ошибка arepos | Смотреть `status` / `message` / `details` |

**Ретраи:** повторять те же `ensure_*` безопасно. Не переключаться на `create_*` без причины.

Блокировки диаграмм (`/api/v1/diagram-locks/*`, `LOCKED_BY_OTHER`) MCP tools **не** вызывают; UI-редактор может править ту же диаграмму параллельно.

## Когда выходить за happy-path

| Ситуация | Tool |
|----------|------|
| Атомарное изменение многих сущностей | `batch_save_model` |
| Нужны определения customProperties в нотации | `ensure_custom_properties` **до** массового `ensure_node` (нужны права на редактирование нотации) |
| Изменить attrs / имя существующего узла или связи | `update_node` / `update_link` |
| Удалить узел или связь | `delete_node` / `delete_link` |
| Полная замена attrs диаграммы | `update_diagram` (осторожно с 409) |

## Пример: 3 узла и 2 связи

| # | Tool | Суть |
|---|------|------|
| 1 | `search_catalog` | Найти модель и нотацию |
| 2 | `search_notation` | Component (например Application Component) |
| 3–5 | `ensure_node` ×3 | Узлы A, B, C с `componentName` |
| 6 | `search_notation` | Relation (например Serving) |
| 7–8 | `ensure_link` ×2 | A→B, B→C |
| 9 | `ensure_diagram` | Имя ландшафтной диаграммы |
| 10 | `add_diagram_instances` | 3 nodes + 2 edges + `baseUpdatedAt` |
| 11 | `create_wiki` | Описание диаграммы (опционально) |

## Чеклист завершения

- [ ] Узлы созданы/найдены, `node.id` сохранены
- [ ] Связи между нужными source/target, `link.id` сохранены
- [ ] Диаграмма существует (`ensure_diagram`)
- [ ] Canvas содержит нужные instances (`add_diagram_instances`)
- [ ] Нет неразрешённых `AMBIGUOUS_*` / `DIAGRAM_CONFLICT`
- [ ] Wiki создана или обновлена (если нужна)

## Вне этого рецепта (v1)

Не закладывать в сценарий: CRUD нотаций (кроме `ensure_custom_properties`), shares, OEF import, admin tools, stdio, `layout_diagram`, graph neighbors, enforce relation-rules на create, `delete_diagram`.

Полный каталог tools: [`tools.ru.md`](tools.ru.md).
