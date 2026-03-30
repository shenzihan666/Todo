from __future__ import annotations

import structlog

logger = structlog.get_logger(__name__)


def build_ask_user_questions_tool(out_questions: list[str]):
    """Return a tool that records clarifying questions for the SSE ``clarification`` event."""

    async def ask_user_questions(
        questions: list[str],
        context: str | None = None,
    ) -> str:
        """Ask the user one or more clarifying questions before changing todos.

        Use when required information is missing or ambiguous (e.g. a date without a clock time).
        Do not use this to replace natural chat; after calling, reply in the user's language
        and incorporate the questions politely.

        Args:
            questions: One or more concrete questions (non-empty strings).
            context: Optional short note on why clarification is needed (for logging only).

        Returns:
            Instruction text for the model to continue the reply.
        """
        cleaned = [str(x).strip() for x in questions if str(x).strip()]
        if not cleaned:
            return (
                "No valid questions were provided. Pass at least one non-empty question string."
            )
        out_questions.extend(cleaned)
        logger.info(
            "agent_tool_call",
            tool="ask_user_questions",
            question_count=len(cleaned),
            context=(context[:200] if context else None),
        )
        joined = "\n".join(f"- {q}" for q in cleaned)
        return (
            "Recorded the following for the client (clarification event). "
            "Reply in the user's language and ask these politely:\n"
            f"{joined}"
        )

    return ask_user_questions
