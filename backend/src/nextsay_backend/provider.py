from __future__ import annotations

from typing import Protocol

from .schemas import MessageRole, ReplyCandidate, ReplyRequest, ReplyStyle


class ReplyProvider(Protocol):
    def generate(self, request: ReplyRequest) -> list[ReplyCandidate]: ...


class MockReplyProvider:
    def generate(self, request: ReplyRequest) -> list[ReplyCandidate]:
        latest = next(
            (message.text for message in reversed(request.messages) if message.role == MessageRole.OTHER),
            request.messages[-1].text,
        )
        topic = latest[:24]
        return [
            ReplyCandidate(style=ReplyStyle.CONCISE, text=f"收到，关于“{topic}”我会尽快处理。"),
            ReplyCandidate(
                style=ReplyStyle.TACTFUL,
                text=f"收到，关于“{topic}”我正在确认具体情况，稍后给您一个准确答复。",
            ),
            ReplyCandidate(
                style=ReplyStyle.NATURAL,
                text=f"好的，我看到“{topic}”了，我先确认一下，确定后马上回复你。",
            ),
        ]

