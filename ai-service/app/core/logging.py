"""
core/logging.py
───────────────
Structured JSON logging for the platform backend.

Usage
-----
    from app.core.logging import get_logger
    logger = get_logger(__name__)
    logger.info("Server started", extra={"port": 8000})

All log records are emitted as a single JSON object per line, making them
easy to ingest with any log aggregation tool (Loki, Datadog, CloudWatch …).
"""
import json
import logging
import sys
from typing import Any


class _JsonFormatter(logging.Formatter):
    """Format log records as single-line JSON objects."""

    def format(self, record: logging.LogRecord) -> str:
        log_object: dict[str, Any] = {
            "timestamp": self.formatTime(record, self.datefmt),
            "level": record.levelname,
            "logger": record.name,
            "message": record.getMessage(),
        }

        # Merge any extra fields the caller passed via `extra={...}`
        skip_fields = {
            "name", "msg", "args", "levelname", "levelno", "pathname",
            "filename", "module", "exc_info", "exc_text", "stack_info",
            "lineno", "funcName", "created", "msecs", "relativeCreated",
            "thread", "threadName", "processName", "process", "message",
            "taskName",
        }
        for key, value in record.__dict__.items():
            if key not in skip_fields:
                log_object[key] = value

        # Append exception info when present
        if record.exc_info:
            log_object["exception"] = self.formatException(record.exc_info)

        return json.dumps(log_object, default=str)


def _configure_root_logger(level: int = logging.INFO) -> None:
    """Attach the JSON handler to the root logger (idempotent)."""
    root = logging.getLogger()
    if root.handlers:
        # Already configured — avoid adding duplicate handlers
        return
    handler = logging.StreamHandler(sys.stdout)
    handler.setFormatter(_JsonFormatter())
    root.addHandler(handler)
    root.setLevel(level)


# Configure once at import time.
_configure_root_logger()


def get_logger(name: str) -> logging.Logger:
    """Return a named logger that inherits the JSON handler from root.

    Args:
        name: Typically ``__name__`` of the calling module.

    Returns:
        A :class:`logging.Logger` instance.
    """
    return logging.getLogger(name)
