from __future__ import annotations

from enum import StrEnum
from uuid import uuid4

from pydantic import BaseModel, ConfigDict, Field


class MessageRole(StrEnum):
    ME = "me"
    OTHER = "other"
    UNKNOWN = "unknown"


class Relationship(StrEnum):
    UNSPECIFIED = "unspecified"
    MANAGER = "manager"
    TEACHER = "teacher"
    CUSTOMER = "customer"
    COLLEAGUE = "colleague"
    FRIEND = "friend"
    FAMILY = "family"


class ReplyStyle(StrEnum):
    CONCISE = "concise"
    TACTFUL = "tactful"
    NATURAL = "natural"


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", str_strip_whitespace=True)


class Message(StrictModel):
    role: MessageRole
    text: str = Field(min_length=1, max_length=1000)
    confidence: float = Field(default=1.0, ge=0.0, le=1.0)


class ReplyRequest(StrictModel):
    messages: list[Message] = Field(min_length=1, max_length=20)
    draft: str = Field(default="", max_length=1000)
    instruction: str = Field(default="", max_length=1000)
    relationship: Relationship = Relationship.UNSPECIFIED
    locale: str = Field(default="zh-CN", min_length=2, max_length=20)


class ReplyCandidate(StrictModel):
    style: ReplyStyle
    text: str = Field(min_length=1, max_length=500)


class ReplyResponse(StrictModel):
    candidates: list[ReplyCandidate] = Field(min_length=3, max_length=3)
    request_id: str = Field(default_factory=lambda: uuid4().hex, serialization_alias="requestId")

