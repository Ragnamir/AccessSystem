# Архитектура

- Язык/платформа: Java 17, Spring Boot 3.3.x
- Слои:
  - `api` — REST-контроллеры (`/health`).
  - `service` — прикладные сервисы оркестрации (`AccessService`).
  - `domain` — доменные типы и контракты (интерфейсы):
    - Value Objects: `UserId`, `ZoneId`, `CheckpointId`, `IssuerId`, `SignedPayload`.
    - Модели: `PassageAttempt`, `PassageDirection`.
    - Контракты: `CheckpointMessageVerifier`, `IssuerTokenDecoder`, `AccessRuleEvaluator`, `UserStateService`.
  - `service.stub` — минимальные реализации интерфейсов для сборки и тестов.

- Разделение «что/как»:
  - Контракты в `domain.contracts.*` определяют поведение.
  - Реализации регистрируются отдельными бинами, легко заменяемы на реальные.

- Хранилище:
  - Пока не используется JPA; подключение к PostgreSQL для интеграционных тестов.

## Принятые решения по БД и миграциям

- Миграции выполняются Flyway из `classpath:db/migration`.
  - Первая версия схемы: `V1__init.sql` (таблицы `users`, `zones`, `checkpoints`, `keys` + индексы).
- Для поддержки актуальных версий PostgreSQL используется связка зависимостей:
  - `org.flywaydb:flyway-core`
  - `org.flywaydb:flyway-database-postgresql`
- Конфигурация приложения (`src/main/resources/application.yml`):
  - Параметры `spring.datasource.*` допускают переопределение через `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`.
  - `spring.flyway.enabled=true`, `locations=classpath:db/migration`.

## Интеграционные тесты и окружение

- Тесты используют Testcontainers для изоляции и повторяемости:
  - Контейнер `postgres:13-alpine` поднимается автоматически в `DatabaseConnectionTest` и `MigrationIntegrationTest`.
  - Параметры подключения в тестах пробрасываются через `@DynamicPropertySource` (без зависимостей от локальной БД).
- Причины выбора Testcontainers:
  - Повторяемость окружения CI/локально; отсутствие ручной подготовки БД.
  - Быстрое создание/уничтожение БД между тестами.

## Совместимость и версии

- Закреплена версия Flyway и добавлен модуль БД (`flyway-database-postgresql`) для поддержки детектирования версий PostgreSQL.
- В контейнерах тестов используется совместимый образ PostgreSQL (`13-alpine`). При необходимости версию можно поднять после обновления зависимостей Flyway.


