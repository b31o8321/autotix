# Autotix 重设计

**日期**：2026-05-14
**状态**：草案 v1（待用户试用反馈）
**前置版本**：[`2026-05-12-autotix-design.md`](./2026-05-12-autotix-design.md) + 11 个已交付的 slice（auth / ticket / AI / SLA / 附件等）

---

## 一、背景与目标

11 个 slice 跑通后端到端，但 UI 偏 antd 默认样式，对话视图缺少客户上下文，领域里没有 Customer 聚合、tag 库、自定义字段等"产品化"必备物。

**本次重设计目标**：在不推翻现有代码的前提下，把 autotix 从 "scaffold + 跑通" 升级为 "可被中小团队作为日常工具长期使用"。

**目标用户画像**：
- 4–15 人客服 / 支持团队
- 50–500 工单 / 天
- 技术能力足够自托管
- **AI-first 心态**：希望让 AI 处理大部分常规工单，坐席只接管 AI 不擅长或被显式升级的部分

**差异化定位**：
- vs Chatwoot：AI 是一等公民（不是后补插件），右栏 AI 协作面板贯穿对话
- vs Zendesk：轻量级（Help Scout 视觉密度），自托管，省人省钱
- vs Plain：自托管 + 中文友好 + 多渠道（Plain 主打英文 SaaS）

---

## 二、本次重设计范围

**包含（incremental 调整）**：

1. 新增领域：Customer 聚合 / TagDefinition / CustomFieldDefinition
2. Ticket 新字段：customerId / aiSuspended / customFields / escalatedAt
3. 信息架构：合并 Inbox + Desk → 统一 "Inbox" 一个页面
4. 视觉系统：Help Scout 风浅色基调 + design token 化
5. 三栏详情页：左 ticket 列表 / 中对话流 / 右客户信息+AI 协作面板
6. 邮件式对话堆叠（替代当前聊天气泡）
7. AI 协作面板（草稿生成 + confidence + 操作按钮）
8. 设置项：autoReply 全局/渠道双控 + tag 库 + 自定义字段 schema

**不包含**（已存在或下个版本）：

- 多语言 i18n（保持当前中文为主）
- 移动端 / 响应式（v1 桌面优先）
- 报表 / dashboard（先维持占位，专门重做一版）
- OAuth 各平台授权（沿用当前 API-key 路径）
- 工单合并 / 父子关系（slice 8 已有 parentTicketId for spawn，merge 后续）
- 业务时间（SLA 暂不计算工作日历）
- 通知出站（Slack/邮件 SLA 提醒）

---

## 三、领域模型增量

### 3.1 新增聚合：Customer

```
Customer
  id: CustomerId                     (Long primary key)
  displayName: String?               (从首次出现的 channel 拿 customerName)
  primaryEmail: String?              (有 EMAIL identifier 时填)
  identifiers: List<CustomerIdentifier>
  attributes: Map<String, String>    (自定义字段值)
  createdAt / updatedAt: Instant

CustomerIdentifier (value object)
  type: enum { EMAIL, PHONE, LINE_USER_ID, WECOM_USER_ID, WHATSAPP_NUM, INSTAGRAM_HANDLE, ZENDESK_USER, CUSTOM_EXTERNAL }
  value: String                       (实际标识，如邮箱字符串)
  channelId: ChannelId?               (哪个 channel 第一次发现这个标识)
  firstSeenAt: Instant
```

**聚合规则**（`CustomerLookupService.findOrCreate(channelId, identifierType, value)`）：

1. (type, lowercase value) 在 `customer_identifier` 表有命中 → 返回对应 Customer
2. 无命中 → 新建 Customer，挂一个 identifier 行
3. 之后若同 value 在另一个 channel 出现 → 给现有 Customer 追加 identifier（不新建 Customer）

**手动合并**（v2 backlog）：admin 在 Customer 详情页可"merge into another customer"，迁移所有 ticket 与 identifier。

### 3.2 新增：TagDefinition

```
TagDefinition
  id / name / color (hex) / category: String? / createdAt
```

- 删除 Ticket 上的 `tags_csv` 不变，依然存原始字符串
- 但前端选择 UI 改为"输入框 + 下拉建议（从 TagDefinition 列表）"
- 坐席输入新 tag 自动 upsert 到 `tag_definition`（首次使用时入库）
- Settings 加一个 Tags 页：CRUD + 颜色 / 分类

### 3.3 新增：CustomFieldDefinition

```
CustomFieldDefinition
  id / name / key / type ∈ {TEXT, NUMBER, DATE}
  appliesTo ∈ {TICKET, CUSTOMER}
  required: boolean
  displayOrder: int
```

- Ticket 增 `customFields: Map<String,String>`（JSON 列）
- Customer 复用上面的 `attributes: Map<String,String>`
- v1 只支持 TEXT/NUMBER/DATE 三种基本类型；DROPDOWN/MULTI-SELECT 进 backlog
- Settings 加一个 Custom Fields 页：管理 schema

### 3.4 Ticket 字段调整

```
+ customerId: CustomerId?            (替换 customerIdentifier 作为主链接)
+ aiSuspended: boolean (default false) (转人工后 AI 不再介入此工单)
+ escalatedAt: Instant?              (何时转的人工)
+ customFields: Map<String,String>   (key -> value，按 CustomFieldDefinition.key 索引)
保留: customerIdentifier 冗余字段 (便于无 customer 时也能展示)
```

**新动作**：
- `Ticket.escalateToHuman(actorId)` → `aiSuspended=true` + `escalatedAt=now` + 活动日志 `ESCALATED`
- `Ticket.resumeAi(actorId)` → `aiSuspended=false` + 活动日志 `AI_RESUMED`（admin only）
- `Ticket.setCustomField(key, value)` + `Ticket.clearCustomField(key)`

**`DispatchAIReplyUseCase` 改造**：
- 流程开头检查 `ticket.aiSuspended()`；如果 true，直接返回，不调 AI
- 队列里收到该 ticketId 也跳过（AI dispatch subscriber 处也检查一次）

### 3.5 新增 ActivityAction

加 `ESCALATED` 和 `AI_RESUMED` 到 `TicketActivityAction` 枚举。

### 3.6 持久化

新建表：`customer`, `customer_identifier`, `tag_definition`, `custom_field_definition`。
Ticket 加列：`customer_id`, `ai_suspended`, `escalated_at`, `custom_fields_json`。
所有 4 个 dialect schema 文件同步。

---

## 四、信息架构

### 4.1 nav 收敛

当前主侧栏：`Desk · Inbox · Reports · Settings`
重设计后：`Inbox · Reports · Settings`（**Desk 与 Inbox 合并**）

不再有 "Inbox 实时流 + Desk 表格" 两套概念，统一一个 **Inbox** 页。SSE 事件实时更新左栏列表的位置 / 未读标 / 计数徽章。

### 4.2 左栏"智能视图"（Smart Views）

Inbox 页左栏顶部 5 个内置筛选 tab：

| Tab | 含义 |
|---|---|
| **Mine** | `assigneeId = currentUser` |
| **Unassigned** | `assigneeId IS NULL AND status NOT IN (SOLVED, CLOSED, SPAM)` |
| **Open** | `status IN (NEW, OPEN, WAITING_ON_CUSTOMER, WAITING_ON_INTERNAL) AND aiSuspended=false` |
| **Needs human** | `aiSuspended=true OR slaBreached=true` |
| **All** | 无筛选（含 SOLVED / CLOSED，可选） |

每个 tab 显示未读计数徽章（基于读取记录 - 实现见 §6.4）。

下方有"工单状态切换"二级筛选（Open / Solved / Closed / Spam），与 smart view 正交叠加。

底部一个"+ Saved view"按钮，v2 让用户保存自定义筛选条件（v1 仅内置 5 个）。

### 4.3 Settings 页内 nav 调整

Settings 子页扩到：

```
Channels · AI Configuration · Tags · Custom Fields · Automation Rules ·
SLA Policies · Users · General (新)
```

- **Tags**：管理 TagDefinition 库（CRUD + 颜色 + 分类）
- **Custom Fields**：管理 CustomFieldDefinition schema
- **General**（新）：AI 全局 autoReply 开关 + 重开窗口天数 + bootstrap admin 信息等"杂项"

### 4.4 顶部 header

简化为：左侧 logo · 中间空 · 右侧：当前用户头像（dropdown：profile / 退出登录）。

去掉当前的粗大 Header bar。

---

## 五、页面级 UI

### 5.1 Inbox 主页（三栏）

```
┌────────┬─────────────────────┬──────────────────────────┬──────────────────────┐
│ logo   │ ▼ Mine        12    │  Order issue #1234       │ Customer             │
│        │   Unassigned   5    │  ──────────────────────  │ ────────              │
│ ◆ Inbox│   Open        24    │                          │ Alice                 │
│ ◯ Reports  Needs human  3    │  [ Email · 2h ago ]      │ alice@example.com     │
│ ⚙ Settings All          88   │   Hi, my order #1234     │ Tickets · 3            │
│        │                     │   hasn't arrived yet...  │ ─                      │
│        │ ─                   │                          │ Tags                   │
│        │ ● Order issue       │  [ AI · 1h ago ]         │ [refund] [logistics]   │
│        │   alice · 2m        │   Hello, I looked into   │ + add                  │
│        │   refund · normal   │   your order...          │ ─                      │
│        │ ─                   │                          │ Custom fields          │
│        │ ◯ Refund query      │  [ Internal note · 30m ] │ Order #: 1234         │
│        │   bob · 1h          │   AI not handling refund │ Plan: pro              │
│        │   high · SLA!       │   — assigning to Carol   │ ─                      │
│        │ ─                   │                          │ ┌────────────────────┐ │
│        │ ◯ Login problem     │  [ Reply box ]           │ │  AI Draft          │ │
│        │   carol · 3h        │  ☐ Internal note         │ │  (collapsible)     │ │
│        │   low               │  📎  [ Reply ▾ ]         │ │  ...               │ │
│        │                     │                          │ └────────────────────┘ │
└────────┴─────────────────────┴──────────────────────────┴──────────────────────┘
   48px        300px                  flex                       320px
```

**左侧主侧栏（48px）**：仅图标 + tooltip
**ticket 列表（300px）**：smart view tab 顶部，列表每行 80px（未读圆点 + 主题 + 客户 + 时间 / 第二行 tag + priority + SLA 角标）
**对话主区（flex）**：邮件式堆叠（每条消息 = 一张展开卡片，左侧 4px 竖色条区分 INBOUND/OUTBOUND/AI/Internal），底部固定 reply box
**右栏（320px, 可折叠到 0）**：Customer / Tags / Custom Fields / AI Draft 四块自上而下

### 5.2 对话主区（邮件式堆叠）

每条消息卡片结构：

```
┌──────────────────────────────────────────────────────┐
│ │  ⬢ Alice (customer)              Email · 2h ago     │
│ │  hi, my order hasn't arrived...                     │
│ │  [📎 receipt.pdf]                                   │
└──────────────────────────────────────────────────────┘
```

- 4px 左竖条颜色：客户 = 灰色，AI = 蓝色，agent = 绿色，internal note = 黄色
- 头部：渠道 icon + 发送者 + 时间
- 内容：markdown 渲染，附件以 chip 形式
- 全宽（不区分左右）—— 邮件视觉

底部 reply box：

```
┌──────────────────────────────────────────────────────┐
│ Type your reply...                                   │
│                                                      │
├──────────────────────────────────────────────────────┤
│ 📎  ☐ Internal note      Tags ▾  Priority ▾   ⋯ More │
│                            [ Send ↵ ]  [ Solve & Send ▾ ] │
└──────────────────────────────────────────────────────┘
```

"⋯ More" 收纳：Escalate to human / Assign... / Permanently close / Mark spam / Resume AI (admin)。

### 5.3 右栏 AI Draft 协作面板

```
┌────────────────────────────────┐
│ AI Draft                   ⟲   │
│ ──────────────────────────     │
│ Confidence: 78%                │
│ Based on: 3 recent messages    │
│            + customer history  │
│                                │
│ "您好 Alice，关于您的订单     │
│  #1234，物流显示明天送达..."   │
│                                │
│ [ Use this ]  [ Edit & use ]   │
│ [ Regenerate ]                 │
└────────────────────────────────┘
```

- `Use this` → 直接发送
- `Edit & use` → 内容塞进 reply box，坐席编辑后发
- `Regenerate` → 重新调 AI（可选不同 prompt 风格：更友好 / 更正式 / 更简短）
- AI 草稿在工单状态变化时（新客户消息 / 改优先级）自动失效并重生
- `aiSuspended=true` 时整个 panel 折叠并显示 "AI suspended — manually resume from More menu"

### 5.4 Customer 侧栏

```
Customer
─────────
Alice
alice@example.com
+ phone, line, ...     ← clickable to view all identifiers

History · 3 tickets
  ✓ Login help (solved 2w ago)
  ✓ Billing question (solved 1m ago)
  ● Order issue (current)
```

点 history 链接跳到该工单。

### 5.5 Tags 编辑

输入框 type-ahead 下拉显示 TagDefinition 列表 + "Create '<input>'" 末项。回车 / 点击新建则 upsert 到 TagDefinition 表并加到 Ticket。

Tag chip 用 TagDefinition 配的颜色。

### 5.6 Custom Fields 区

按 `displayOrder` 显示，每个字段是 inline-editable（点击 → 文本框 / number / date picker），失焦自动保存。

---

## 六、AI 行为细节

### 6.1 autoReply 双层控制

- **全局开关**：Settings → General → "Auto-reply enabled" （影响所有新建 ticket 默认行为）
- **渠道开关**（已存在）：每个 Channel 的 `autoReplyEnabled` 字段；admin 可针对该 channel 单独关闭
- **决策**：`channel.autoReplyEnabled && globalAutoReply && !ticket.aiSuspended` 才会让 AI 自动直发，否则仅生成草稿到右栏面板等坐席处理

### 6.2 Escalate to Human

坐席在对话区"⋯ More"点 "Escalate to human"：
- 弹窗确认 + 可选填原因
- `Ticket.escalateToHuman(currentUserId, reason)` → `aiSuspended=true` + `escalatedAt=now` + 活动日志 `ESCALATED`
- 工单状态如果是 NEW / WAITING_ON_CUSTOMER → 自动改 OPEN
- 这之后所有 inbound 消息都不再触发 AI dispatch（在 `ProcessWebhookUseCase` 和 `AiDispatchQueueSubscriber` 双层检查 aiSuspended）
- 右栏 AI Draft panel 折叠

### 6.3 Resume AI（admin）

admin 在"⋯ More"看到 "Resume AI" 选项（普通 AGENT 看不到）：
- `Ticket.resumeAi(currentUserId)` → `aiSuspended=false` + 活动日志 `AI_RESUMED`
- 不会自动触发 AI 回复，需要等下一条 inbound

### 6.4 未读计数

简化方案：`ticket_unread` 表 `(ticket_id, user_id, unread_count)`。
- 收到 inbound 时给所有相关坐席 +1
- 坐席打开工单详情时清零
- smart view 计数 = SUM(unread_count) over 该 view 的 ticket 集合
- v2 优化为 SSE 实时增量推

---

## 七、视觉系统

### 7.1 Design Tokens

```yaml
# color
primary:    '#2962FF'
primary-fg: '#FFFFFF'
accent:     '#FF6B35'       # SLA / 错误
success:    '#16A34A'       # solved
warning:    '#F59E0B'       # waiting / internal
danger:     '#DC2626'       # closed / spam

text-1:     '#0B1426'       # 主文本
text-2:     '#5A6B7D'       # 次要文本
text-3:     '#9BAAB8'       # 三级 / placeholder

bg-1:       '#FFFFFF'       # 主背景
bg-2:       '#F7F9FB'       # hover / 二级背景
bg-3:       '#EEF2F6'       # 边线 / 分隔
sidebar-bg: '#0B1426'       # 主侧栏深色

# typography
font:       -apple-system, 'SF Pro Text', '-system-ui-fallback'...
size-sm:    12px
size-base:  13px            # 默认正文（antd 默认 14 偏大）
size-md:    15px
size-lg:    20px            # h2
size-xl:    24px            # h1

# spacing
radius-sm:  4px
radius-md:  6px             # 全局组件默认
radius-lg:  12px            # 邮件卡片
density:    line-height 1.5, button-h 28px (antd default 32)
```

### 7.2 antd 主题覆盖

```ts
const theme = {
  token: {
    colorPrimary: '#2962FF',
    colorSuccess: '#16A34A',
    colorWarning: '#F59E0B',
    colorError:   '#DC2626',
    fontSize:     13,
    borderRadius: 6,
    colorBgLayout: '#F7F9FB',
    colorTextBase: '#0B1426',
  },
  components: {
    Card: { paddingLG: 16, headerBg: 'transparent' },
    Table: { rowHoverBg: '#F7F9FB' },
    Button: { controlHeight: 28 },
    Tag: { defaultBg: '#EEF2F6' },
  },
};
```

### 7.3 减法清单

- 删 antd Card 默认 shadow（只保留 1px 边或不要边）
- 删手动 "Refresh" 按钮（SSE 实时刷新）
- 删 status 单独 Tag → 用左竖线 / 行内小圆点
- 删 Reports 占位卡片（直接显示 "Coming soon"，但保留 nav 入口）
- 删 antd 默认页面 header → 用自定义 48px 顶栏

### 7.4 暗色模式（可选）

v1 不做。但 design token 命名保持 semantic（如 `text-1` 而不是 `gray-900`），后续切暗色只需换 token 值，不需要改组件。

---

## 八、不在本次范围

明确"知道但不做"的事，避免 scope creep：

- 工单 merge / link（slice 8 已有 parentTicketId for spawn，merge 后续）
- 业务时间日历（SLA 不计算工作日 vs 假日）
- 出站通知（Slack / 邮件 SLA 提醒）
- Macros / 预设回复模板
- CSAT 满意度调查
- 全文搜索（继续 LIKE）
- 暗色模式实施
- 移动端 / 响应式
- Reports 真实数据（保持占位）
- 工单 SPAM 自动检测
- AI knowledge base / RAG
- OAuth 各平台流程

---

## 九、实施分片建议

### Slice 12 · 领域增量（约 1.5 天）

- 新增表与实体：customer, customer_identifier, tag_definition, custom_field_definition
- Ticket 字段：customerId / aiSuspended / escalatedAt / customFields
- CustomerLookupService + 改造 ProcessWebhookUseCase
- ESCALATED / AI_RESUMED 活动 action
- 单元测试 + repository 测试

### Slice 13 · admin REST 与 settings（约 1 天）

- TagDefinitionAdminController
- CustomFieldDefinitionAdminController
- AIConfig 加 `global.autoReplyEnabled` 字段
- CustomerAdminController（list / detail / search by identifier）
- 端到端测试

### Slice 14 · 前端 IA 重构（约 2 天）

- 主 nav 改 3 项（Inbox / Reports / Settings）
- 合并 Inbox + Desk 为统一 Inbox 三栏页
- 左栏 smart view + 二级状态筛选
- AppLayout 改 48px 主侧栏 + 极简 header
- design token 全局应用 + antd theme

### Slice 15 · 对话主区与 AI 协作面板（约 2 天）

- 邮件式堆叠重做（删气泡气泡 component）
- AI Draft panel（生成 + use / edit / regenerate）
- Customer 侧栏 / Tags / Custom Fields 区
- Escalate to human / Resume AI 操作

### Slice 16 · Settings 新增页（约 1 天）

- Tags 管理页
- Custom Fields schema 管理页
- General 设置页（autoReply 全局开关等）
- 现有 Channels / AI / Users / SLA / Automation 视觉对齐

### Slice 17 · 视觉打磨与未读计数（约 1 天）

- ticket_unread 表 + 更新逻辑
- 侧栏 smart view 未读徽章
- 主侧栏图标 + tooltip
- 整体边距 / 字体 / 颜色 review，对照 design token 清理 inline style

**估算总工时**：~8-9 个工作日（沿用 Opus 编排 + Sonnet 实现模式，每个 slice 单独提交并 docker 重启）。

---

## 十、验收标准

- 全部 backend 测试通过（slice 11 = 249，目标 slice 17 ≥ 290）
- `pnpm typecheck` clean
- 三栏页面在 1440px 宽下信息密度合适，1024px 宽下右栏自动折叠
- Customer 自动聚合：同一邮箱在 ZENDESK 和 CUSTOM 两个 channel 出现，能看到一个 Customer 关联两个 ticket
- 转人工后该 ticket 后续 inbound 不触发 AI
- 标签从 TagDefinition 库下拉选择，新输入会自动入库
- 视觉对照 §7 设计 token 一致（用截图肉眼校验）
