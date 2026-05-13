# Autotix 设计文档

**日期**：2026-05-12  
**状态**：草稿，待实施计划

---

## 一、产品定位

Autotix 是一个**自托管的开源 AI 工单自动化平台**。

**核心价值主张**：连接任意客服/电商平台，接入任意 AI，自动完成"收消息 → AI 处理 → 回写平台"全链路。单机可用，可扩展至分布式。

**目标用户**：有技术能力的中小团队，想自建 AI 客服自动化，不想从零对接 20 个平台 API。

**差异化定位**（对比最接近的竞品 Chatwoot）：
- AI 是一等公民，整个引擎围绕"自动处理"设计，而非"给人工坐席看"
- 内置 20+ 平台适配器，包含亚洲平台（LINE、WeCom、国内 ERP）
- BYOAI：填 3 个字段接入任意 OpenAI-compatible AI，包括本地 LLM
- 生产验证：适配器来自 shulex-intelli 真实运行的代码，不是 Demo

### 范围边界

**包含**：
- 多渠道 Webhook 统一接收 + 标准化
- AI 自动回复（BYOAI）
- 人工坐席工单管理界面
- 自动化规则（分配、标签、关闭）
- 平台授权 / OAuth 管理
- 基础数据报表

**不包含（第一版）**：
- 多租户 / SaaS 计费
- 语音渠道（Freshcaller 等）
- 多语言 SDK（提供 HTTP 直调接口即可）
- 电商数据同步（订单/产品 Sync）

---

## 二、仓库结构

```
autotix/
├── autotix-server/              ← Spring Boot 主服务（JDK 8 + Spring Boot 2.4.13，对齐 shulex-intelli）
│   ├── domain/                  ← 领域层（核心业务，无外部依赖）
│   ├── application/             ← 应用层（用例编排）
│   ├── infrastructure/          ← 基础设施层（平台适配器、DB、AI 客户端、auth）
│   └── interfaces/              ← 接口层（REST Controller、Webhook 入口、SSE）
├── autotix-web/                 ← React SPA（UmiJS + antd v5）
│   ├── Login/                   ← 登录
│   ├── Desk/                    ← 工单管理
│   ├── Inbox/                   ← AI 对话收件箱（SSE 实时）
│   ├── Settings/                ← 渠道连接 + AI 配置 + 用户管理
│   └── Reports/                 ← 数据看板
├── docker-compose.yml           ← 一键启动（app + db）
└── docs/                        ← 接入文档
```

### 技术栈（对齐 shulex-intelli `com.shulex:parent:2.0.2-RELEASE`）

| 项 | 版本 / 选型 |
|---|---|
| JDK | 1.8 |
| Spring Boot | 2.4.13 |
| Spring Framework | 5.3.27 |
| 持久化 | MyBatis Plus 3.5.3.1（不用 JPA） |
| 工具库 | Lombok 1.18.22 / Fastjson 1.2.83 / Hutool 5.8.18 / OkHttp 4.9.3 |
| 安全 | Spring Security 5.x + jjwt 0.11.5（JWT 无状态） |
| Markdown | flexmark 0.62.x（Java 8 兼容线） |
| 缓存 | Caffeine 2.9.x（Java 8 兼容线） |

> ⚠️ 开源版不依赖 shulex 私有 nexus；parent 用 `spring-boot-starter-parent:2.4.13`。所有版本与 intelli 对齐，便于代码迁移。

### 架构选型：DDD 分层 + 单体 Spring Boot

后端采用 DDD 四层架构，便于后续按限界上下文拆分微服务或替换某层实现：

```
autotix-server/
├── domain/                          ← 领域层（纯业务逻辑，零外部依赖）
│   ├── ticket/                      ← Ticket 限界上下文
│   │   ├── Ticket.java              ← 聚合根（状态机：open/pending/closed）
│   │   ├── Message.java             ← 值对象
│   │   ├── TicketStatus.java
│   │   ├── TicketRepository.java    ← 仓储接口（domain 定义，infra 实现）
│   │   └── TicketDomainService.java ← 领域服务（跨聚合操作）
│   ├── channel/                     ← Channel 限界上下文
│   │   ├── Channel.java             ← 聚合根（平台连接实例）
│   │   ├── ChannelCredential.java   ← 值对象（OAuth token 等）
│   │   ├── ChannelType.java         ← 枚举（Email / Chat）
│   │   └── ChannelRepository.java
│   ├── ai/                          ← AI 调用领域服务
│   │   ├── AIReplyPort.java         ← 端口接口（infra 实现）
│   │   └── AIResponse.java          ← 值对象（reply / action / tags）
│   ├── user/                        ← User 限界上下文
│   │   ├── User.java                ← 聚合根
│   │   ├── UserRole.java            ← 枚举（ADMIN / AGENT / VIEWER）
│   │   └── UserRepository.java
│   └── event/                       ← 跨上下文事件
│       ├── TicketEvent.java         ← Webhook 标准化事件
│       └── InboxEvent.java          ← SSE 推送事件
│
├── application/                     ← 应用层（用例编排，调用 domain + port）
│   ├── ticket/
│   │   ├── ProcessWebhookUseCase.java   ← 接收 Webhook → 创建/更新 Ticket
│   │   ├── DispatchAIReplyUseCase.java  ← 调用 AI → 回写平台
│   │   └── CloseTicketUseCase.java
│   └── channel/
│       ├── ConnectChannelUseCase.java   ← OAuth 授权流程
│       └── DisconnectChannelUseCase.java
│
├── infrastructure/                  ← 基础设施层（实现 domain 定义的接口）
│   ├── persistence/                 ← 仓储实现（MyBatis Plus，对齐 intelli）
│   ├── ai/                          ← AIReplyPort 实现（HTTP → OpenAI-compatible）
│   ├── auth/                        ← JWT + Spring Security + 密码哈希 + Bootstrap admin
│   ├── inbox/                       ← InboxEventPublisher（SSE 扇出）
│   ├── platform/                    ← 平台适配器（防腐层）
│   │   ├── zendesk/                 ← ZendeskPlugin（Webhook 解析 + 回写）
│   │   ├── freshdesk/
│   │   ├── line/
│   │   ├── shopify/
│   │   └── wecom/
│   └── infra/                       ← 可替换基础设施实现
│       ├── lock/                    ← LockProvider（内存 / Redis）
│       ├── queue/                   ← QueueProvider（内存 / Kafka）
│       ├── cache/                   ← CacheProvider（Caffeine / Redis）
│       └── idempotency/             ← IdempotencyStore（基于 CacheProvider）
│
└── interfaces/                      ← 接口层（HTTP 入口）
    ├── webhook/                     ← POST /v2/webhook/{platform}/{token}
    ├── desk/                        ← 人工坐席 REST API
    ├── inbox/                       ← GET /api/inbox/stream（SSE）
    ├── auth/                        ← /api/auth/login | refresh | me | password
    └── admin/                       ← 平台授权 + AI 设置 + 用户管理 API
```

**DDD 关键约束**：
- `domain/` 不依赖任何 Spring Bean、JPA、HTTP 库，可独立单元测试
- `application/` 只依赖 `domain/`，通过端口接口（Port）调用外部能力
- `infrastructure/` 实现端口接口，是唯一允许引入第三方 SDK 的层
- 平台适配器（Plugin）作为**防腐层（ACL）**，隔离三方 API 变化对 domain 的影响

### 代码来源

| autotix-server 层 | 来源 |
|---|---|
| `domain/ticket` | Tars 工单核心模型（去多租户，提取业务规则） |
| `domain/channel` | shulex-intelli ExternKey/ChannelAuth 领域模型（精简） |
| `application/` | Tars 服务层（重构为 UseCase 风格） |
| `infrastructure/platform/` | shulex-intelli 各平台适配器（去 Nacos/Feign） |
| `infrastructure/ai/` | 新增，替换 Tars 原有 Feign 调用 |
| `interfaces/` | shulex-intelli + Tars Controller 层（合并简化） |

| autotix-web 模块 | 来源 |
|---|---|
| `Desk/` `Inbox/` `Reports/` | plg-help-desk（去 qiankun + 换 antd） |
| `Settings/` | 新增，平台连接向导 + AI 配置页 |

---

## 三、核心处理流程

```
三方平台
    │  Webhook POST /v2/webhook/{platform}/{token}
    ▼
[Integration Layer]
  ├── 路由到对应 Platform Plugin
  ├── 签名验证（平台特定算法）
  ├── 解析为标准 TicketEvent
  └── 幂等去重（ticketId + 平台）
    │
    ▼
[Ticket Engine]
  ├── 路由判断
  │   ├── 命中自动化规则 → 直接执行（tag / assign / close）
  │   ├── AI 自动处理模式 → 调用 AI
  │   └── 人工队列模式 → 推送到坐席收件箱
  ├── AI 调用（见第四节）
  └── 回写三方平台（reply / tag / close）
    │
    ▼
[Desk API]
  └── 人工坐席可随时查看、接管、手动回复
```

---

## 四、AI 接入接口

### 配置（管理界面填写）

```yaml
ai:
  endpoint: https://api.openai.com/v1   # 任意 OpenAI-compatible 地址
  api_key: sk-xxx                        # 或 Anthropic / 本地 Ollama 等
  model: gpt-4o                          # 模型名称
  system_prompt: |                       # 可自定义 prompt 模板
    你是一个专业的客服助手，请用礼貌简洁的语言回复客户问题。
```

兼容目标：OpenAI、Claude（via proxy）、Gemini、Ollama 本地模型。

### Autotix 发给 AI 的请求

```json
{
  "model": "gpt-4o",
  "messages": [
    { "role": "system", "content": "{{system_prompt}}" },
    { "role": "user",   "content": "渠道：{{channelType}}\n客户：{{customerName}}\n\n最新消息：{{latestMessage}}\n\n历史记录：{{conversationHistory}}" }
  ]
}
```

### AI 返回格式

Autotix 期望 AI 返回 JSON（兼容纯文本回退）：

```json
{
  "reply": "您好，您的订单 #1234 已发货，预计 3 天内到达。",
  "action": "close",
  "tags": ["已处理", "物流"]
}
```

- `reply`：必填，回复内容（Markdown 格式）
- `action`：可选，`close` / `assign` / `tag` / `none`
- `tags`：可选，要打的标签列表

AI 返回纯字符串时，视为 `reply` 内容，向后兼容。

---

## 五、渠道大类与格式转换

### 渠道大类（Channel Type）

渠道大类不绑定平台，而是绑定**集成实例**。每个 Platform Plugin 声明自己属于哪个大类：

```java
interface TicketPlatformPlugin {
    ChannelType channelType();  // Email / Chat（/ Voice，暂不支持）
}
```

| 平台 | 集成实例 | 渠道大类 |
|---|---|---|
| Zendesk | Zendesk Tickets | Email |
| Zendesk | Zendesk Sunshine | Chat |
| Freshworks | Freshdesk | Email |
| Freshworks | Freshchat | Chat |
| LINE | LINE Messaging | Chat |
| WhatsApp | WhatsApp Business | Chat |
| WeCom | 企业微信 | Chat |
| Amazon | Amazon Buyer Messages | Email |
| Shopify | Shopify Email | Email |
| Gmail / Outlook | 邮件 | Email |
| Gorgias | Gorgias Tickets | Email |
| Intercom | Intercom Chat | Chat |

### 格式转换流程

AI 始终返回 Markdown，Autotix 在回写平台前按渠道大类转换：

```
AI 回复（Markdown）
    │
    ▼
ReplyFormatter（按 channelType）
  ├── Email → Markdown 转 HTML
  └── Chat  → 去除 Markdown 符号，转纯文本
    │
    ▼
Platform Adapter.sendReply()  ← 各平台处理最终 API 调用
```

新增平台只需在 Plugin 里声明 `channelType()`，格式转换自动继承。

---

## 五点五、用户体系 & 鉴权

### 角色

| 角色 | 权限 |
|---|---|
| `ADMIN` | 全部：渠道、AI、用户、Automation Rule、Reports |
| `AGENT` | Desk + Inbox + Reports；可处理 / 回复 / 接管 / 关闭工单 |
| `VIEWER` | 只读：可看工单和报表，不可变更 |

### 鉴权

- **JWT 无状态**：登录返回 `accessToken`（默认 60 分钟）+ `refreshToken`（30 天）
- **Spring Security 5.x** + 自定义 `JwtAuthenticationFilter`
- **路由权限**：
  ```
  /v2/webhook/**       permitAll（依赖签名验证）
  /api/auth/**         permitAll
  /api/admin/**        ROLE_ADMIN
  /api/desk/**         ROLE_ADMIN | ROLE_AGENT
  /api/inbox/**        ROLE_ADMIN | ROLE_AGENT
  /api/reports/**      ROLE_ADMIN | ROLE_AGENT | ROLE_VIEWER
  ```
- **Bootstrap Admin**：首次启动若库内无任何 ADMIN，按 `autotix.auth.bootstrap-admin-*` 自动建一个，日志 WARN 提示改密
- **不可降级最后一位管理员**：`UpdateUserRoleUseCase` 和 `DisableUserUseCase` 强制校验 `countByRole(ADMIN) > 1`

---

## 五点六、Inbox 实时推送：SSE

### 为什么选 SSE 而不是 WebSocket

| 维度 | Inbox 实际情况 | 选型结论 |
|---|---|---|
| 流向 | 主要服务端推送（新工单 / AI 回复完成 / 客户回复 / 状态变更） | 单向流 SSE 更合身 |
| 客户端动作 | 接管、回复、关闭都是离散命令 | 走普通 REST POST 即可 |
| 协议复杂度 | 不需要双向、不需要二进制 | SSE 是 HTTP/1.1 原生，零运维负担 |
| 重连 | EventSource 内置自动重连 | 无需自己实现 |
| 反代 | Nginx/CloudFront 都默认支持 | 不用特殊配置 |

### 流程

```
[Application Layer]
  any UseCase that mutates ticket state
        │
        │  publish(InboxEvent)
        ▼
[InboxEventPublisher] ←─── (kafka topic "inbox.events" in distributed mode)
        │
        │  fan out to local SseEmitter instances
        ▼
[Browser EventSource] /api/inbox/stream?token=...
        │
        ▼
  React Inbox 页面 onmessage → setState
```

### 协议

```
GET /api/inbox/stream
Accept: text/event-stream

event: TICKET_CREATED
data: {"ticketId":"123","channelId":"ch_1","summary":"...","occurredAt":"..."}

event: AI_REPLIED
data: {...}

: ping        ← 每 25s 心跳（注释行），保活
```

事件种类：`TICKET_CREATED / AI_REPLIED / AGENT_REPLIED / STATUS_CHANGED / ASSIGNED`。

---

## 六、基础设施扩展架构

所有基础设施能力通过接口抽象，内置轻量实现，用户可替换为分布式组件：

| 接口 | 默认实现（零外部依赖） | 可替换为 |
|---|---|---|
| `LockProvider` | JVM 内存锁 | Redis / Zookeeper |
| `QueueProvider` | 同步内存队列 | Kafka / RabbitMQ / RocketMQ |
| `CacheProvider` | Caffeine 本地缓存 | Redis |
| `StorageProvider` | H2 嵌入式数据库 | MySQL / PostgreSQL |
| `WebhookStore` | 数据库存储 | S3 / OSS（大 payload） |

切换方式：修改 `application.yml`，不改应用代码。

### 部署形态

**单机（默认）**：
```bash
docker compose up
```

**分布式（生产）**：
```yaml
# application.yml
autotix:
  lock: redis
  queue: kafka
  storage: mysql
```

---

## 七、前端迁移要点

从 `plg-help-desk` 迁移到 `autotix-web` 的关键改动：

| 改动项 | 现状 | 目标 |
|---|---|---|
| 微前端框架 | qiankun 子应用 | 独立 SPA，自带登录和路由入口 |
| 组件库 | `@shulex/design`（内部，203 个文件） | antd v5（find-replace，机械性工作） |
| 图标库 | `@shulex/icons`（内部，153 个文件） | `@ant-design/icons`（对齐命名差异） |
| API 层 | 调内部服务接口 | 对齐 autotix-server REST API |
| 多租户 | tenantId 贯穿全局 | 去除，单工作空间 |
| 新增页面 | 无 | Settings（渠道连接向导 + AI 配置） |

---

## 八、开源策略

**许可证**：AGPL-3.0（与 Chatwoot 相同）
- 修改后必须开源，防止被直接封装为商业产品
- 提供官方 Cloud 托管版可合法商业化
- 对个人/团队自用无限制

**竞品对比**：

| | Chatwoot | Autotix |
|---|---|---|
| AI 自动回复 | 后补插件 | 核心链路 |
| 平台覆盖 | 20 个，亚洲平台弱 | 20+，含 LINE/WeCom/国内 ERP |
| AI 接入 | 需自己写集成 | 3 个字段，OpenAI-compatible |
| 部署 | Docker Compose | Docker Compose |

---

## 八点五、测试

### 后端

- **JUnit 5 + Mockito**（Spring Boot 2.4 默认带）
- **分层测试**：
  - `domain/**Test` — 纯 JUnit，无 Spring，零 IO
  - `application/**Test` — JUnit + Mockito，所有 port 都 mock
  - `infrastructure/**Test` — `@SpringBootTest` + `@ActiveProfiles("test")` + H2
  - `interfaces/**Test` — `@WebMvcTest`（切片）或 `@SpringBootTest` + `TestRestTemplate`
- **Webhook 解析测试**：用 `src/test/resources/fixtures/{platform}/*.json` 真实抓包样本（脱敏后）
- 配置：`src/test/resources/application.yml`（H2 + 测试 JWT secret + 随机端口）

### 前端

- **Vitest + @testing-library/react + jsdom**
- 页面 smoke test + service 函数 unit test
- 配置：`vitest.config.ts`、setup：`src/test/setup.ts`

---

## 九、实施阶段（待 writing-plans 细化）

**阶段 1：后端骨架（约 2 周）**
- 从 shulex-intelli 提取 integration 层（去 Nacos/Feign）
- 从 Tars 提取 engine 层（去多租户，替换 AI 调用）
- 实现基础设施接口抽象（Lock/Queue/Cache/Storage）
- Docker Compose 可启动，Webhook 可接收

**阶段 2：前端迁移（约 2 周）**
- plg-help-desk 去 qiankun，独立 SPA
- `@shulex/design` → antd v5
- 新增 Settings 页（渠道授权向导 + AI 配置）

**阶段 3：平台适配器移植（约 1-2 周）**
- 优先移植：Zendesk、Freshdesk、Shopify、LINE、WeCom
- 验证端到端流程（Webhook 入 → AI 处理 → 回写）

**阶段 4：完善与发布（约 1 周）**
- 完整 Docker Compose + 环境变量文档
- Demo 网站（autotix.dev）
- GitHub README + 快速开始指南
- 开源发布
