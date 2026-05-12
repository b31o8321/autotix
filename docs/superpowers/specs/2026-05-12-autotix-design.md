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
├── autotix-server/              ← Spring Boot 主服务（Java 8 + Spring Boot 3）
│   ├── integration/             ← Webhook 入口 + 平台适配器
│   ├── engine/                  ← 工单引擎 + AI 调度
│   ├── desk/                    ← 人工坐席 REST API
│   └── admin/                   ← 平台授权配置 + AI 设置 API
├── autotix-web/                 ← React SPA（UmiJS + antd）
│   ├── Desk/                    ← 工单管理
│   ├── Inbox/                   ← AI 对话收件箱
│   ├── Settings/                ← 渠道连接 + AI 配置
│   └── Reports/                 ← 数据看板
├── docker-compose.yml           ← 一键启动（app + db）
└── docs/                        ← 接入文档
```

### 架构选型：单体 Spring Boot

选择单体而非微服务，原因：
1. 现有代码（shulex-intelli + Tars）本身是单体，迁移路径最短
2. Docker Compose 单机部署零门槛，符合"5 分钟跑起来"目标
3. 基础设施接口抽象保留分布式扩展能力，用户可自行替换组件

### 代码来源

| autotix-server 模块 | 来源 |
|---|---|
| `integration/` | shulex-intelli 集成适配层（去除 Nacos/Feign 依赖） |
| `engine/` | Tars 工单引擎（去除多租户逻辑，替换 AI 调用） |
| `desk/` | Tars Desk API（简化为单工作空间） |
| `admin/` | 新增，OAuth 配置 + AI 设置 |

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
