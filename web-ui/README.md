# Web UI Module

Веб-интерфейс для отображения данных системы контроля доступа.

## Описание

Отдельный Spring Boot модуль, предоставляющий веб-интерфейс (Thymeleaf) для просмотра:
- Список пропускных пунктов
- Список пользователей с текущим положением
- Список последних переходов (события)
- Список отказов

## Технологии

- Java 17
- Spring Boot 3.3.5
- Thymeleaf для шаблонов
- PostgreSQL (JDBC, без JPA)
- Testcontainers для интеграционных тестов

## Запуск

### Предпосылки

- Java 17+
- Maven
- PostgreSQL (та же БД, что используется основным приложением)

### Конфигурация

Модуль использует те же переменные окружения для подключения к БД:
- `DB_URL` (по умолчанию: `jdbc:postgresql://localhost:5432/postgres`)
- `DB_USERNAME` (по умолчанию: `postgres`)
- `DB_PASSWORD` (по умолчанию: `postgres`)

### Запуск приложения

```bash
cd web-ui
mvn spring-boot:run
```

Приложение будет доступно по адресу: `http://localhost:8081`

### Запуск тестов

```bash
cd web-ui
mvn test
```

## Endpoints

- `GET /` - Главная страница с навигацией
- `GET /checkpoints?page=0&size=20` - Список пропускных пунктов
- `GET /users?page=0&size=20` - Список пользователей
- `GET /events?page=0&size=20` - Список последних переходов
- `GET /denials?page=0&size=20` - Список отказов

### Параметры пагинации

- `page` - номер страницы (начиная с 0, по умолчанию: 0)
- `size` - размер страницы (по умолчанию: 20)

## Безопасность

**Важно**: Модуль не обращается к таблицам, содержащим ключи:
- `keys`
- `checkpoint_keys`
- `issuer_keys`

Все SQL запросы явно ограничены только таблицами для отображения данных.

## Структура модуля

```
web-ui/
├── src/main/java/com/example/webui/
│   ├── WebUiApplication.java
│   ├── controller/
│   │   └── DashboardController.java
│   ├── service/
│   │   ├── CheckpointService.java
│   │   ├── UserService.java
│   │   ├── EventService.java
│   │   ├── DenialService.java
│   │   └── PageResult.java
│   └── repository/
│       ├── CheckpointRepository.java
│       ├── CheckpointRepositoryImpl.java
│       ├── UserRepository.java
│       ├── UserRepositoryImpl.java
│       ├── EventRepository.java
│       ├── EventRepositoryImpl.java
│       ├── DenialRepository.java
│       ├── DenialRepositoryImpl.java
│       └── View models (CheckpointView, UserView, EventView, DenialView)
└── src/main/resources/
    ├── application.yml
    └── templates/
        ├── index.html
        ├── checkpoints.html
        ├── users.html
        ├── events.html
        └── denials.html
```

