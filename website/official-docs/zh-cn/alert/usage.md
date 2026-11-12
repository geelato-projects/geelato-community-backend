---
title: 业务告警规则与使用
sidebar_label: 规则与使用
---

# 业务告警规则与使用

本页说明告警规则/模板/静默的配置方法与可用 REST API。总览见[业务告警](overview.md)。

## 启用步骤

1. 部署 geelato-message（默认端口 8086），确认 enqueue 契约可达（见消息集成指南附录 C）；
2. 引入 `geelato-alert` 依赖，配置引擎：

```properties
geelato.alert.engine.enabled=true
geelato.alert.engine.message-base-url=http://127.0.0.1:8086
geelato.alert.engine.message-system-code=xxx
geelato.alert.engine.message-api-key=xxx
# 可选：接收人求解兜底角色
geelato.alert.engine.fallback-role-code=
```

3. 启用异常引擎（`geelato.exception.engine.enabled=true`）以产生 EXCEPTION 事件源；
4. 导入或创建告警规则并启用。

## 规则配置

规则五要素（列定义见总览）：**事件源匹配（match_json）→ 触发方式（EVENT/SCHEDULE/BOTH）→ 聚合策略（INDEPENDENT/AGGREGATE + 窗口参数）→ 接收人（receiver_json）→ 通道路由（route_json）**。

YAML 导入导出与异常规则同款：`POST /api/alert/rule/import-yaml`、`GET /{id}/export-yaml`；种子规则 `ALR-001~ALR-007`（YAML 见工程 `bea/alert-rules/`，seed SQL `alert-rules-seed-insert-2026-10.sql`）。

EXCEPTION 源的 match_json 形态：

```json
{ "source": "EXCEPTION", "events": ["OPEN", "ESCALATED"], "ruleCodes": ["EXC-BL-015"] }
```

`events` 匹配 `platform_exception` 实例生命周期事件类型；`ruleCodes` 声明后仅对源规则编码包含列表内实例的告警（不声明则全部）。

## 模板与渲染

- 模板表 `platform_alert_template`：`templateCode` + 渠道变体，每个变体含 `titleScript` / `contentScript`（js）与示例上下文。
- 脚本只读 `$ctx` / `$source`（结构性禁取数），产出标题与正文；变体停用或缺失回退 `(code, default)`。
- 调试通道：`POST /api/alert/template/test-render`（真实渲染，引擎关闭时占位返回）、`GET /context-schema`（上下文结构）、`GET /context-from-record/{recordId}`（取真实告警记录的渲染快照作为样例）。

## 静默

`platform_alert_silence` 按维度 + 时间窗抑制投递（RESOLVE 通知可豁免）。`POST /api/alert/silence/match-preview` 可在不落库的情况下预演"哪些在途告警会被静默"。

## REST API

:::note
端点声明自 `@ApiRestController`，在带 `/api` 前缀的宿主中实际路径带 `/api`。
:::

**规则 `/api/alert/rule`**

| 端点 | 说明 |
| --- | --- |
| `POST /pageQuery`、`GET /{id}`、`POST /save` | 分页 / 详情 / 保存 |
| `POST /{id}/enable`、`/disable`、`/delete` | 启停删 |
| `POST /import-yaml`、`GET /{id}/export-yaml` | YAML round-trip |
| `POST /{id}/trial-run` | 试跑 |
| `GET /{id}/records` | 规则告警记录 |

**模板 `/api/alert/template`**：`POST /pageQuery`、`GET /{code}`、`POST /save`、`POST /{code}/delete`、`POST /test-render`、`GET /context-schema`、`GET /context-from-record/{recordId}`

**记录 `/api/alert/record`**：`POST /pageQuery`、`GET /{id}`、`GET /{id}/messages`（关联 message 侧消息）、`POST /{id}/resend`（快照重投）、`POST /{id}/silence-from`（由记录发起静默）

**静默 `/api/alert/silence`**：`POST /pageQuery`、`POST /save`、`POST /{id}/revoke`、`POST /match-preview`

**管理 `/api/alert`**：`POST /alert/admin/trigger`（手动触发规则）、`POST /alert/admin/flush`（手动冲刷）、`GET /alert/admin/stats`（统计）、`POST /alert/event`（事件接入端点）

## 运维要点

- **重启不漏扫**：源扫描按 `source-lookback-seconds`（默认 600s）回看水位。
- **投递失败排查**：记录状态 `PARTIAL/FAIL` 与 message 侧消息列表（`GET /{id}/messages`）；FAIL 死信用 `resend` 快照重投（不重渲染）。
- **聚合组卡住**：GROUP 组受 `group_wait_seconds` 窗口与 `group_interval_seconds` 顺延约束，可用 `POST /alert/admin/flush` 手动冲刷。
- **每日限额**：`daily_limit` 超限的投递被丢弃留痕，次窗口恢复。
