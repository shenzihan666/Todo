"""Bill field parsing shared by agent tools and execute-actions (no underscore imports)."""

from __future__ import annotations

from decimal import Decimal, InvalidOperation


def normalize_bill_type(value: str | None) -> str | None:
    """Return ``income`` / ``expense`` or None if invalid or empty."""
    if value is None or not str(value).strip():
        return None
    s = str(value).strip().lower()
    if s in ("income", "expense"):
        return s
    return None


def parse_amount(value: object) -> Decimal | None:
    """Parse a positive decimal amount with two fractional digits, or None."""
    if value is None:
        return None
    try:
        d = Decimal(str(value))
    except (InvalidOperation, ValueError, TypeError):
        return None
    if d <= 0:
        return None
    return d.quantize(Decimal("0.01"))
