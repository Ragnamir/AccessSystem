# Тестирование

## Интеграционные тесты
- `HealthIntegrationTest` — проверяет `GET /health` → `200 OK`, тело `{"status":"OK"}`.
  - В тесте исключена автоконфигурация БД: `spring.autoconfigure.exclude=...DataSourceAutoConfiguration`.
- `DatabaseConnectionTest` — поднимает контейнер PostgreSQL через Testcontainers и проверяет подключение (`SELECT 1`).
  - Используется `@Testcontainers` и `@Container PostgreSQLContainer` с образом `postgres:13-alpine`.
  - Драйверы БД для миграций: `org.flywaydb:flyway-core` + `org.flywaydb:flyway-database-postgresql`.
- `MigrationIntegrationTest` — проверяет применение миграций Flyway и наличие таблиц `users`, `zones`, `checkpoints`, `keys`.
  - Подключение к контейнеру настраивается через `@DynamicPropertySource` (URL, username, password), профиль `test`.

## Запуск тестов
```bash
mvn -q test
```

Условия для интеграционных тестов (Testcontainers):
- Требуется доступный Docker (локально/в Docker Desktop). Контейнеры поднимаются автоматически.
- База данных на хосте не требуется; используется контейнер `postgres:13-alpine`.

Переменные окружения (необязательно):
- Можно переопределять параметры DataSource через `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` при локальном запуске приложения (см. `src/main/resources/application.yml`). В тестах значения поставляются динамически из контейнера.


