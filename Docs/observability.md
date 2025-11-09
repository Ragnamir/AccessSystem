# Наблюдаемость: метрики и health

## Метрики (Micrometer / Prometheus)

Экспортируются через `/actuator/metrics` (JSON) и `/actuator/prometheus` (Prometheus текстовый формат).

- access_denials_total
  - Назначение: счётчик отказов в доступе
  - Теги: reason (SIGNATURE_INVALID, TOKEN_INVALID, REPLAY, ACCESS_DENIED, STATE_MISMATCH, INTERNAL_ERROR)
- access_events_success_total
  - Назначение: счётчик успешно обработанных событий (транзакция прошла полностью)
- ingest_event_latency
  - Назначение: латентность обработчика `/ingest/event` (Timer)
  - Экспорт: суммарные/квантили в Prometheus через `_count`, `_sum`, `_max`

Примеры проверок:
- GET `/actuator/metrics/access_denials_total`
- GET `/actuator/prometheus` и поиск `access_denials_total`

## Health Indicators

- DB (встроенный Spring Boot) — проверка подключения к БД
- keys (кастомный) — наличие записей в `issuer_keys` и `checkpoint_keys`
  - Статус UP, если обе таблицы содержат хотя бы 1 запись; иначе DOWN
  - Детали: `issuer_keys.count`, `checkpoint_keys.count`

Пример:
- GET `/actuator/health` (детали включены `show-details: always`)

## Конфигурация

В `application.yml`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: always
```

Зависимости:
- `spring-boot-starter-actuator`
- `micrometer-registry-prometheus`
- `spring-boot-starter-aop` (для аннотаций `@Timed`)

## SLA / Алёрты (черновик)

- Ошибки:
  - alert: рост `access_denials_total{reason!="ACCESS_DENIED"}` (крипто/инфраструктура)
- Доступность:
  - alert: `/actuator/health` status != UP
- Пропускная способность/латентность:
  - alert: p95 `ingest_event_latency` > целевого (например, > 200 мс) в течение 5 мин

## Тестирование

Интеграционный тест `ObservabilityIntegrationTest` проверяет:
- `/actuator/health` и компонент `keys`
- наличие счётчика `access_denials_total`
- доступность `/actuator/prometheus`


