"""add estimated_minutes on todos

Revision ID: 008_estimated_minutes
Revises: 007_bills
Create Date: 2026-03-30

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str | None = "008_estimated_minutes"
down_revision: str | Sequence[str] | None = "007_bills"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "todos",
        sa.Column("estimated_minutes", sa.Integer(), nullable=True),
    )


def downgrade() -> None:
    op.drop_column("todos", "estimated_minutes")
