# Event Generator

Small standalone Spring Boot app that seeds minimal data and sends events to the Access System `/ingest/event` endpoint at a configurable rate.

## Run

```bash
mvn -q -f event-generator/pom.xml spring-boot:run \
  -Dspring-boot.run.arguments="--generator.dbUrl=jdbc:postgresql://localhost:5432/postgres --generator.dbUser=postgres --generator.dbPassword=postgres --generator.ingestUrl=http://localhost:8080/ingest/event --generator.ratePerSecond=2"
```

## What it does
- Seeds DB if empty: 
  - **3 zones**: zone-a, zone-b, zone-c
  - **2 users**: user-1 (full access), user-2 (limited access)
  - **3 checkpoints**: cp-a-b (zone-a → zone-b), cp-b-c (zone-b → zone-c), cp-c-a (zone-c → zone-a)
  - **Access rules**: 
    - user-1: OUT→zone-a, zone-a→zone-b, zone-b→zone-c, zone-c→zone-a, zone-a→OUT
    - user-2: OUT→zone-a, zone-a→zone-b, zone-b→OUT
  - Issuer and checkpoint keys
- Rotates scenarios: valid passage, bad signature, replay, access denied
- Uses different users, zones, and checkpoints across scenarios
- Signs payloads per canonical format used by tests

## Config
- `generator.ingestUrl` (default `http://localhost:8080/ingest/event`)
- `generator.ratePerSecond` (default `1`)
- `generator.seedDatabase` (default `true`)
- `generator.dbUrl`, `generator.dbUser`, `generator.dbPassword`

## Smoke Test
Point `ingestUrl` to a running Access System. Verify 202 for valid events and denials for invalid ones.


