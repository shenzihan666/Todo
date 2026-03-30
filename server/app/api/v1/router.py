from fastapi import APIRouter

from app.api.v1.endpoints import agent, auth, bills, health, media, speech, tenants, todos

api_router = APIRouter()
api_router.include_router(auth.router, prefix="")
api_router.include_router(health.router, prefix="")
api_router.include_router(tenants.router, prefix="")
api_router.include_router(todos.router, prefix="")
api_router.include_router(bills.router, prefix="")
api_router.include_router(media.router, prefix="")
api_router.include_router(speech.router, prefix="/speech")
api_router.include_router(agent.router, prefix="/agent")
