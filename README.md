# TodoList

Monorepo: **FastAPI** backend (PostgreSQL, Alembic), **Android** client (Jetpack Compose). Layout is layered for extension, migration between environments, and dependency upgrades.

## Repository layout

| Path | Role |
|------|------|
| [`server/`](server/) | Python API (`app/` package: `core`, `api`, `models`, `schemas`, `services`, `repositories`) |
| [`server/alembic/`](server/alembic/) | Database migrations |
| [`db/init/`](db/init/) | Optional raw SQL bootstrap (used by `scripts/init_local_db.py`) |
| [`android-app/`](android-app/) | Kotlin + Compose app (`data`, `domain`, `ui`, `di`, `navigation`) |

## Backend (local)

Requirements: Python 3.12+, PostgreSQL, and [uv](https://docs.astral.sh/uv/) for installing and running the server (see below).

1. Create a database and configure env (from repo root or `server/`):

   ```bash
   cd server
   cp .env.example .env
   # edit .env — set POSTGRES_PASSWORD and any overrides
   ```

2. Sync dependencies and run migrations:

   ```bash
   uv sync --extra dev
   uv run alembic upgrade head
   ```

   Alternatively, bootstrap with SQL only (creates DB if missing):

   ```bash
   uv run python scripts/init_local_db.py
   ```

3. Run the API:

   ```bash
   uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
   ```

- OpenAPI: `http://127.0.0.1:8000/docs`
- Health: `GET /api/v1/health` (expects `app_metadata.schema_version` after migrations or init script)
- Multi-tenant: see [docs/domain/multi-tenancy.md](docs/domain/multi-tenancy.md) (JWT `tenant_id`, bootstrap header, etc.)

## Backend (Docker)

From the **repository root**:

```bash
docker compose up --build
```

If `uv sync` fails during the image build (e.g. PyPI timeouts), retry the build or use a faster network mirror; the [`server/Dockerfile`](server/Dockerfile) installs dependencies with **uv** from [`server/uv.lock`](server/uv.lock) for reproducible builds.

- API: `http://127.0.0.1:8000`
- Postgres: `localhost:5432` (user/password/db: `postgres` / `postgres` / `todolist` as in Compose)

The API image runs `alembic upgrade head` before starting Uvicorn.

## Tests (server)

```bash
cd server
uv sync --extra dev
uv run pytest
```

## Android app

Requirements: Android Studio / JDK 17+, Android SDK.

1. Copy `android-app/local.properties.example` to `local.properties` and set `sdk.dir` (and optional `local.server.host` / `local.server.port` for the **dev** flavor).

2. Build the **dev** debug APK (HTTP to your LAN API):

   ```bash
   cd android-app
   ./gradlew assembleDevDebug
   ```

3. **prod** flavor points at `https://api.todolist.com` (adjust in `app/build.gradle.kts` if needed).

### Client architecture

- **Networking:** Retrofit + OkHttp + `kotlinx.serialization` (`data/network/`).
- **Domain:** repository interfaces (`domain/repository/`).
- **Data:** `HealthRepositoryImpl` and API DTOs (`data/`).
- **DI:** [`AppContainer`](android-app/app/src/main/java/com/todolist/app/di/AppContainer.kt) holds singletons; `TodoListApplication` exposes a `ViewModelProvider.Factory` for `HealthViewModel`. You can replace this with **Hilt + KSP** when your Gradle environment resolves the KSP plugin for your Kotlin/AGP versions.

- **UI:** `ui/home/` (main voice/chat), `ui/auth/`, `ui/settings/`, `ui/theme/`, `navigation/` (Navigation Compose).

## Git hooks

This repo uses [pre-commit](https://pre-commit.com/) to run automated checks on every commit and push. New contributors must install the hooks once after cloning.

### Quick setup

```bash
uv tool install pre-commit
pre-commit install --install-hooks -t pre-commit -t commit-msg -t pre-push
```

The first run downloads isolated environments for each hook (~2-5 min); subsequent runs are cached and fast.

### What the hooks do

| Stage | Hooks | What they check |
|-------|-------|-----------------|
| **pre-commit** | trailing-whitespace, end-of-file-fixer, check-yaml/toml/json, check-merge-conflict, check-added-large-files, detect-private-key, mixed-line-ending | File hygiene |
| **pre-commit** | [gitleaks](https://github.com/gitleaks/gitleaks) | Secrets scanning |
| **pre-commit** | [ruff](https://docs.astral.sh/ruff/) lint + format | Python linting & formatting (staged files only) |
| **commit-msg** | [conventional-pre-commit](https://github.com/compilerla/conventional-pre-commit) | Enforces [Conventional Commits](https://www.conventionalcommits.org/) format |
| **pre-push** | pytest | Runs server test suite before push |

### Commit message format

All commits must follow Conventional Commits:

```
type(optional-scope): short description

[optional body]
```

Allowed types: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `perf`, `ci`, `build`, `revert`.

### Skipping hooks

```bash
git commit --no-verify            # skip ALL hooks (emergency only)
SKIP=ruff-format git commit ...   # skip a single hook by id
```

### Running manually

```bash
pre-commit run --all-files        # check every file in the repo
pre-commit run ruff --all-files   # run one specific hook
pre-commit autoupdate             # bump hook versions to latest
```

## Linting (Python)

The server uses [Ruff](https://docs.astral.sh/ruff/) for linting and formatting. Configuration lives in [`server/pyproject.toml`](server/pyproject.toml).

```bash
cd server
uv run ruff check .          # lint
uv run ruff check --fix .    # lint + auto-fix
uv run ruff format .         # format
```

## API versioning

Versioned routes live under `/api/v1/`. Add new routers in `app/api/v1/` and include them from `app/api/v1/router.py`.
