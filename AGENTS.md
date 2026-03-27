# AGENTS.md

## Project Overview

TodoList monorepo — **FastAPI** backend (PostgreSQL, Alembic) + **Android** client (Jetpack Compose).

## Repository Structure

```
server/          Python API (FastAPI, SQLAlchemy async, Pydantic); code under server/app/
server/alembic/  Database migrations
server/tests/    Pytest test suite
db/init/         Raw SQL bootstrap (001_schema.sql)
android-app/     Kotlin + Compose (Retrofit, Navigation Compose, OkHttp WebSocket)
docs/            Project & SDK documentation (see docs/INDEX.md)
```

## Documentation

**Always check `docs/INDEX.md` first** when you need context on SDKs, tools, or architecture.
It contains a keyword-indexed table mapping topics → file paths under `docs/`.

## Tech Stack

| Layer | Tech | Config |
|-------|------|--------|
| API | FastAPI 0.115.x, Python 3.12+, **uv** | `server/pyproject.toml`, `server/uv.lock` |
| DB | PostgreSQL 16, SQLAlchemy 2 async + asyncpg | `docker-compose.yml` |
| Migrations | Alembic | `server/alembic.ini` |
| Android | Kotlin 2.2, JVM 17, Jetpack Compose, Retrofit, OkHttp | `android-app/app/build.gradle.kts` |
| Lint | Ruff (lint + format) | `server/pyproject.toml [tool.ruff]` |
| Hooks | pre-commit (hygiene, gitleaks, ruff, conventional-commits) + **pre-push** pytest (server) | `.pre-commit-config.yaml` |

## Server Architecture (Layered)

```
app/api/v1/endpoints/ → schemas/ → services/ → repositories/ → models/
```

(Paths are under `server/app/`.)

- **Endpoints**: route handlers, depend on schemas for I/O — **health** (`GET /api/v1/health`), **tenants** (create), **todos** CRUD (tenant-scoped), **speech** WebSocket
- **Schemas**: Pydantic models (request/response)
- **Services**: business logic
- **Repositories**: DB access (async SQLAlchemy)
- **Models**: ORM models (`models/todo.py`, `models/tenant.py`, `models/base.py`)
- **Core**: config, database session, logging, exceptions (`core/`)
- **Speech (STT)**: WebSocket `WS /api/v1/speech/ws` streams PCM (`pcm_s16le`, 16 kHz mono); Faster-Whisper engine in `services/transcription/` (swap implementations without changing the wire protocol)

## Key Commands

```bash
# Docker (from repo root)
docker compose up --build

# Local server (from server/)
cd server && uv sync --extra dev
uv run alembic upgrade head
uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Tests
cd server && uv run pytest

# Lint
cd server && uv run ruff check . && uv run ruff format --check .
```

## Conventions

- **Commit messages**: Conventional Commits — `type(scope): description`
  - Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, `revert`
- **Python style**: Ruff enforced, line-length 99, double quotes, space indent
- **API versioning**: routes under `/api/v1/`, new routers in `app/api/v1/`
- **DB migrations**: always via Alembic (`alembic revision --autogenerate -m "..."`)
- **Environment**: copy `server/.env.example` → `.env`, never commit `.env`
- **Line endings**: LF everywhere except `.bat/.cmd/.ps1` (CRLF)

## Multi-Tenancy (backend standard)

- **Strategy**: shared PostgreSQL database and schema; **row-level isolation** with a `tenant_id UUID NOT NULL` column on every **business** table, referencing `tenants(id)`.
- **`tenants`**: registry of tenants (primary key `id` UUID). Create tenants via `POST /api/v1/tenants` before scoped operations.
- **System tables** (e.g. `app_metadata`) are **exempt** — they are global, not per-tenant.
- **API tenant context**: callers send **`X-Tenant-ID: <uuid>`** on requests that operate on tenant data (e.g. todos). Future auth (JWT) should supply `tenant_id` from token claims instead of/in addition to this header.
- **Repositories**: must accept `tenant_id` and **always** filter writes/reads by it; never return or mutate rows for another tenant.
- **New tables**: add `tenant_id` + FK to `tenants`, composite/secondary indexes as needed for `(tenant_id, ...)`.

## Database Schema

See `db/init/001_schema.sql` and Alembic migrations:

- `app_metadata` — key-value store (tracks `schema_version`; not tenant-scoped)
- `tenants` — id (UUID), name, timestamps
- `todos` — id, **tenant_id** (FK → `tenants`), title, description, completed, created_at, updated_at

## Android Quick Ref

- Architecture: MVVM — `ui/` → `domain/repository/` → `data/network/` (speech: `data/speech/`, `data/audio/`)
- DI: manual `AppContainer` (swap for Hilt/KSP when plugin resolves)
- Flavors: `dev` (HTTP, local API) / `prod` (HTTPS, production API); `BuildConfig.API_BASE_URL` / `HEALTH_URL`
- Config: `local.properties` for `sdk.dir`, optional `local.server.host` (default in Gradle: `192.168.1.1`) and `local.server.port` (default `8000`)
- Home / speech UI: `ui/home/` — hold-to-talk mic and chat transcript; `domain/speech/SpeechTranscriber` + `data/speech/` (OkHttp WebSocket); `AudioRecorder` (16 kHz PCM) → `ws://<host>:<port>/api/v1/speech/ws` (see `buildSpeechWebSocketUrl` in `ui/settings/SettingsViewModel.kt`)
- Multi-tenant API: register/login returns JWT with `tenant_id`; send **`Authorization: Bearer`** on tenant-scoped HTTP calls (see `AuthInterceptor` in `di/AppContainer.kt`).

## File Editing Checklist

1. Run `uv run ruff check` and `uv run ruff format` after Python changes
2. Run `uv run pytest` before committing server changes (also enforced on **pre-push**)
3. Use `alembic revision --autogenerate` for any model changes
4. Follow existing patterns in the same layer (endpoints, services, repos)
5. Keep schemas in `schemas/`, never expose ORM models to API layer
