from nextsay_backend.prompt import build_messages
from nextsay_backend.schemas import Message, MessageRole, Relationship, ReplyRequest


def test_prompt_contains_context_and_safety_contract() -> None:
    request = ReplyRequest(
        messages=[Message(role=MessageRole.OTHER, text="明天能交吗？")],
        draft="我还没做完",
        instruction="委婉说明需要延期",
        relationship=Relationship.MANAGER,
        locale="zh-CN",
    )

    messages = build_messages(request)

    assert [message["role"] for message in messages] == ["system", "user"]
    combined = "\n".join(message["content"] for message in messages)
    assert "manager" in combined
    assert "明天能交吗" in combined
    assert "我还没做完" in combined
    assert "委婉说明需要延期" in combined
    assert "不得虚构" in combined
    assert "不得自动发送" in combined
    assert '"concise"' in combined
    assert '"tactful"' in combined
    assert '"natural"' in combined

