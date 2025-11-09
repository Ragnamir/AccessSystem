# API

## Health
- Метод: GET `/health`
- Ответ: `200 OK`, тело: `{ "status": "OK" }`
- Зависимости: нет (в тесте автоконфигурация БД исключена).

Расширение: в будущем добавить `/actuator/health` через Spring Boot Actuator.


## Ingest
- Метод: POST `/ingest/event`
- Тело запроса (JSON):
  - `checkpointId` (string, required)
  - `timestamp` (string, ISO-8601 UTC с суффиксом `Z`, required)
  - `fromZone` (string, required)
  - `toZone` (string, required)
  - `userToken` (string, required)
  - `signature` (string, required)
- Успех: `202 Accepted`, тело: `{ "status": "accepted", "checkpointId": "..." }`
- Ошибка валидации: `400 Bad Request`

Спецификация: см. `Docs/openapi.yaml`.


