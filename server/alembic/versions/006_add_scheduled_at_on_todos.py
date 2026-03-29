"""add scheduled_at on todos

Revision ID: 006_scheduled_at
Revises: 005_conversations
Create Date: 2026-03-29

"""

from collections.abc import Sequence

import sqlalchemy as sa

from alembic import op

revision: str | None = "006_scheduled_at"
down_revision: str | Sequence[str] | None = "005_conversations"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.add_column(
        "todos",
        sa.Column("scheduled_at", sa.DateTime(timezone=True), nullable=True),
    )
    op.create_index(
        op.f("ix_todos_tenant_id_scheduled_at"),
        "todos",
        ["tenant_id", "scheduled_at"],
        unique=False,
    )


def downgrade() -> None:
    op.drop_index(op.f("ix_todos_tenant_id_scheduled_at"), table_name="todos")
    op.drop_column("todos", "scheduled_at")
