"""Tests for bill tool helpers (display labels, amount parsing)."""

from decimal import Decimal

from app.services.agent.tools.bill_tools import (
    _parse_amount,
    format_display_amount,
)


def test_format_display_amount() -> None:
    assert format_display_amount(Decimal("50"), "expense") == "支出 ¥50.00"
    assert format_display_amount(Decimal("1200.5"), "income") == "收入 ¥1200.50"


def test_parse_amount() -> None:
    assert _parse_amount("12.34") == Decimal("12.34")
    assert _parse_amount(100) == Decimal("100.00")
    assert _parse_amount(None) is None
    assert _parse_amount("-1") is None
    assert _parse_amount("0") is None
