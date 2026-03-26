from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.db import check_database

app = FastAPI(title="TodoList API", version="0.1.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/api/v1/health", response_model=None)
async def health():
    ok = await check_database()
    if not ok:
        return JSONResponse(
            status_code=503,
            content={"status": "error", "db": "offline"},
        )
    return {"status": "ok", "db": "connected"}


@app.get("/")
async def root() -> dict[str, str]:
    return {"service": "todolist-api"}
