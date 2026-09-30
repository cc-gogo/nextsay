from __future__ import annotations

import json

from .schemas import ReplyRequest


SYSTEM_PROMPT = """你是 NextSay 的回复生成器。请站在用户立场，根据给定对话生成下一句可直接发送的回复。
规则：
1. 只使用提供的上下文，不得虚构日期、承诺、事实、身份或关系。
2. 不要解释如何回复，不要输出思考过程。
3. 不得自动发送；你的职责仅是生成候选文本供用户确认。
4. 除非用户明确要求长文，否则每条回复保持简短。
5. 使用请求中的语言，默认简体中文。
6. 三条回复含义和措辞必须明显不同。
7. 只返回 JSON，不要使用 Markdown。
返回格式：
{"candidates":[{"style":"concise","text":"..."},{"style":"tactful","text":"..."},{"style":"natural","text":"..."}]}
"""


def build_messages(request: ReplyRequest) -> list[dict[str, str]]:
    payload = {
        "relationship": request.relationship.value,
        "locale": request.locale,
        "messages": [message.model_dump(mode="json") for message in request.messages],
        "draft": request.draft,
        "instruction": request.instruction,
    }
    return [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": json.dumps(payload, ensure_ascii=False)},
    ]

