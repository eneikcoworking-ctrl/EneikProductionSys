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

## Agent Account API

Create an agent account:

```bash
curl -X POST http://localhost:8080/api/accounts \
  -H "Content-Type: application/json" \
  -d '{"name":"backend-agent","capabilities":"BARCAN-TAG-02,BARCAN-TAG-11"}'
```

List accounts and update liveness:

```bash
curl http://localhost:8080/api/accounts
curl -X POST http://localhost:8080/api/accounts/<account-id>/heartbeat
curl -X POST http://localhost:8080/api/accounts/<account-id>/status \
  -H "Content-Type: application/json" \
  -d '{"status":"idle"}'
```

Use the created account to claim queued work:

```bash
curl -X POST http://localhost:8080/api/tasks/claim \
  -H "Content-Type: application/json" \
  -d '{"accountId":"<account-id>","capableTags":["BARCAN-TAG-02","BARCAN-TAG-11"]}'
```

## Jules Integration

The project flow can dispatch generated tasks to Google Jules when the repo is installed in Jules and an API key is configured:

```bash
JULES_ENABLED=true
JULES_API_KEY=<your-jules-api-key>
JULES_SOURCE_PREFIX=sources/github/eneikcoworking-ctrl/
JULES_STARTING_BRANCH=main
```

Without these values, tasks are still created locally and marked with the dispatch reason.

## Project Factory

When a project is created through `POST /api/projects`, the system provisions a local isolated workspace, stores the factory status on the project, and creates seven project-scoped Jules accounts. Local workspaces are written to:

```bash
PROJECT_FACTORY_WORKSPACE_ROOT=./project-workspaces
```

GitHub and Linear provisioning are disabled by default for safe local development. Enable them only after configuring credentials:

```bash
GITHUB_ENABLED=true
GITHUB_TOKEN=<github-token-or-app-token>
GITHUB_ORG=eneikcoworking-ctrl

LINEAR_ENABLED=true
LINEAR_API_KEY=<linear-api-key>
LINEAR_TEAM_ID=<linear-team-id>
```

Without these values, the project is still created locally and the dashboard shows skipped GitHub/Linear statuses.

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
