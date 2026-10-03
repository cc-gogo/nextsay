# 微信隐藏输入焦点直接写入实验设计

## 背景

荣耀 X40 上的微信聊天输入框已经获得系统输入焦点，`dumpsys input_method` 显示其为 `MMEditText`，但 UIAutomator 和普通无障碍节点遍历只能看到空根节点。当前 `ReplyInserter` 只递归收集节点树中的 `isEditable` 节点，因此微信必然返回“未找到可写入输入框”；QQ 的标准输入框不受影响。

## 目标

- 在递归遍历前，调用微信应用窗口根节点的 `findFocus(AccessibilityNodeInfo.FOCUS_INPUT)`。
- 直接焦点节点只有同时满足可编辑、已聚焦、非密码时才进入现有写入流程。
- 如果直接焦点为空或不安全，回退到当前递归选择逻辑，继续兼容 QQ。
- 点击候选只执行现有 `ACTION_SET_TEXT`，绝不执行发送、坐标点击、手势、剪贴板粘贴或按键注入。

## 实现边界

新增纯 Kotlin `DirectFocusPolicy`，输入 `editable`、`focused`、`password` 三项元数据，输出是否可使用直接焦点。`ReplyInserter.selectField()` 负责：

1. 调用 `root.findFocus(FOCUS_INPUT)`。
2. 用 `DirectFocusPolicy` 检查返回节点。
3. 安全时直接返回该节点；不安全时回收节点并执行原有树遍历。
4. 后续包名、空文本和字段安全校验仍由现有 `ReplyInserterPolicy` 执行。

不读取、记录或输出输入框文字。实验不删除 NextSay 输入法，也不改变浮球、OCR、后端或历史记录。

## 测试

自动化单元测试覆盖：

- 可编辑、已聚焦、非密码节点可被直接使用。
- 未聚焦、不可编辑或密码节点全部拒绝。
- 现有 `EditableFieldSelectorTest` 和 `ReplyInserterPolicyTest` 继续通过。

真机实验：

- 将当前输入法切回搜狗。
- 微信聊天输入框获得焦点并保持搜狗键盘展开。
- 通过绿色浮球生成候选并点击一条。
- 成功标准：候选写入微信输入框但不发送。
- 失败标准：仍显示复制兜底；此时认定荣耀 X40 的微信没有向无障碍服务开放直接焦点节点，停止继续叠加无障碍模拟方案。

## 回退

直接焦点路径是树遍历之前的单一分支。若真机失败或影响 QQ，删除该分支和 `DirectFocusPolicy` 即恢复现状，不涉及数据迁移。

## 真机结论

2026-08-12 在荣耀 X40 微信真机验证失败：候选点击后仍进入复制兜底，未取得可用于 `ACTION_SET_TEXT` 的微信直接输入焦点。实验代码已回退。后续不再增加坐标、长按菜单或手势模拟写入；微信稳定写入继续依赖真正的 `InputMethodService`。
