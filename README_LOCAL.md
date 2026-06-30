# Local Development

This project runs locally as a three-service Docker Compose stack:

- Spring Boot backend: http://localhost:8080
- Svelte frontend: http://localhost:3000
- FastAPI ML service: http://localhost:8000/docs

## Prerequisites

- Docker Desktop running
- Docker Compose plugin available via `docker compose`

## Start

```bash
./scripts/deploy.sh
```

Or run Compose directly:

```bash
docker compose up --build -d
```

## Verify

```bash
docker ps
curl http://localhost:8080/
curl http://localhost:8080/health
curl http://localhost:8000/docs
curl http://localhost:3000
curl http://localhost:8080/api/v1/greetings/latest
```

`GET /api/v1/greetings/latest` returns `404` until at least one greeting exists. Seed one with:

```bash
curl -X POST http://localhost:8080/api/v1/greetings \
  -H "Content-Type: application/json" \
  -d '{"message":"Local stack is operational"}'
```

## Logs

```bash
docker compose logs -f
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f ml
```

## Stop

```bash
docker compose down
```

To remove local container data as well:

```bash
docker compose down -v
```
