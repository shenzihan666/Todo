"""add tenants table and tenant_id on todos

Revision ID: 002_tenants
Revises: 001_initial
Create Date: 2026-03-27

"""

from collections.abc import Sequence

import sqlalchemy as sa
from sqlalchemy.dialects import postgresql

from alembic import op

revision: str | None = "002_tenants"
down_revision: str | Sequence[str] | None = "001_initial"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "tenants",
        sa.Column("id", postgresql.UUID(as_uuid=True), nullable=False),
        sa.Column("name", sa.String(length=255), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column(
            "updated_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.PrimaryKeyConstraint("id"),
    )
    op.execute(
        """
        INSERT INTO tenants (id, name)
        VALUES ('00000000-0000-0000-0000-000000000001'::uuid, 'Migrated default tenant')
        """,
    )
    op.add_column(
        "todos",
        sa.Column("tenant_id", postgresql.UUID(as_uuid=True), nullable=True),
    )
    op.execute(
        """
        UPDATE todos
        SET tenant_id = '00000000-0000-0000-0000-000000000001'::uuid
        WHERE tenant_id IS NULL
        """,
    )
    op.alter_column("todos", "tenant_id", nullable=False)
    op.create_foreign_key(
        "fk_todos_tenant_id_tenants",
        "todos",
        "tenants",
        ["tenant_id"],
        ["id"],
        ondelete="CASCADE",
    )
    op.create_index("ix_todos_tenant_id", "todos", ["tenant_id"], unique=False)


def downgrade() -> None:
    op.drop_index("ix_todos_tenant_id", table_name="todos")
    op.drop_constraint("fk_todos_tenant_id_tenants", "todos", type_="foreignkey")
    op.drop_column("todos", "tenant_id")
    op.drop_table("tenants")
