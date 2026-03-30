"""Tests for agent scheduled_at parsing and confirmation display strings."""

from datetime import UTC

from app.services.agent.tools.db_tools import (
    _format_display_scheduled_at,
    _parse_scheduled_at_iso_raw,
    parse_scheduled_at_iso,
)


def test_display_uses_wall_clock_not_utc_for_offset_iso():
    """+08:00 08:00 must show 08:00, not 00:00 (UTC)."""
    raw = _parse_scheduled_at_iso_raw("2026-03-31T08:00:00+08:00")
    assert raw is not None
    out = _format_display_scheduled_at(raw)
    assert out is not None
    assert "08:00" in out
    assert "00:00" not in out


def test_parse_for_db_normalizes_to_utc():
    raw = _parse_scheduled_at_iso_raw("2026-03-31T08:00:00+08:00")
    assert raw is not None
    utc = parse_scheduled_at_iso("2026-03-31T08:00:00+08:00")
    assert utc is not None
    assert utc.tzinfo == UTC
    assert utc.hour == 0 and utc.minute == 0


def test_format_display_z_utc_midnight():
    raw = _parse_scheduled_at_iso_raw("2026-03-31T00:00:00Z")
    assert raw is not None
    out = _format_display_scheduled_at(raw)
    assert out is not None
    assert "00:00" in out
