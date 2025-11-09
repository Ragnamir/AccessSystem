# Доменная модель

- `UserId`, `ZoneId`, `CheckpointId`, `IssuerId` — value-objects для строгой типизации идентификаторов.
- `PassageDirection` — направление прохода: ENTER | EXIT | TRANSIT.
- `SignedPayload` — байтовая подпись сообщения от пункта пропуска.
- `PassageAttempt` — попытка прохода: включает пользователя, пункт, из/в зону, время и подпись.

Инварианты:
- Идентификаторы не пустые/blank.
- `SignedPayload` не пустой.
- В `PassageAttempt` обязательны: `userId`, `checkpointId`, `direction`, `occurredAt`, `signedPayload`.


