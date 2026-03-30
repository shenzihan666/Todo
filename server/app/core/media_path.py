"""Shared path resolution for uploaded media (disk layout under [Settings.media_upload_dir])."""

from pathlib import Path

from app.core.config import settings


def resolve_upload_file_path(stored_path: str) -> Path:
    """Return the absolute path for *stored_path* under the upload root.

    Raises:
        ValueError: If the resolved path escapes ``media_upload_dir`` (path traversal).
    """
    root = Path(settings.media_upload_dir).resolve()
    full = (root / stored_path).resolve()
    try:
        full.relative_to(root)
    except ValueError:
        raise ValueError("stored_path outside upload root") from None
    return full
