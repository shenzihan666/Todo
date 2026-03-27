import uuid
from typing import Annotated

from fastapi import APIRouter, Depends, File, UploadFile, status
from fastapi.responses import FileResponse

from app.api.deps import get_media_service
from app.schemas.media import MediaRead
from app.services.media_service import MediaService

router = APIRouter()


@router.post("", response_model=MediaRead, status_code=status.HTTP_201_CREATED)
async def upload_media(
    service: Annotated[MediaService, Depends(get_media_service)],
    file: UploadFile = File(...),
) -> MediaRead:
    return await service.create_upload(file)


@router.get("/{media_id}")
async def download_media(
    media_id: uuid.UUID,
    service: Annotated[MediaService, Depends(get_media_service)],
) -> FileResponse:
    return await service.get_media_file(media_id)
