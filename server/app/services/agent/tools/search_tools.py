from __future__ import annotations

import asyncio

import structlog
from tavily import TavilyClient

logger = structlog.get_logger(__name__)


def build_search_tool(api_key: str):
    """Return a web_search callable backed by the Tavily API."""
    client = TavilyClient(api_key=api_key)

    def _sync_search(query: str, max_results: int) -> str:
        results = client.search(query, max_results=max_results)
        entries = results.get("results", [])
        logger.info(
            "agent_tool_call",
            tool="web_search",
            query=query[:500],
            max_results=max_results,
            result_count=len(entries),
        )
        if not entries:
            return "No results found."
        parts: list[str] = []
        for r in entries:
            title = r.get("title", "")
            content = r.get("content", "")
            url = r.get("url", "")
            parts.append(f"- **{title}**\n  {content}\n  {url}")
        return "\n\n".join(parts)

    async def web_search(query: str, max_results: int = 5) -> str:
        """Search the internet for real-time information on any topic.

        Args:
            query: The search query string.
            max_results: Maximum number of results to return (default 5).

        Returns:
            Search results with titles, URLs, and content snippets.
        """
        return await asyncio.to_thread(_sync_search, query, max_results)

    return web_search
