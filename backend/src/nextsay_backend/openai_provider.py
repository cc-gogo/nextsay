from __future__ import annotations

import json
import re

import httpx
from pydantic import ValidationError

from .prompt import build_messages
from .schemas import ReplyCandidate, ReplyRequest, ReplyResponse


class ProviderRequestError(RuntimeError):
    pass


class ProviderResponseError(RuntimeError):
    pass


class OpenAICompatibleProvider:
    def __init__(
        self,
        *,
        base_url: str,
        api_key: str,
        model: str,
        client: httpx.Client | None = None,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._api_key = api_key
        self._model = model
        self._client = client or httpx.Client()

    def generate(self, request: ReplyRequest) -> list[ReplyCandidate]:
        try:
            response = self._client.post(
                f"{self._base_url}/chat/completions",
                headers={"Authorization": f"Bearer {self._api_key}"},
                json={
                    "model": self._model,
                    "messages": build_messages(request),
                    "temperature": 0.7,
                    "response_format": {"type": "json_object"},
                },
                timeout=10.0,
            )
        except httpx.TimeoutException as exc:
            raise ProviderRequestError("model provider timed out") from exc
        except httpx.RequestError as exc:
            raise ProviderRequestError("model provider request failed") from exc

        if not response.is_success:
            raise ProviderRequestError(f"model provider returned HTTP {response.status_code}")

        try:
            content = response.json()["choices"][0]["message"]["content"]
            if not isinstance(content, str):
                raise TypeError("content must be text")
            decoded = json.loads(_strip_json_fence(content))
            validated = ReplyResponse(candidates=decoded["candidates"])
        except (KeyError, IndexError, TypeError, json.JSONDecodeError, ValidationError) as exc:
            raise ProviderResponseError("model provider returned invalid reply JSON") from exc
        return validated.candidates


def _strip_json_fence(content: str) -> str:
    stripped = content.strip()
    match = re.fullmatch(r"```(?:json)?\s*([\s\S]*?)\s*```", stripped, flags=re.IGNORECASE)
    return match.group(1) if match else stripped

