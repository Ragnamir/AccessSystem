# Окружение и профили

- Java 17, Maven 3.9+, Spring Boot 3.3.x.
- Профили:
  - `default` — без обязательного подключения к БД.
  - `test` — подключение к PostgreSQL для интеграционных тестов.

## PostgreSQL (test)
- URL: `jdbc:postgresql://localhost:5432/postgres`
- Пользователь: `postgres`
- Пароль: `postgres`
- Источник: файл `GeneralDocs/TestEnvironment/TestEnvironmentVariables`.

Конфигурация: `src/test/resources/application-test.yml`.

## Запуск локально
```bash
mvn -q -DskipTests compile
mvn -q spring-boot:run
```


