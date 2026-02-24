# Ticketing Reservation Service

## Run (dev)
- Profile: `dev`
- DB: MariaDB (`ticketing`)

## Health Check
- `GET /health` → `ok`
- `GET /health/db` → `ok` (JDBC `SELECT 1`)

## Tech
- Spring Boot
- Spring Data JPA
- MariaDB