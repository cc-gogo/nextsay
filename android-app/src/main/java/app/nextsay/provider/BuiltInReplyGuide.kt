package app.nextsay.provider

/** Only the selected relationship is sent; no extra model call or user setup is needed. */
internal data class BuiltInReplyGuide(
    val relationship: String,
    val guidance: String,
    val candidateApproaches: List<CandidateApproach>,
    val replyMode: String = LoverReplyModes.NATURAL,
    val preferredLength: Int? = null,
    val patterns: List<ReplyPattern> = emptyList(),
)

internal data class CandidateApproach(val style: String, val approach: String)
internal data class ReplyPattern(val id: String, val applicableWhen: String, val structure: String, val example: String)

internal object BuiltInReplyGuides {
    fun forRelationship(relationship: String, requestedMode: String? = LoverReplyModes.NATURAL): BuiltInReplyGuide {
        val selected = relationship.takeIf { it in guides } ?: "unspecified"
        val (guidance, approaches) = guides.getValue(selected)
        val base = BuiltInReplyGuide(selected, guidance, listOf("concise", "tactful", "natural").zip(approaches) { style, approach ->
            CandidateApproach(style, approach)
        })
        if (selected != "lover") return base
        if (LoverReplyModes.normalize(selected, requestedMode) == LoverReplyModes.HUANGMAO) {
            return base.copy(
                replyMode = LoverReplyModes.HUANGMAO,
                preferredLength = 20,
                guidance = "恋人黄毛模式：短、笃定、松弛、有趣，不急着解释或寻求认可。双方正在轻松接梗时，可以承认后轻轻放大、幽默反转、适度自嘲，调侃后保留真实温暖，不连续对抗。欣赏要具体有依据，不炫耀或编造经历。情绪低落、严肃问题或冲突时先认真回应，可以道歉，不拿表白、拒绝或不适打赌，不玩失联、嫉妒或服从测试。亲近程度按已有聊天，不预设性别，不默认昵称。",
                candidateApproaches = listOf(
                    CandidateApproach("concise", "一句有底气的直接回应，不长篇自证"),
                    CandidateApproach("tactful", "轻松但温暖，保留对方的选择和边界"),
                    CandidateApproach("natural", "双方轻松时接梗或小反转；严肃时自然认真，不硬撩"),
                ),
                patterns = playfulPatterns,
            )
        }
        return base.copy(patterns = naturalPatterns)
    }

    // Independently written examples illustrate structures, never add events to the real chat.
    private val naturalPatterns = listOf(
        ReplyPattern("receive_emotion", "对方分享疲惫、失落或开心", "回应一个具体感受，再给倾听或空间；不抢着说教", "示例：对方说今天累，可说：今天辛苦了，想聊我就听着。"),
        ReplyPattern("reduce_pressure", "对方忙碌、想休息或没有继续话题", "接住当前状态，收住追问；不惩罚、不假装消失", "示例：对方说最近忙，可说：好，你先忙，空了再聊。"),
        ReplyPattern("clarify_one_point", "一个未知或误会影响这轮回应", "温和承接，只澄清一个事实，不猜内心", "示例：对方说你不在乎，可说：是哪件事让你有这种感觉？我想听清楚。"),
        ReplyPattern("soft_repair", "上下文确认我方表达造成误会或伤害", "承认具体问题，说明真实感受或可行修补；不无条件认错、不编造承诺", "示例：确实没表达清楚时，可说：刚才没表达好，我重新说。"),
    )
    private val playfulPatterns = listOf(
        ReplyPattern("playful_agreement", "明确是双方愉快的打趣，不是批评或拒绝", "轻松承认，再加一点无伤害的夸张；不对抗不贬低", "示例：对方笑说你很会说，可说：被你发现了，夸我可以直说。"),
        ReplyPattern("light_flip", "双方轻松且用户愿意表达亲近", "把玩笑换个角度接回去，保持真实；认真问题不能回避", "示例：用户确实想对方，对方问是不是想我了，可说：这都被你猜中了。"),
        ReplyPattern("warm_pull", "对方积极接话或分享一个可欣赏的细节", "调侃之后给真实的认可，只升一点温度；不搞机械推拉比例", "示例：用户也觉得聊天有趣时，可说：巧了，跟你聊天也挺有意思。"),
        ReplyPattern("self_assured_boundary", "对方忙、不适或明确要求停止", "从容接受，短句收住当前方向；不把拒绝说成嘴硬", "示例：对方说别再撩了，可说：好，听你的。"),
    )

    private val guides = mapOf(
        "unspecified" to (
            "关系尚不明确：友好中性，有礼但不疏远。回应当前具体内容，不默认亲密、上下级或交易关系；不贸然使用昵称、调情或套近乎。" to
                listOf("直接回应重点", "照顾感受并留有余地", "口语化接话，不强行追加问题")
        ),
        "manager" to (
            "与领导沟通：尊重、清楚、有担当，不卑微讨好。先回应任务或结论，再给已知进展、限制或必要澄清；没有依据不承诺完成时间、不谎报进度，不把同意当成唯一选择。" to
                listOf("简要回应任务或结论", "说明已知限制并礼貌协商", "自然专业地说明下一步或一个必要问题")
        ),
        "teacher" to (
            "与老师沟通：礼貌真诚，具体表达疑问、反馈或请求。感谢应有依据，不堆敬语；保留学生自己的理解，不编造学习成果、请假理由或作业进度。" to
                listOf("简明回应或说明问题", "礼貌表达请求或不同理解", "自然交流并明确一个学习上的重点")
        ),
        "customer" to (
            "与客户沟通：专业可信，先接住需求或顾虑，再说明已知信息和可选下一步。售后先回应具体问题，不推责；不虚构价格、优惠、库存、交付时间或保证，不为了成交盲目答应。" to
                listOf("直接回应客户需求", "照顾顾虑并澄清可确认的信息", "亲切专业地提出合适下一步，不强推成交")
        ),
        "colleague" to (
            "与同事沟通：平等协作、自然清楚，不官腔不甩锅。任务先回应分工、进展或需要配合的点；闲聊可轻松但不越界。未知工作安排保持未知，不擅自替他人承诺或承担任务。" to
                listOf("简明回应协作重点", "体谅对方并协商边界或安排", "自然口语化回应，可接轻松话题但不强行玩笑")
        ),
        "friend" to (
            "与朋友沟通：平等、随意、有来有回，避免客服腔和说教。先接住对方说的事，再选择共情、轻松接梗或分享已知的自己观点；不能为了接话虚构经历。对方难过或忙时不强行玩笑、不连续追问。" to
                listOf("短而直接地接住对方", "真诚理解，给对方轻松选择或空间", "按我方口吻自然接话，合适时轻松接梗")
        ),
        "family" to (
            "与家人沟通：亲切、具体、有关心，也尊重双方边界。回应实际需要而不空泛叮嘱，不默认辈分、家庭分工或健康状况；不同意见平和表达，不说教、不用亲情施压。" to
                listOf("亲切简短地回应眼前的事", "表达具体关心，同时尊重选择", "像平时家常聊天，不套固定叮嘱")
        ),
        "lover" to (
            "与恋人沟通：温暖、真诚、亲近但不油腻。称呼与亲密程度依据已有聊天和对象偏好，不擅自叫宝贝或升级关系。分享开心时可轻松互动；累、难过或争执时先理解具体感受，再澄清或表达自己的真实态度，不用调情掩盖问题，不无条件认错也不拒绝合理道歉。" to
                listOf("短而温暖地直接回应", "认真承接感受或协商一个实际问题", "贴合我方口吻自然亲近，仅在合适情境轻松互动")
        ),
    )
}
