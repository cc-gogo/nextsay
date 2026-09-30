from __future__ import annotations

import pytest
from pydantic import ValidationError

from nextsay_backend.provider import MockReplyProvider, ReplyProvider
from nextsay_backend.schemas import (
    Message,
    MessageRole,
    Relationship,
    ReplyCandidate,
    ReplyRequest,
    ReplyStyle,
)
from nextsay_backend.service import InvalidProviderResponse, ReplyService


def make_request() -> ReplyRequest:
    return ReplyRequest(
        messages=[
            Message(role=MessageRole.OTHER, text="今天能把方案发给我吗？", confidence=0.95),
            Message(role=MessageRole.ME, text="我正在核对最后的数据。", confidence=0.9),
        ],
        relationship=Relationship.MANAGER,
        locale="zh-CN",
    )


def test_request_requires_at_least_one_message() -> None:
    with pytest.raises(ValidationError):
        ReplyRequest(messages=[])


def test_request_accepts_no_more_than_twenty_messages() -> None:
    messages = [Message(role=MessageRole.OTHER, text=f"消息{i}") for i in range(21)]

    with pytest.raises(ValidationError):
        ReplyRequest(messages=messages)


def test_message_text_is_limited_to_one_thousand_characters() -> None:
    with pytest.raises(ValidationError):
        Message(role=MessageRole.OTHER, text="字" * 1001)


def test_mock_provider_returns_the_three_required_styles() -> None:
    response = ReplyService(MockReplyProvider()).generate(make_request())

    assert [candidate.style for candidate in response.candidates] == [
        ReplyStyle.CONCISE,
        ReplyStyle.TACTFUL,
        ReplyStyle.NATURAL,
    ]
    assert all(candidate.text.strip() for candidate in response.candidates)


class DuplicateProvider(ReplyProvider):
    def generate(self, request: ReplyRequest) -> list[ReplyCandidate]:
        return [
            ReplyCandidate(style=ReplyStyle.CONCISE, text="同一句"),
            ReplyCandidate(style=ReplyStyle.TACTFUL, text="同一句"),
            ReplyCandidate(style=ReplyStyle.NATURAL, text="第三句"),
        ]


def test_service_rejects_duplicate_provider_candidates() -> None:
    with pytest.raises(InvalidProviderResponse, match="distinct"):
        ReplyService(DuplicateProvider()).generate(make_request())

