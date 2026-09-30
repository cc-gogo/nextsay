from __future__ import annotations

import logging

import httpx
import pytest

from nextsay_backend.app import create_app
from nextsay_backend.openai_provider import ProviderRequestError
from nextsay_backend.provider import ReplyProvider
from nextsay_backend.schemas import ReplyRequest
from nextsay_backend.service import ReplyService
from nextsay_backend.settings import Settings


def settings() -> Settings:
    return Settings(provider="mock", dev_token="dev-secret")


def valid_payload() -> dict[str, object]:
    return {
        "messages": [{"role": "other", "text": "这是一条不能出现在日志里的消息"}],
        "relationship": "manager",
        "locale": "zh-CN",
    }


@pytest.fixture
def anyio_backend() -> str:
    return "asyncio"


async def request(app, method: str, path: str, **kwargs) -> httpx.Response:
    transport = httpx.ASGITransport(app=app, raise_app_exceptions=False)
    async with httpx.AsyncClient(transport=transport, base_url="http://test") as client:
        return await client.request(method, path, **kwargs)


@pytest.mark.anyio
async def test_health_endpoint_needs_no_authentication() -> None:
    response = await request(create_app(settings()), "GET", "/health")

    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


@pytest.mark.anyio
async def test_reply_endpoint_requires_development_token() -> None:
    response = await request(create_app(settings()), "POST", "/v1/replies", json=valid_payload())

    assert response.status_code == 401


@pytest.mark.anyio
async def test_reply_endpoint_returns_three_candidates_and_request_id() -> None:
    response = await request(
        create_app(settings()),
        "POST",
        "/v1/replies",
        headers={"Authorization": "Bearer dev-secret"},
        json=valid_payload(),
    )

    assert response.status_code == 200
    body = response.json()
    assert len(body["candidates"]) == 3
    assert body["requestId"] == response.headers["X-Request-ID"]


@pytest.mark.anyio
async def test_reply_endpoint_rejects_invalid_payload() -> None:
    response = await request(
        create_app(settings()),
        "POST",
        "/v1/replies",
        headers={"Authorization": "Bearer dev-secret"},
        json={"messages": []},
    )

    assert response.status_code == 422


class FailingProvider(ReplyProvider):
    def generate(self, request: ReplyRequest):
        raise ProviderRequestError("upstream unavailable")


@pytest.mark.anyio
async def test_provider_failure_is_reported_as_bad_gateway() -> None:
    app = create_app(settings(), service=ReplyService(FailingProvider()))

    response = await request(
        app,
        "POST",
        "/v1/replies",
        headers={"Authorization": "Bearer dev-secret"},
        json=valid_payload(),
    )

    assert response.status_code == 502
    assert response.json()["detail"] == "reply provider unavailable"


@pytest.mark.anyio
async def test_operational_logs_exclude_message_and_candidate_text(caplog) -> None:
    caplog.set_level(logging.INFO, logger="nextsay.api")

    response = await request(
        create_app(settings()),
        "POST",
        "/v1/replies",
        headers={"Authorization": "Bearer dev-secret"},
        json=valid_payload(),
    )

    assert response.status_code == 200
    logs = caplog.text
    assert "这是一条不能出现在日志里的消息" not in logs
    for candidate in response.json()["candidates"]:
        assert candidate["text"] not in logs
    assert "request_id=" in logs
    assert "status=200" in logs
