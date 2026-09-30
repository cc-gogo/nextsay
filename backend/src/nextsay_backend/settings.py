from __future__ import annotations

from typing import Literal

from pydantic import SecretStr
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_prefix="NEXTSAY_",
        env_file=".env",
        extra="ignore",
    )

    provider: Literal["mock", "openai"] = "mock"
    api_key: SecretStr = SecretStr("")
    base_url: str = "https://api.openai.com/v1"
    model: str = ""
    dev_token: SecretStr = SecretStr("local-dev-token")

