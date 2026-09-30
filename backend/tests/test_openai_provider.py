from __future__ import annotations

import json

import httpx
import pytest

from nextsay_backend.openai_provider import (
    OpenAICompatibleProvider,
    ProviderRequestError,
    ProviderResponseError,
)
from nextsay_backend.schemas import Message, MessageRole, ReplyRequest, ReplyStyle


def make_request() -> ReplyRequest:
    return ReplyRequest(messages=[Message(role=MessageRole.OTHER, text="方便现在沟通吗？")])


def payload() -> dict[str, object]:
    return {
        "candidates": [
            {"style": "concise", "text": "可以，您说。"},
            {"style": "tactful", "text": "可以的，您方便的话现在说就好。"},
            {"style": "natural", "text": "可以呀，你说吧。"},
        ]
    }


def provider_for(handler) -> OpenAICompatibleProvider:
    client = httpx.Client(transport=httpx.MockTransport(handler))
    return OpenAICompatibleProvider(
        base_url="https://model.example/v1",
        api_key="test-key",
        model="test-model",
        client=client,
    )


def test_provider_parses_structured_candidates() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        assert request.headers["authorization"] == "Bearer test-key"
        body = json.loads(request.content)
        assert body["model"] == "test-model"
        return httpx.Response(
            200,
            json={"choices": [{"message": {"content": json.dumps(payload(), ensure_ascii=False)}}]},
        )

    candidates = provider_for(handler).generate(make_request())

    assert [candidate.style for candidate in candidates] == list(ReplyStyle)


def test_provider_accepts_one_json_markdown_fence() -> None:
    content = "```json\n" + json.dumps(payload(), ensure_ascii=False) + "\n```"
    provider = provider_for(
        lambda request: httpx.Response(200, json={"choices": [{"message": {"content": content}}]})
    )

    assert len(provider.generate(make_request())) == 3


def test_provider_maps_timeout_to_request_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ReadTimeout("slow provider", request=request)

    with pytest.raises(ProviderRequestError, match="timed out"):
        provider_for(handler).generate(make_request())


def test_provider_rejects_non_success_status() -> None:
    provider = provider_for(lambda request: httpx.Response(429, json={"error": "rate limited"}))

    with pytest.raises(ProviderRequestError, match="429"):
        provider.generate(make_request())


@pytest.mark.parametrize(
    "content",
    [
        "not json",
        json.dumps({"candidates": payload()["candidates"][:2]}, ensure_ascii=False),
    ],
)
def test_provider_rejects_malformed_or_incomplete_content(content: str) -> None:
    provider = provider_for(
        lambda request: httpx.Response(200, json={"choices": [{"message": {"content": content}}]})
    )

    with pytest.raises(ProviderResponseError):
        provider.generate(make_request())

