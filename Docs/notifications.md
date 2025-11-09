# Уведомления об отказах доступа

Система контроля доступа поддерживает отправку уведомлений о всех отказах в доступе. Это позволяет внешним системам получать информацию о событиях безопасности в реальном времени.

## Архитектура

Система уведомлений построена на основе интерфейса `NotificationSender`, который позволяет использовать различные реализации:
- **Stub** (заглушка) — для тестирования и разработки, хранит уведомления в памяти
- **Webhook** (будущая реализация) — отправка HTTP POST запросов на указанный URL
- **Queue** (будущая реализация) — отправка в очередь сообщений (RabbitMQ, Kafka и т.д.)

## Конфигурация

Уведомления настраиваются через свойства в `application.yml`:

```yaml
access-system:
  notifications:
    # Тип отправителя: 'stub' (по умолчанию) или 'webhook' (будущая реализация)
    type: ${NOTIFICATION_TYPE:stub}
    
    # URL webhook для отправки уведомлений (используется при type=webhook)
    webhook-url: ${NOTIFICATION_WEBHOOK_URL:}
    
    # Максимальное количество попыток повтора при ошибке отправки
    max-retries: ${NOTIFICATION_MAX_RETRIES:3}
    
    # Задержка между попытками повтора в миллисекундах
    retry-delay-ms: ${NOTIFICATION_RETRY_DELAY_MS:1000}
```

### Переменные окружения

- `NOTIFICATION_TYPE` — тип отправителя (по умолчанию: `stub`)
- `NOTIFICATION_WEBHOOK_URL` — URL для webhook (используется при `type=webhook`)
- `NOTIFICATION_MAX_RETRIES` — максимальное количество повторов (по умолчанию: `3`)
- `NOTIFICATION_RETRY_DELAY_MS` — задержка между повторами в мс (по умолчанию: `1000`)

## Формат уведомления

Уведомление содержит следующую информацию об отказе в доступе:

```java
record DenialNotification(
    String eventId,              // Идентификатор события (может быть null)
    UUID checkpointId,          // UUID контрольной точки (может быть null)
    String checkpointCode,      // Код контрольной точки
    UUID userId,                // UUID пользователя (может быть null)
    String userCode,            // Код пользователя (может быть null)
    UUID fromZoneId,            // UUID исходной зоны (может быть null)
    String fromZoneCode,        // Код исходной зоны (может быть null)
    UUID toZoneId,              // UUID целевой зоны (может быть null)
    String toZoneCode,          // Код целевой зоны (может быть null)
    DenialReason reason,        // Причина отказа (enum)
    String details,             // Дополнительные детали отказа
    long timestamp              // Временная метка уведомления (мс с начала эпохи)
)
```

### Поля уведомления

| Поле | Тип | Описание | Обязательность |
|------|-----|----------|-----------------|
| `eventId` | String | Уникальный идентификатор события | Опционально |
| `checkpointId` | UUID | UUID контрольной точки | Опционально |
| `checkpointCode` | String | Код контрольной точки | Обязательно |
| `userId` | UUID | UUID пользователя | Опционально |
| `userCode` | String | Код пользователя | Опционально |
| `fromZoneId` | UUID | UUID исходной зоны | Опционально |
| `fromZoneCode` | String | Код исходной зоны | Опционально |
| `toZoneId` | UUID | UUID целевой зоны | Опционально |
| `toZoneCode` | String | Код целевой зоны | Опционально |
| `reason` | DenialReason | Причина отказа (enum) | Обязательно |
| `details` | String | Дополнительные детали | Опционально |
| `timestamp` | long | Временная метка уведомления | Автоматически |

### Причины отказа (DenialReason)

| Значение | Описание |
|----------|----------|
| `SIGNATURE_INVALID` | Проверка подписи контрольной точки не прошла |
| `TOKEN_INVALID` | Проверка токена пользователя (JWT/JWS) не прошла |
| `REPLAY` | Обнаружена атака повторного использования (replay attack) |
| `ACCESS_DENIED` | Доступ запрещён правилами доступа |
| `STATE_MISMATCH` | Несоответствие состояния пользователя |
| `INTERNAL_ERROR` | Внутренняя ошибка при обработке |

## Пример JSON уведомления (для webhook)

При реализации webhook, уведомление будет отправляться в формате JSON:

```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "checkpointId": "660e8400-e29b-41d4-a716-446655440001",
  "checkpointCode": "cp-1",
  "userId": "770e8400-e29b-41d4-a716-446655440002",
  "userCode": "user-123",
  "fromZoneId": "880e8400-e29b-41d4-a716-446655440003",
  "fromZoneCode": "zone-a",
  "toZoneId": "990e8400-e29b-41d4-a716-446655440004",
  "toZoneCode": "zone-b",
  "reason": "ACCESS_DENIED",
  "details": "Zone zone-a has no configured exit to OUT",
  "timestamp": 1699123456789
}
```

## Повторы при ошибках (Retry)

Система поддерживает автоматические повторы при ошибках отправки уведомлений:

1. **Максимальное количество повторов**: настраивается через `max-retries` (по умолчанию: 3)
2. **Задержка между повторами**: настраивается через `retry-delay-ms` (по умолчанию: 1000 мс)
3. **Стратегия повторов**: экспоненциальная задержка (каждая попытка увеличивает задержку)

### Обработка ошибок

- Ошибки отправки уведомлений **не влияют** на запись отказа в базу данных
- Ошибки логируются с уровнем `WARN` для `NotificationException` и `ERROR` для неожиданных исключений
- При недоступности `NotificationSender` (опциональная зависимость) система продолжает работу без отправки уведомлений

## Интеграция

Уведомления отправляются автоматически при каждом вызове `DenialRepository.recordDenial()`. Это происходит:

1. После успешной записи отказа в базу данных
2. Асинхронно (не блокирует основной поток обработки)
3. С обработкой ошибок (ошибки не влияют на запись отказа)

### Пример использования

```java
@Repository
public class DenialRepositoryImpl implements DenialRepository {
    
    private final Optional<NotificationSender> notificationSender;
    
    public void recordDenial(...) {
        // 1. Запись в БД
        jdbcTemplate.update(...);
        
        // 2. Отправка уведомления (если доступно)
        sendNotificationIfAvailable(notification);
    }
}
```

## Тестирование

Для тестирования используется `StubNotificationSender`, который:
- Хранит уведомления в памяти
- Предоставляет методы для проверки отправленных уведомлений
- Поддерживает симуляцию ошибок для тестирования обработки ошибок

### Пример теста

```java
@SpringBootTest
@ActiveProfiles("test")
class NotificationIntegrationTest {
    
    @BeforeEach
    void setUp() {
        StubNotificationSender.clearNotifications();
    }
    
    @Test
    void recordDenial_shouldSendNotification() {
        // When
        denialRepository.recordDenial(...);
        
        // Then
        List<DenialNotification> notifications = 
            StubNotificationSender.getSentNotifications();
        assertThat(notifications).hasSize(1);
    }
}
```

## Безопасность

При реализации webhook рекомендуется:

1. **HTTPS**: использовать только HTTPS для передачи уведомлений
2. **Аутентификация**: добавить подпись уведомлений или использовать API ключи
3. **Валидация**: проверять формат и содержимое уведомлений на стороне получателя
4. **Rate limiting**: ограничить частоту отправки уведомлений для предотвращения DoS

## Будущие улучшения

- [ ] Реализация `WebhookNotificationSender` для HTTP POST запросов
- [ ] Реализация `QueueNotificationSender` для очередей сообщений
- [ ] Поддержка batch-отправки уведомлений
- [ ] Метрики и мониторинг успешности отправки
- [ ] Поддержка фильтрации уведомлений по типу причины отказа

