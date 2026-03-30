"""add check constraint on bills.type

Revision ID: 009_bills_type_check
Revises: 008_estimated_minutes
Create Date: 2026-03-30

"""

from collections.abc import Sequence

from alembic import op

revision: str | None = "009_bills_type_check"
down_revision: str | Sequence[str] | None = "008_estimated_minutes"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_check_constraint(
        "ck_bills_type_income_expense",
        "bills",
        "type IN ('income', 'expense')",
    )


def downgrade() -> None:
    op.drop_constraint("ck_bills_type_income_expense", "bills", type_="check")
