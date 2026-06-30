# EneikProductionSys — Integration Contract (читать ВСЕМ агентам перед началом работы)

Это единственный источник правды по интеграции. Если в вашем ролевом промте что-то
противоречит этому файлу — приоритет у этого файла. Цель: после слияния всех 7 веток
система поднимается одной командой и работает на локальной машине без правок.

## 1. Стек и порты (зафиксировано, не менять)
| Компонент            | Технология              | Локальный порт | URL |
|----------------------|--------------------------|-----------------|-----|
| Backend API          | Java 17 + Spring Boot 3  | 8080            | http://localhost:8080 |
| Frontend             | Svelte (Vite) + Nginx    | 3000            | http://localhost:3000 |
| AI Prediction Service| Python FastAPI           | 8000            | http://localhost:8000 |
| База данных          | H2 (file-based, для локали) / Postgres (опционально в compose) | 5432 (если postgres) | — |

## 2. Единый REST-контракт Backend ⇄ Frontend
Базовый путь: `/api/v1`

### GET /api/v1/greetings/latest
Ответ 200:
```json
{
  "id": "uuid-string",
  "message": "string",
  "currentStatus": "RECEIVED | IN_PROGRESS | COMPLETED | BLOCKED",
  "createdAt": "2026-06-30T10:00:00Z",
  "leadTimeSeconds": 42
}
```

### POST /api/v1/greetings
Запрос:
```json
{ "message": "string" }
```
Ответ 201: тот же объект, что и GET /latest.
Ответ 400 (нарушение комплаенса): `{ "error": "Compliance Violation", "code": 400 }`

### Внутренний вызов Backend ⇄ AI-сервис
POST `http://localhost:8000/api/v1/predict/bottleneck`
Запрос: `{ "wip_count": int, "avg_cycle_time": float }`
Ответ: `{ "risk_score": float, "is_bottleneck_predicted": boolean }`

Этот URL в Java НЕ хардкодить — только через `application.properties` ключ `ml.service.url`.

## 3. CORS (обязательно для Backend и QA)
Backend должен разрешать запросы с `http://localhost:3000`. Без этого Svelte не сможет
обращаться к API — проверка этого факта входит в обязанности QA (тест на CORS-заголовки).

## 4. Структура репозитория (финал, после миграции)
```
/src/main/java/com/eneik/production/...      ← Backend (Java Spring Boot)
/src/main/resources/db/migration/...          ← SQL миграции
/src/test/java/com/eneik/production/...       ← Backend тесты
/frontend/src/...                             ← Svelte приложение (новая директория!)
/src/models/ml/PredictionService.py           ← AI-сервис (FastAPI)
/deploy/iac/...                               ← Terraform манифесты
/Dockerfile                                   ← Backend образ
/Dockerfile.frontend                          ← Frontend образ
/docker-compose.yml                           ← Единая точка запуска всей системы
/scripts/deploy.sh                            ← Скрипт сборки и запуска
/scripts/linear_sync.py                       ← Синхронизация с Linear
/docs/metrics/report_latest.md                ← Отчеты Six Sigma
```
ВАЖНО: Svelte-проект должен жить в `/frontend`, а не в `/src/views` — это отдельный
npm-проект со своим `package.json`, `vite.config.js`. Старая директория `src/views`
(React/TSX) удаляется при миграции (Промт 2).

## 5. Финальная команда запуска (критерий успеха всей работы)
```bash
docker-compose up --build
```
После этого должны быть доступны:
- http://localhost:3000 — рабочий Svelte UI с живыми данными
- http://localhost:8080/api/v1/greetings/latest — рабочий JSON-эндпоинт
- http://localhost:8000/api/v1/predict/bottleneck — рабочий AI-эндпоинт

## 6. Порядок слияния веток (чтобы не было конфликтов)
1. Промт 1 (Backend/Data) — модель и репозиторий
2. Промт 3 (Security) — фильтр поверх контроллера
3. Промт 4 (AI) — внешний сервис + клиент
4. Промт 2 (Frontend) — Svelte UI (зависит от контракта из п.2, не от кода Backend)
5. Промт 5 (DevOps) — Docker/Compose/Terraform (зависит от того, что есть Dockerfile-цели)
6. Промт 6 (QA) — тесты (запускаются после слияния 1,3,4)
7. Промт 7 (BA) — метрики и Linear sync (не блокирует остальных, делается параллельно)

Каждый агент перед началом читает файл своей роли в корне репозитория
(`<Role>.md` либо секцию в `docs/DIRECTORY_MAP.md`) и этот контракт.
