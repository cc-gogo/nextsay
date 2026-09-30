from __future__ import annotations

from .provider import ReplyProvider
from .schemas import ReplyRequest, ReplyResponse, ReplyStyle


class InvalidProviderResponse(ValueError):
    pass


class ReplyService:
    def __init__(self, provider: ReplyProvider) -> None:
        self._provider = provider

    def generate(self, request: ReplyRequest) -> ReplyResponse:
        candidates = self._provider.generate(request)
        expected_styles = {ReplyStyle.CONCISE, ReplyStyle.TACTFUL, ReplyStyle.NATURAL}
        actual_styles = {candidate.style for candidate in candidates}
        if len(candidates) != 3 or actual_styles != expected_styles:
            raise InvalidProviderResponse("provider must return all three reply styles")

        normalized = [candidate.text.strip().casefold() for candidate in candidates]
        if any(not text for text in normalized) or len(set(normalized)) != 3:
            raise InvalidProviderResponse("provider candidates must be non-empty and distinct")

        ordered = sorted(candidates, key=lambda item: list(ReplyStyle).index(item.style))
        return ReplyResponse(candidates=ordered)

