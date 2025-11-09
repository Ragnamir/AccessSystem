# Матрица доступов и правила доступа

## Обзор

Система контроля доступа использует таблицу `access_rules` для определения прав пользователей на вход в конкретные зоны. Если пользователю разрешён вход в зону, это разрешение действует вне зависимости от того, из какой зоны он переходит (включая вход извне). Выход в состояние OUT разрешается автоматически, если для исходной зоны существует конфигурация выхода. Правила хранятся в базе данных и проверяются сервисом `AccessRuleEvaluatorImpl`.

## Структура таблицы access_rules

Таблица `access_rules` содержит следующие поля:

- `id` (UUID) — первичный ключ
- `user_id` (UUID) — идентификатор пользователя (FK → `users.id`)
- `from_zone_id` (UUID, nullable) — техническое поле, оставленное для обратной совместимости (в актуальной схеме всегда `NULL`)
- `to_zone_id` (UUID, NOT NULL) — целевая зона (FK → `zones.id`)
- `created_at` (TIMESTAMPTZ) — время создания правила

Уникальность обеспечивает индекс:

- `UNIQUE (user_id, to_zone_id)` — одно разрешение на вход в конкретную зону

## Матрица доступов

### Пример структуры зон

```
Внешняя среда (NULL)
    ↓
  Zone A (зона входа)
    ↓
  Zone B (промежуточная зона)
    ↓
  Zone C (закрытая зона)
```

### Пример разрешений

#### User 1 (полный доступ)

| Целевая зона | Доступ |
|--------------|--------|
| Zone A       | ✅ Разрешен |
| Zone B       | ✅ Разрешен |

> Примечание: Разрешение на выход (OUT) не требует отдельной записи в `access_rules` и отображено только для наглядности.

#### User 2 (ограниченный доступ)

| Целевая зона | Доступ |
|--------------|--------|
| Zone B       | ✅ Разрешен |
| OUT          | ❌ Запрещен |

### Примеры использования

#### Пример 1: Вход в систему

```sql
-- Пользователь user-1 может войти в Zone A извне
INSERT INTO access_rules (user_id, to_zone_id)
VALUES (
    (SELECT id FROM users WHERE code = 'user-1'),
    (SELECT id FROM zones WHERE code = 'zone-a')
);
```

#### Пример 2: Переход между зонами

```sql
-- Пользователь user-1 может перейти из Zone A в Zone B
INSERT INTO access_rules (user_id, to_zone_id)
VALUES (
    (SELECT id FROM users WHERE code = 'user-1'),
    (SELECT id FROM zones WHERE code = 'zone-b')
);
```

#### Пример 3: Многоуровневый доступ

```sql
-- Пользователь user-1 может перейти из Zone B в Zone C
INSERT INTO access_rules (user_id, to_zone_id)
VALUES (
    (SELECT id FROM users WHERE code = 'user-1'),
    (SELECT id FROM zones WHERE code = 'zone-c')
);
```

## Логика проверки доступа

Сервис `AccessRuleEvaluatorImpl` проверяет доступ следующим образом:

1. Получаем код пользователя и значение целевой зоны (`toZone`). `null`/`OUT` означает попытку выхода.
2. Если `toZone == null`, метод возвращает `ALLOW` — хранить отдельное правило не требуется.
3. Если `toZone != null`, присоединяем `zones` по `to_zone_id` и ищем запись с совпадающим кодом зоны.
4. Параметр `fromZone` используется только для сверки текущего состояния пользователя (см. `state.md`) и на поиск правил не влияет.
5. Результат:
   - `ALLOW` — если правило найдено или выполняется выход
   - `DENY` — если правило для целевой зоны не найдено

## API сервиса

### AccessRuleEvaluator.canTransit()

```java
public interface AccessRuleEvaluator {
    AccessDecision canTransit(UserId userId, ZoneId fromZone, ZoneId toZone);
}
```

**Параметры:**
- `userId` — идентификатор пользователя
- `fromZone` — исходная зона (может быть `null` для входа извне)
- `toZone` — целевая зона

**Возвращает:**
- `AccessDecision.ALLOW` — доступ разрешен
- `AccessDecision.DENY` — доступ запрещен

## Индексы для производительности

Таблица содержит следующие индексы для оптимизации запросов:

- `idx_access_rules_user` — поиск по пользователю
- `idx_access_rules_to_zone` — поиск по целевой зоне
- `ux_access_rules_user_to_zone` — уникальность разрешения на зону

## Примеры запросов

### Получить все правила для пользователя

```sql
SELECT 
    u.code AS user_code,
    to_z.code AS to_zone,
    ar.created_at
FROM access_rules ar
INNER JOIN users u ON ar.user_id = u.id
INNER JOIN zones to_z ON ar.to_zone_id = to_z.id
WHERE u.code = 'user-1'
ORDER BY ar.created_at;
```

### Получить все доступные зоны для пользователя

```sql
SELECT DISTINCT to_z.code AS zone_code
FROM access_rules ar
INNER JOIN users u ON ar.user_id = u.id
INNER JOIN zones to_z ON ar.to_zone_id = to_z.id
WHERE u.code = 'user-1';
```

## Рекомендации по использованию

1. **Инициализация правил:** Назначайте пользователям разрешения на вход в зоны через `to_zone_id`. Разрешение на выход назначается автоматически по наличию выходных checkpoint'ов.
2. **Гранулярность:** Одно правило соответствует одному разрешению на целевую зону, независимо от источника перехода.
3. **NULL значения:** Поле `from_zone_id` заполнять не требуется.
4. **Производительность:** Следите за количеством правил и удаляйте устаревшие записи для поддержания эффективности индексов.
5. **Выходы:** Перед настройкой checkpoint'ов убедитесь, что для каждой зоны, из которой разрешён выход, существует checkpoint с `to_zone_id = NULL`. `TransactionalEventProcessingService` проверяет это условие и блокирует выход при отсутствии конфигурации.

## Интеграция с AccessService

`AccessRuleEvaluator` используется в `AccessService` для проверки прав доступа после успешной верификации подписи и токена:

```java
// В AccessService.processAttempt()
AccessDecision decision = accessRuleEvaluator.canTransit(
    attempt.userId(),
    attempt.fromZone(),
    attempt.toZone()
);
```

Если `decision == DENY`, доступ отклоняется и событие сохраняется в таблице отказов.

