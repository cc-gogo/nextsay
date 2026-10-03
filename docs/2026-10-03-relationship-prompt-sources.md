# Relationship reply guide sources and adaptation

This change adds compact built-in guidance to the existing Android direct-provider generation path. It does not install an Agent Skill, execute upstream scripts, add a server, or send repository contents/phone screenshots to a model.

## Sources read (2026-10-03)

### Natural mode / contextual conversation

- [Goutoujunshi core workflow](https://github.com/shengjidaguai-china/goutoujunshi/blob/master/SKILL.md)
- [Practical reply composer](https://github.com/shengjidaguai-china/goutoujunshi/blob/master/references/practical/实战话术编排器：从一句回复到后续分支.md)
- [Scene calibration](https://github.com/shengjidaguai-china/goutoujunshi/blob/master/references/practical/场景感、松弛感与社交校准：从接话到关系推进.md)
- [Conflict and repair](https://github.com/shengjidaguai-china/goutoujunshi/blob/master/references/knowledge/07-沟通冲突与修复.md)

Adapted ideas: one principal purpose per reply; factual/emotional/relational layers as needed, not a mandatory checklist; length, familiarity and personal wording calibration; acceptance of uncertainty; receiving emotion, lowering pressure, clarification and repair. The upstream questionnaire, MBTI/scoring, multi-person analysis, ongoing tool workflow and long explanatory outputs do not fit NextSay's candidate window and are not added.

### Huangmao mode / concise playful wording

- [Core rules](https://github.com/cugfzx/huangmao-skill/blob/main/SKILL.md)
- [Complete reply example library](https://github.com/cugfzx/huangmao-skill/blob/main/references/pickup-lines.md)
- [Six scenario analyses](https://github.com/cugfzx/huangmao-skill/blob/main/references/scenario-analysis.md)
- [Core concepts](https://github.com/cugfzx/huangmao-skill/blob/main/references/game-concepts.md)

Adapted ideas: concise confident language, playful agreement, a light change of perspective, specific appreciation, and a balance between playfulness and warmth. The core says no emoji and about 20 characters, while examples include emoji/long replies; NextSay therefore treats those as defaults subordinate to the user's current requirements, not truncation rules.

Explicitly excluded: treating rejection as a test, refusing all apologies, dismissing serious questions/emotions, false scarcity or stories, jealousy manipulation, fixed delay/withdrawal schedules, automatic re-contact after blocking, demeaning tests and physical escalation. Serious complaints, grief, fatigue and clear boundaries take precedence in both modes. No gender or personality is inferred from a selected relationship.

The two modes have separate guidance, candidate approaches and teaching patterns. Examples in `BuiltInReplyGuide.kt` are independently written hypothetical demonstrations, not facts or text to mechanically copy into an actual conversation.

## Application behavior

- Eight existing relationships each receive their own default guidance, without manual setup.
- Lover mode defaults to `natural`; `huangmao` is explicitly selected in the object editor and saved per contact.
- Only lover relationships may activate Huangmao. Non-lover/unknown modes fall back to natural.
- Priority: current instruction/draft, personal preferences, user relationship preferences, built-in default guidance. Factual, speaker and boundary constraints always apply.
- One ordinary provider generation request uses the selected guide. No separate classification/research call is introduced.
- Old encrypted profiles/history remain intact via additive database migration 2 → 3; legacy 1 → 2 → 3 remains registered. Modes do not enable automatic generation.
- The floating-ball menu and advanced panel show the saved lover style. Since 0.2.7, management is the single source for relationship/style/material: the panel no longer offers a second per-round relationship setting, and stale relationship arguments cannot override the managed profile.

## Attribution and limits

Goutoujunshi's LICENSE was read and states MIT, copyright (c) 2026 powerycy. Attribution and license are included in the APK assets.

Huangmao README advertises MIT, but its linked `LICENSE` returned HTTP 404 during this review. NextSay does not redistribute that repository or copy its prose/example library; the compact wording and examples here are independently written from the high-level concepts. A verified upstream license would be required before copying substantial text.

The requested Shao Ailun / 邵艾伦 reference has not been independently verified: no representative video/link/example was supplied, and these source files do not identify it as their basis. This release must not claim faithful reproduction of that creator's views or an endorsement.

Local tests verify guide selection, transport, UI state, boundedness, mode isolation and database persistence. They do not establish how a live model will interpret every prompt; live output quality needs separately authorized model evaluation using synthetic examples. No user API or phone operation is part of this development run.
