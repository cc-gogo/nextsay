from __future__ import annotations

import logging
import secrets
import time
from uuid import uuid4

from fastapi import FastAPI, Header, HTTPException, Request

from .openai_provider import (
    OpenAICompatibleProvider,
    ProviderRequestError,
    ProviderResponseError,
)
from .provider import MockReplyProvider
from .schemas import ReplyRequest, ReplyResponse
from .service import InvalidProviderResponse, ReplyService
from .settings import Settings


logger = logging.getLogger("nextsay.api")


def create_app(settings: Settings | None = None, *, service: ReplyService | None = None) -> FastAPI:
    config = settings or Settings()
    reply_service = service or _build_service(config)
    app = FastAPI(title="NextSay API", version="0.1.0")

    @app.middleware("http")
    async def add_request_metadata(request: Request, call_next):
        request_id = uuid4().hex
        request.state.request_id = request_id
        started = time.perf_counter()
        response = await call_next(request)
        elapsed_ms = round((time.perf_counter() - started) * 1000)
        response.headers["X-Request-ID"] = request_id
        logger.info(
            "request_id=%s method=%s path=%s status=%s latency_ms=%s provider=%s",
            request_id,
            request.method,
            request.url.path,
            response.status_code,
            elapsed_ms,
            config.provider,
        )
        return response

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.post("/v1/replies", response_model=ReplyResponse, response_model_by_alias=True)
    def replies(
        payload: ReplyRequest,
        request: Request,
        authorization: str | None = Header(default=None),
    ) -> ReplyResponse:
        expected = f"Bearer {config.dev_token.get_secret_value()}"
        if authorization is None or not secrets.compare_digest(authorization, expected):
            raise HTTPException(status_code=401, detail="invalid development token")
        try:
            result = reply_service.generate(payload)
        except (ProviderRequestError, ProviderResponseError, InvalidProviderResponse) as exc:
            raise HTTPException(status_code=502, detail="reply provider unavailable") from exc
        return result.model_copy(update={"request_id": request.state.request_id})

    return app


def _build_service(settings: Settings) -> ReplyService:
    if settings.provider == "mock":
        return ReplyService(MockReplyProvider())

    api_key = settings.api_key.get_secret_value()
    if not api_key or not settings.model:
        raise ValueError("NEXTSAY_API_KEY and NEXTSAY_MODEL are required for openai provider")
    return ReplyService(
        OpenAICompatibleProvider(
            base_url=settings.base_url,
            api_key=api_key,
            model=settings.model,
        )
    )


app = create_app()

