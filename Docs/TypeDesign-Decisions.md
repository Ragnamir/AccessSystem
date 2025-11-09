# Решения по типовой архитектуре (Type Design)

Основано на правилах `122-java-type-design.md`.

- Value Objects вместо примитивов (примитивная одержимость устранена): `UserId`, `ZoneId`, `CheckpointId`, `IssuerId`, `SignedPayload`.
- Контракты отделены от реализаций (интерфейсы):
  - Security: `CheckpointMessageVerifier`, `IssuerTokenDecoder`.
  - Access Control: `AccessRuleEvaluator`, `UserStateService`.
- `AccessService` оркестрирует доменные контракты; логика с ранними возвратами, без лишних try/catch.
- Явные инварианты в конструкторах/record-compact-конструкторах.
- Нейминг согласован с доменом (user, zone, checkpoint, issuer, passage attempt).
- Точность денежных расчётов пока не требуется; при добавлении — использовать `BigDecimal`.
- Границы ответственности:
  - `domain.*` — типы и контракты.
  - `service.*` — приложение и композиция контрактов.
  - `service.stub.*` — временные реализации для запуска и тестов.

Переиспользование/расширение:
- Реальные реализации `CheckpointMessageVerifier` и `IssuerTokenDecoder` можно внедрить, не меняя `AccessService`.
- Правила доступа и хранение состояний пользователя заменяются бинами, не ломая API.


