---
title: 集成管理
sidebar_label: 集成管理
---

# 集成管理（推送 / 拉取）

集成管理是消息中心提供的对外系统集成能力，用于把「外部系统」与消息中心打通。按数据流向分为两类：

- **推送集成（Push）**：外部系统主动调用消息中心入队（即 [消息入队接入](./integration.md)）。集成管理在此负责**登记外部系统**，并可选地结合认证中心对入队请求做身份校验。
- **拉取集成（Pull）**：消息中心按配置主动从外部数据源（JDBC）周期性拉取数据，拉到的每一行自动转成一条消息并入队，走与推送一致的统一分发链路。

> 前置阅读：建议先了解 [消息中心总览](./overview.md) 的路由与消息类型概念。

## 1. 能力总览

| 维度 | 推送集成（Push） | 拉取集成（Pull） |
| :--- | :--- | :--- |
| 数据流向 | 外部系统 → 消息中心 | 外部数据源 → 消息中心 |
| 触发方 | 外部业务系统主动调用 | 消息中心定时调度 |
| 登记对象 | 外部系统（systemCode / clientId） | JDBC 拉取源（连接串 + SQL + 模板） |
| 凭据来源 | 复用认证中心 `oauth_client`，不在本中心存 secret | 本地存储 DB 密码（AES-GCM 密文 + UI 脱敏） |
| 落库方式 | 调用 `/message/enqueue` 入 `platform_msg` | 拉取行渲染后入 `platform_msg` |
| 鉴权 | 可选，结合认证中心校验 token | 不涉及（中心主动外呼） |

两类集成都在运维管理界面「**集成管理**」菜单中维护。

## 2. 推送集成：外部系统登记

### 2.1 与认证中心的关系

推送集成登记的 `clientId` / `systemCode` **复用统一认证服务**（geelato-auth）中已登记的 `oauth_client`，消息中心**不重复存储 client secret**。

- `systemCode`：外部系统的唯一编码，与认证中心 `oauth_client.system_code` 对应。
- `clientId`：认证中心签发的客户端 ID。在新增/编辑界面可从认证中心拉取可选 client（需配置 `geelato.message.integration.auth-base-url`）。
- 入队时若开启鉴权，消息中心会调用认证中心校验调用方携带的 access_token，校验通过后把消息的 `sourceSystem` 回填为该系统的 `systemCode`。

### 2.2 登记字段（`platform_msg_integration_system`）

| 字段 | 是否必填 | 说明 |
| :--- | :--- | :--- |
| `systemCode` | **是** | 系统编码（唯一），对应认证中心 `oauth_client.system_code`。 |
| `systemName` | **是** | 系统名称，便于辨识。 |
| `clientId` | 否 | 认证中心 clientId（引用，不存 secret）。 |
| `authType` | 否 | 认证方式：`oauth_token` / `api_key` / `none`，默认 `none`。 |
| `authVerifyEnabled` | 否 | 入队是否校验 token：`1` 校验 / `0` 不校验，默认 `0`。 |
| `contact` | 否 | 联系人/负责人。 |
| `enableStatus` | 否 | 启用状态：`1` 启用 / `0` 禁用。 |
| `remark` | 否 | 备注。 |

### 2.3 可选的入队鉴权

默认情况下 `/message/enqueue` 不校验调用方身份（保持向后兼容）。当需要对接认证中心做身份校验时：

1. 配置认证中心地址：`geelato.message.integration.auth-base-url=http://<auth-server>`
2. 开启鉴权：`geelato.message.integration.auth-verify-enabled=true`
3. 调用方在入队请求中携带请求头：
   - `X-Client-Id: <clientId>`
   - `Authorization: Bearer <accessToken>`

校验通过后，消息的 `sourceSystem` 会被回填为该 `clientId` 对应登记系统的 `systemCode`；校验失败返回 `401`。

```bash
curl -X POST "http://localhost:8086/message/enqueue" \
  -H "Content-Type: application/json" \
  -H "X-Client-Id: order-center" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "type": "sms",
    "content": "您的订单已发货。",
    "bizKey": "order-1001-sms",
    "receiver": "{\"type\":\"mobilePhone\",\"list\":[\"13800138000\"]}"
  }'
```

> 未携带鉴权头或鉴权总开关关闭时，入队按原逻辑放行，不影响存量调用方。

## 3. 拉取集成：JDBC 拉取源

### 3.1 工作机制

1. **统一调度**：消息中心按全局统一频率（`geelato.message.integration.pull.interval-seconds`，默认 30 秒）扫描所有**启用**的拉取源。
2. **执行拉取**：对每个源以其配置的 JDBC 连接串建立短连接，执行 `pullSql`（增量模式注入上次水位 `:last_pull_at`）。
3. **模板渲染**：对结果集每一行，用 `target_*` 模板渲染出消息标题、内容、接收人。
4. **入队分发**：渲染后的消息调用与推送相同的入队逻辑落入 `platform_msg`，复用幂等键（`pull:<sourceCode>:<主键>`）去重、路由、重试与归档。
5. **记录水位**：更新该源的 `last_pull_at` / `last_pull_status` / `last_pull_count`，供运维查看与增量使用。

> 拉取频率由消息中心统一管理，单个拉取源只能启用/禁用，不能自定义频率。

### 3.2 拉取源字段（`platform_msg_pull_source`）

| 字段 | 是否必填 | 说明 |
| :--- | :--- | :--- |
| `sourceCode` | **是** | 拉取源编码（唯一）。 |
| `sourceName` | **是** | 拉取源名称。 |
| `jdbcUrl` | **是** | 外部数据源 JDBC 连接串。 |
| `dbUsername` | 否 | DB 用户名。 |
| `dbPassword` | 否 | DB 密码（界面填写，落库为 AES-GCM 密文，回显脱敏）。 |
| `dbDriver` | 否 | JDBC 驱动类，默认 `com.mysql.cj.jdbc.Driver`。 |
| `pullSql` | **是** | 拉取 SQL，支持 `:last_pull_at` 占位（增量）。 |
| `incrementalFlag` | 否 | 是否增量：`1` 增量 / `0` 全量，默认 `0`。 |
| `targetMsgType` | **是** | 拉取行转成的消息类型：`sms` / `email` / `bot` 等。 |
| `targetReceiver` | 否 | 目标接收人 JSON，支持 `${col}` 占位。 |
| `targetBuss` | 否 | 业务标识。 |
| `targetTitleTemplate` | 否 | 标题模板，支持 `${col}` 占位。 |
| `targetContentTemplate` | 否 | 内容模板，支持 `${col}` 占位；留空时把整行 JSON 作为内容。 |
| `enableStatus` | 否 | 启用状态：`1` 启用 / `0` 禁用。 |
| `lastPullStatus` | — | 最近拉取状态：`idle` / `running` / `success` / `fail`（系统维护）。 |
| `lastPullAt` | — | 上次拉取水位（系统维护，增量用）。 |
| `lastPullCount` | — | 最近一次拉取行数（系统维护）。 |

### 3.3 模板与占位语法

- **行字段占位**：在 `targetTitleTemplate` / `targetContentTemplate` / `targetReceiver` 中使用 `${列名}`，拉取时按当前行的列值替换。例如 SQL 返回 `order_no`、`mobile` 列，则模板可写 `订单 ${order_no} 已发货，请联系 ${mobile}`。
- **增量水位占位**：在 `pullSql` 中使用 `:last_pull_at`，启用增量后系统会自动绑定上次拉取时间。例如：
  ```sql
  SELECT id, order_no, mobile FROM orders WHERE update_at > :last_pull_at
  ```
- **幂等去重**：每行入队使用 `pull:<sourceCode>:<主键列值>` 作为业务键，主键列按 `id` / `*_id` / `*_no` / `*_code` / `biz_key` 顺序识别；全量模式下重复拉取不会产生重复消息。

### 3.4 完整示例

**场景**：每 30 秒从订单库拉取近期待通知订单，发短信给客户。

拉取源配置：

| 配置项 | 值 |
| :--- | :--- |
| sourceCode | `order-alert` |
| jdbcUrl | `jdbc:mysql://10.0.0.5:3306/orders?useSSL=false` |
| incrementalFlag | `1` |
| pullSql | `SELECT id, order_no, mobile FROM orders WHERE update_at > :last_pull_at AND notify_flag = 0` |
| targetMsgType | `sms` |
| targetReceiver | `{"type":"mobilePhone","list":["${mobile}"]}` |
| targetTitleTemplate | `订单 ${order_no} 通知` |
| targetContentTemplate | `您的订单 ${order_no} 状态已更新，请及时查看。` |
| targetBuss | `order` |

拉取到一行 `{id: 8821, order_no: "SO2026080101", mobile: "13800138000"}` 后，渲染并入队的消息等价于：

```json
{
  "type": "sms",
  "title": "订单 SO2026080101 通知",
  "content": "您的订单 SO2026080101 状态已更新，请及时查看。",
  "bizKey": "pull:order-alert:8821",
  "sourceSystem": "pull:order-alert",
  "buss": "order",
  "receiver": "{\"type\":\"mobilePhone\",\"list\":[\"13800138000\"]}"
}
```

该消息随后按 `sms` 类型走与推送完全一致的路由与发送链路。

## 4. 配置项

在 `application.properties` 中配置（均带默认值，开箱即用）：

| 配置项 | 默认值 | 说明 |
| :--- | :--- | :--- |
| `geelato.message.integration.enabled` | `true` | 集成管理总开关。关闭后调度与鉴权不生效（登记/查询仍可用）。 |
| `geelato.message.integration.pull.enabled` | `true` | 拉取调度开关。 |
| `geelato.message.integration.pull.interval-seconds` | `30` | 全局统一拉取间隔（秒）。 |
| `geelato.message.integration.pull.max-rows` | `1000` | 单次拉取最大行数（0 表示不限）。 |
| `geelato.message.integration.auth-base-url` | （空） | 认证中心地址，配置后启用 oauth client 透传与入队 token 校验。 |
| `geelato.message.integration.auth-verify-enabled` | `false` | 入队是否校验 access_token。 |
| `geelato.message.integration.crypto-key` | 内置默认 | 凭据加解密密钥，**生产环境务必通过环境变量 `GEELATO_MESSAGE_INTEGRATION_CRYPTO_KEY` 覆盖**。 |

## 5. REST 接口速查

所有集成管理接口统一前缀 `/message/ops/integration`，返回统一信封 `{code, msg, data, total, page, size}`。

### 5.1 推送系统

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| `GET` | `/systems` | 分页查询推送系统（参数 `keyword` / `authType` / `enableStatus` / `page` / `size`）。 |
| `GET` | `/systems/{id}` | 推送系统详情。 |
| `POST` | `/systems` | 新增推送系统。 |
| `PUT` | `/systems/{id}` | 更新推送系统。 |
| `POST` | `/systems/{id}/enable` | 启用。 |
| `POST` | `/systems/{id}/disable` | 禁用。 |
| `DELETE` | `/systems/{id}` | 删除（逻辑删除）。 |
| `GET` | `/auth-clients` | 透传认证中心 oauth client 列表，供选择 clientId。 |

### 5.2 拉取源

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| `GET` | `/pull-sources` | 分页查询拉取源（参数 `keyword` / `enableStatus` / `lastPullStatus` / `page` / `size`）。 |
| `GET` | `/pull-sources/{id}` | 拉取源详情（密码脱敏）。 |
| `POST` | `/pull-sources` | 新增拉取源。 |
| `PUT` | `/pull-sources/{id}` | 更新拉取源（密码留空表示不修改）。 |
| `POST` | `/pull-sources/{id}/enable` | 启用。 |
| `POST` | `/pull-sources/{id}/disable` | 禁用。 |
| `DELETE` | `/pull-sources/{id}` | 删除（逻辑删除）。 |
| `POST` | `/pull-sources/{id}/test?sample=3` | 立即试拉一次（不落库），返回行数与前 3 行样例。 |
| `POST` | `/pull-sources/{id}/run` | 立即执行一次拉取并落消息。 |

## 6. 运维 UI 操作指引

进入运维管理界面左侧菜单「**集成管理**」，页面以两个 Tab 组织：

- **推送系统**：列表支持按编码/名称/clientId 检索；新增/编辑弹窗中 `clientId` 下拉来自认证中心；可启用/禁用/删除。开启「入队鉴权」开关后该系统入队需带 token。
- **拉取源**：列表展示编码、名称、JDBC、最近拉取状态/行数/时间；新增/编辑弹窗配置连接信息、拉取 SQL 与目标消息模板；密码框编辑时留空表示不修改。行操作提供：
  - **测试**：立即试拉，弹窗展示行数与样例数据，便于调试 SQL 与模板，**不产生消息**。
  - **立即执行**：立即拉取一次并落库消息，用于临时触发或验证。

> 正式启用前请先执行建表脚本 `platform_msg_integration.sql`（位于 `geelato-message` 模块根目录）。
