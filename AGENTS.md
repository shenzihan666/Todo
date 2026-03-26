# AGENTS.md

## Project Overview

TodoList monorepo — **FastAPI** backend (PostgreSQL, Alembic) + **Android** client (Jetpack Compose).

## Repository Structure

```
server/          Python API (FastAPI, SQLAlchemy async, Pydantic)
server/alembic/  Database migrations
server/tests/    Pytest test suite
db/init/         Raw SQL bootstrap (001_schema.sql)
android-app/     Kotlin + Compose (Retrofit, Navigation Compose)
docs/            Project & SDK documentation (see docs/INDEX.md)
```

## Documentation

**Always check `docs/INDEX.md` first** when you need context on SDKs, tools, or architecture.
It contains a keyword-indexed table mapping topics → file paths under `docs/`.

## Tech Stack

| Layer | Tech | Config |
|-------|------|--------|
| API | FastAPI 0.115, Python 3.12+ | `server/pyproject.toml` |
| DB | PostgreSQL 16, SQLAlchemy 2 async + asyncpg | `docker-compose.yml` |
| Migrations | Alembic | `server/alembic.ini` |
| Android | Kotlin, Jetpack Compose, Retrofit | `android-app/app/build.gradle.kts` |
| Lint | Ruff (lint + format) | `server/pyproject.toml [tool.ruff]` |
| Hooks | pre-commit (hygiene, gitleaks, ruff, conventional-commits, pytest) | `.pre-commit-config.yaml` |

## Server Architecture (Layered)

```
api/v1/endpoints/ → schemas/ → services/ → repositories/ → models/
```

- **Endpoints**: route handlers, depend on schemas for I/O
- **Schemas**: Pydantic models (request/response)
- **Services**: business logic
- **Repositories**: DB access (async SQLAlchemy)
- **Models**: ORM models (`models/todo.py`, `models/base.py`)
- **Core**: config, database session, logging, exceptions (`core/`)
- **Speech (STT)**: WebSocket `GET /api/v1/speech/ws` streams PCM (`pcm_s16le`, 16 kHz mono); Faster-Whisper engine in `services/transcription/` (swap implementations without changing the wire protocol)

## Key Commands

```bash
# Docker (from repo root)
docker compose up --build

# Local server
cd server && pip install -e ".[dev]"
alembic upgrade head
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

# Tests
cd server && pytest

# Lint
cd server && ruff check . && ruff format --check .
```

## Conventions

- **Commit messages**: Conventional Commits — `type(scope): description`
  - Types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, `revert`
- **Python style**: Ruff enforced, line-length 99, double quotes, space indent
- **API versioning**: routes under `/api/v1/`, new routers in `app/api/v1/`
- **DB migrations**: always via Alembic (`alembic revision --autogenerate -m "..."`)
- **Environment**: copy `server/.env.example` → `.env`, never commit `.env`
- **Line endings**: LF everywhere except `.bat/.cmd/.ps1` (CRLF)

## Database Schema

Two tables (see `db/init/001_schema.sql`):
- `app_metadata` — key-value store (tracks `schema_version`)
- `todos` — id, title, description, completed, created_at, updated_at

## Android Quick Ref

- Architecture: MVVM — `ui/` → `domain/repository/` → `data/network/`
- DI: manual `AppContainer` (swap to Hilt when KSP resolves)
- Flavors: `dev` (HTTP, local API) / `prod` (HTTPS, production API)
- Config: `local.properties` for `sdk.dir` and optional `local.server.host`
- Speech: `domain/speech/SpeechTranscriber` + `data/speech/` (OkHttp WebSocket); hold-to-talk uses `AudioRecorder` (16 kHz PCM) → `ws://<host>:8000/api/v1/speech/ws`

## File Editing Checklist

1. Run `ruff check` and `ruff format` after Python changes
2. Run `pytest` before committing server changes
3. Use `alembic revision --autogenerate` for any model changes
4. Follow existing patterns in the same layer (endpoints, services, repos)
5. Keep schemas in `schemas/`, never expose ORM models to API layer
