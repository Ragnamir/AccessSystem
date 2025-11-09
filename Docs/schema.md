# Схема БД (черновик)

- БД: PostgreSQL
- Миграции: Flyway (`classpath:db/migration`)

## Таблицы

- users
  - id UUID PK
  - code VARCHAR(128) UNIQUE NOT NULL
  - created_at TIMESTAMPTZ NOT NULL DEFAULT now()

- zones
  - id UUID PK
  - code VARCHAR(128) UNIQUE NOT NULL
  - created_at TIMESTAMPTZ NOT NULL DEFAULT now()

- checkpoints
  - id UUID PK
  - code VARCHAR(128) UNIQUE NOT NULL
  - from_zone_id UUID FK -> zones(id)
  - to_zone_id UUID FK -> zones(id)
  - created_at TIMESTAMPTZ NOT NULL DEFAULT now()

- keys
  - id UUID PK
  - user_id UUID FK -> users(id)
  - public_key_pem TEXT NOT NULL
  - created_at TIMESTAMPTZ NOT NULL DEFAULT now()

- access_rules
  - id UUID PRIMARY KEY DEFAULT gen_random_uuid()
  - user_id UUID FK -> users(id)
  - from_zone_id UUID FK -> zones(id), nullable (legacy field, не используется)
  - to_zone_id UUID FK -> zones(id) NOT NULL
  - created_at TIMESTAMPTZ NOT NULL DEFAULT now()

## Индексы

- users(code)
- zones(code)
- checkpoints(code)
- keys(user_id)
- access_rules(user_id)
- access_rules(to_zone_id)
- access_rules(user_id, to_zone_id)

## Диаграмма (текстовая)

```
users (id PK) <1----*> keys (id PK)
   |                           |
   | user_id FK -------------- /
   |
   | user_id FK
   |
access_rules (id PK, user_id FK, to_zone_id FK)

zones (id PK)
   ^
   |
   | to_zone_id FK
   |
access_rules (...)

checkpoints (id PK, from_zone_id FK -> zones.id, to_zone_id FK -> zones.id NULLable для выходов)
```

## Миграции

- **V1__init.sql** — создаёт основные таблицы: users, zones, checkpoints, keys
- **V2__checkpoint_keys.sql** — таблица checkpoint_keys для хранения ключей пропускных пунктов
- **V3__issuer_keys.sql** — таблица issuer_keys для хранения ключей центра выдачи
- **V4__event_nonces.sql** — таблица event_nonces для защиты от replay-атак
- **V5__access_rules.sql** — таблица access_rules для правил доступа пользователей
- **V11__remove_exit_rules.sql** — удаляет персональные правила выхода и возвращает обязательность `to_zone_id`

Все миграции запускаются автоматически при старте приложения (см. `application.yml`).

