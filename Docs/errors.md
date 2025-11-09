# Справочник причин отказов и кодов ошибок

## Обзор

Система контроля доступа логирует все отказы в доступе в таблицу `denials` с категоризацией по причинам. Это позволяет отслеживать и анализировать различные типы отказов для мониторинга безопасности и диагностики проблем.

## Таблица denials

Таблица `denials` содержит следующие поля:

- `id` (UUID) — уникальный идентификатор записи
- `event_id` (VARCHAR(512), nullable) — идентификатор события, если доступен
- `checkpoint_id` (UUID, nullable) — UUID пропускного пункта
- `checkpoint_code` (VARCHAR(128)) — код пропускного пункта
- `user_id` (UUID, nullable) — UUID пользователя
- `user_code` (VARCHAR(128)) — код пользователя
- `from_zone_id` (UUID, nullable) — UUID исходной зоны
- `from_zone_code` (VARCHAR(128)) — код исходной зоны
- `to_zone_id` (UUID, nullable) — UUID целевой зоны
- `to_zone_code` (VARCHAR(128)) — код целевой зоны
- `reason` (VARCHAR(64), NOT NULL) — категория отказа (см. ниже)
- `details` (TEXT, nullable) — дополнительные детали об отказе
- `created_at` (TIMESTAMPTZ) — время создания записи

## Категории отказов (DenialReason)

### SIGNATURE_INVALID

**Описание:** Проверка криптографической подписи пропускного пункта не прошла.

**Причины:**
- Неверная подпись сообщения
- Отсутствует или поврежден публичный ключ пропускного пункта
- Неверный алгоритм подписи
- Подпись создана другим ключом

**Когда возникает:**
- В `IngestController` при проверке подписи через `CheckpointMessageVerifier`

**HTTP статус:** 403 Forbidden

**Пример записи:**
```sql
INSERT INTO denials (checkpoint_code, reason, details)
VALUES ('cp-1', 'SIGNATURE_INVALID', 'Signature verification failed: Invalid signature');
```

---

### TOKEN_INVALID

**Описание:** Проверка токена пользователя (JWT/JWS) не прошла.

**Причины:**
- Токен истек (expired)
- Неверная подпись токена
- Отсутствует или поврежден публичный ключ издателя токена
- Токен не содержит обязательных полей (userId)
- Неверный формат токена

**Когда возникает:**
- В `IngestController` при проверке токена через `IssuerTokenVerificationService`

**HTTP статус:** 403 Forbidden

**Пример записи:**
```sql
INSERT INTO denials (checkpoint_code, user_code, reason, details)
VALUES ('cp-1', 'user-123', 'TOKEN_INVALID', 'Token verification failed: Token expired');
```

---

### REPLAY

**Описание:** Обнаружена попытка повторного использования события (replay attack).

**Причины:**
- `eventId` уже использован ранее
- Timestamp события находится вне допустимого временного окна
- Событие слишком старое или из будущего

**Когда возникает:**
- В `IngestController` при проверке anti-replay через `AntiReplayService`

**HTTP статус:** 403 Forbidden

**Пример записи:**
```sql
INSERT INTO denials (event_id, checkpoint_code, reason, details)
VALUES ('event-123', 'cp-1', 'REPLAY', 'Anti-replay validation failed: event_id_already_used - Event ID already exists');
```

---

### ACCESS_DENIED

**Описание:** Доступ запрещен правилами доступа.

**Причины:**
- Нет соответствующего правила в таблице `access_rules` для данного пользователя и зон
- Правило существует, но явно запрещает доступ (если в будущем добавится поддержка явного запрета)

**Когда возникает:**
- В `TransactionalEventProcessingService` при проверке правил доступа через `AccessRuleEvaluator`

**HTTP статус:** 403 Forbidden

**Пример записи:**
```sql
INSERT INTO denials (event_id, checkpoint_id, checkpoint_code, user_id, user_code, 
                    from_zone_id, from_zone_code, to_zone_id, to_zone_code, reason, details)
VALUES (
    'event-123',
    '550e8400-e29b-41d4-a716-446655440000',
    'cp-1',
    '660e8400-e29b-41d4-a716-446655440000',
    'user-123',
    '770e8400-e29b-41d4-a716-446655440000',
    'zone-a',
    '880e8400-e29b-41d4-a716-446655440000',
    'zone-b',
    'ACCESS_DENIED',
    'Access rule not found or denied'
);
```

---

### STATE_MISMATCH

**Описание:** Несоответствие состояния пользователя ожидаемому.

**Причины:**
- Пользователь находится не в той зоне, из которой пытается перейти
- Ошибка при обновлении состояния пользователя (оптимистичная блокировка)
- Нарушение инвариантов состояния (например, попытка перехода из NULL в NULL)

**Когда возникает:**
- В `TransactionalEventProcessingService` при обновлении состояния через `UserStateService.updateZone()`

**HTTP статус:** 403 Forbidden

**Пример записи:**
```sql
INSERT INTO denials (event_id, checkpoint_id, checkpoint_code, user_id, user_code,
                    from_zone_id, from_zone_code, to_zone_id, to_zone_code, reason, details)
VALUES (
    'event-123',
    '550e8400-e29b-41d4-a716-446655440000',
    'cp-1',
    '660e8400-e29b-41d4-a716-446655440000',
    'user-123',
    '770e8400-e29b-41d4-a716-446655440000',
    'zone-a',
    '880e8400-e29b-41d4-a716-446655440000',
    'zone-b',
    'STATE_MISMATCH',
    'Failed to update user state: OptimisticLockException: Version mismatch'
);
```

---

### INTERNAL_ERROR

**Описание:** Внутренняя ошибка системы при обработке события.

**Причины:**
- Пропускной пункт не найден в базе данных
- Пользователь не найден в базе данных
- Зона не найдена в базе данных
- Ошибка формата timestamp
- Ошибка при записи события в базу данных
- Неожиданное исключение при обработке

**Когда возникает:**
- В `IngestController` при ошибках парсинга timestamp
- В `TransactionalEventProcessingService` при ошибках разрешения UUID, записи событий и других внутренних ошибках

**HTTP статус:** 400 Bad Request (для ошибок формата) или 403 Forbidden (для других)

**Примеры записей:**
```sql
-- Пропускной пункт не найден
INSERT INTO denials (event_id, checkpoint_code, user_code, reason, details)
VALUES ('event-123', 'cp-unknown', 'user-123', 'INTERNAL_ERROR', 'Checkpoint not found: cp-unknown');

-- Неверный формат timestamp
INSERT INTO denials (checkpoint_code, reason, details)
VALUES ('cp-1', 'INTERNAL_ERROR', 'Invalid timestamp format: 2024-13-45T25:99:99Z');

-- Ошибка записи события
INSERT INTO denials (event_id, checkpoint_id, checkpoint_code, user_id, user_code, reason, details)
VALUES ('event-123', '550e8400-e29b-41d4-a716-446655440000', 'cp-1',
        '660e8400-e29b-41d4-a716-446655440000', 'user-123',
        'INTERNAL_ERROR', 'Failed to record event: Unique constraint violation');
```

---

## Маппинг причин обработки на категории отказов

В `IngestController` и `TransactionalEventProcessingService` происходит маппинг строковых причин на категории `DenialReason`:

| Причина обработки | Категория отказа |
|-------------------|------------------|
| `access_denied` | `ACCESS_DENIED` |
| `state_update_failed`, `state_mismatch` | `STATE_MISMATCH` |
| `checkpoint_not_found`, `user_not_found`, `zone_not_found`, `event_record_failed` | `INTERNAL_ERROR` |
| `replay_detected`, `timestamp_out_of_window`, `event_id_already_used` | `REPLAY` |
| `signature_verification_failed` | `SIGNATURE_INVALID` |
| `token_verification_failed`, `token_expired`, `token_invalid` | `TOKEN_INVALID` |

## Запросы для анализа отказов

### Количество отказов по категориям
```sql
SELECT reason, COUNT(*) as count
FROM denials
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY reason
ORDER BY count DESC;
```

### Отказы по пропускному пункту
```sql
SELECT checkpoint_code, reason, COUNT(*) as count
FROM denials
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY checkpoint_code, reason
ORDER BY checkpoint_code, count DESC;
```

### Отказы по пользователю
```sql
SELECT user_code, reason, COUNT(*) as count
FROM denials
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY user_code, reason
ORDER BY user_code, count DESC;
```

### Последние отказы с деталями
```sql
SELECT 
    created_at,
    checkpoint_code,
    user_code,
    from_zone_code,
    to_zone_code,
    reason,
    details
FROM denials
ORDER BY created_at DESC
LIMIT 100;
```

## Рекомендации по использованию

1. **Мониторинг:** Настройте алерты на высокое количество отказов определенных категорий (особенно `SIGNATURE_INVALID`, `REPLAY`)

2. **Анализ безопасности:** Регулярно анализируйте отказы категорий `SIGNATURE_INVALID` и `REPLAY` для выявления попыток атак

3. **Диагностика:** Используйте поле `details` для детального анализа причин отказов

4. **Производительность:** Таблица `denials` может расти быстро, рассмотрите архивирование старых записей

5. **Индексы:** Используйте существующие индексы на `reason`, `checkpoint_code`, `user_code`, `created_at` для эффективных запросов

