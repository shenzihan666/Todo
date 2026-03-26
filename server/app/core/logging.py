import logging
import sys


def configure_logging(level: int = logging.INFO) -> None:
    """Configure root logger for structured-ish console output."""
    logging.basicConfig(
        level=level,
        format="%(asctime)s | %(levelname)s | %(name)s | %(message)s",
        datefmt="%Y-%m-%dT%H:%M:%S",
        stream=sys.stdout,
        force=True,
    )
