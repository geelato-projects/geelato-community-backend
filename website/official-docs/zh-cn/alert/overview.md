---
title: 业务告警总览
sidebar_label: 业务告警
---

# 业务告警（geelato-alert）

业务告警中心把"需要人知道的事"从业务代码里解耦出来：**事件/调度触发 → 匹配告警规则 → 聚合抑制 → 模板渲染 → 多通道路由投递**。与[业务异常](../exception/overview.md)是两套独立引擎但互相衔接——异常实例的生命周期事件是告警当前唯一内置事件源。

模块坐标 `cn.geelato:geelato-alert`。**引擎默认关闭**，启用：`geelato.alert.engine.enabled=true`（quickstart 默认 `${GEELATO_ALERT_ENGINE_ENABLED:false}`）；渲染装配（alert-render profile）总是注册。

## 规则模型

规则存储在 `platform_alert_rule`（4 表体系：rule / template / alert / silence）：

| 列 | 说明 |
| --- | --- |
| `alert_code` | `ALR-{三位序号}` |
| `match_json` | 事件源匹配 `{source, events, ruleCodes}`——**事件源泛化设计**，结构由 source 决定；当前内置源仅 `EXCEPTION`（源→实体映射 `platform_exception`，事件走框架 ORM 事件机制），未知源硬失败 |
| `trigger_mode` / `cron_expression` / `dataset_json` | `EVENT` / `SCHEDULE` / `BOTH`；SCHEDULE 配数据集口径 |
| `aggregate_mode` | `INDEPENDENT`（单条直通）/ `AGGREGATE`（聚合组），即 SINGLE/GROUP 语义 |
| `group_wait_seconds` / `group_interval_seconds` / `repeat_interval_minutes` | 聚合窗口 / 再通知最小间隔 / 在途组重复提醒 |
| `max_items` / `resolve_notify` / `daily_limit` | 清单渲染条数上限 / 全部闭环恢复通知 / 每日投递上限 |
| `receiver_json` / `route_json` | 接收人选择器 `{extra[]}` / 通道路由 `{default:{channels, templateCode}}` |

## 引擎流水线

```
AlertEngineScheduler（自管理单线程，宿主不开全局调度）
  主 tick 串行：源扫描 → 冲刷 → 投递 → REPEAT
  schedule tick 独立轮询 SCHEDULE cron（默认 30s）

事件腿：AlertMatcher（event 类型 + ruleCodes 匹配）→ AlertGrouper
  SINGLE：直通 PENDING（flush_at=now）
  GROUP：在途组追加（eventIds 去重并入）或新建组（group_wait 窗口 + active_group_key 唯一占用）
  落库时同步采集源实例业务数据进 source_json（渲染不回查源表）

冲刷：FlushScanner（PENDING→READY→渲染快照）→ RepeatScanner（在途组重复提醒）→ ResolveHandler（闭环 RESOLVE 通知）
投递：DeliverEngine（静默终判 → 渲染兜底 → 接收人求解 → daily_limit → 通道拆条 enqueue）
```

告警记录状态机：`PENDING → READY → SENT / PARTIAL / FAIL / SILENCED`。

## 渲染（结构性禁取数）

- 模板表 `platform_alert_template`：`templateCode` + 渠道变体（titleScript/contentScript js 脚本）；变体缺失或停用回退 `(code, default)`。
- 上下文三形态：SINGLE=`$source`（源实例快照）/ GROUP=清单+统计 / SCHEDULE=数据集注入。
- **禁取数是结构性的**：渲染运行在 `alert-render` profile——上下文只读绑定（frozenBindings）、不注册 SQL kit 与数据源，模板脚本只能加工 `$ctx`/`$source`，不能查库；单脚本超时 2s，title≤120、content≤32KB 超长截断。
- 渲染开关 `geelato.alert.engine.enabled`（关闭时 testRender 占位），`workbench-base-url`（默认 `/fms/exception-center`）供深链拼接。

## 投递（对接 geelato-message）

- `MessageTransport` → `POST {message-base-url}/message/enqueue`（默认端口 8086），sender=`fms-alert`、buss=`alert`；鉴权头 `X-System-Code` + `Authorization: Bearer`。契约见消息集成指南附录 C。
- 按 `route_json` 通道路由拆条（`wework`→`weixin_work_group` 映射），`bizKey={recordId}:{batchNo}:{channel}` 幂等由 message 侧 idempotency_key 兜底。
- 全渠道失败退避重试（`backoffBaseSeconds * 2^n`），超 `maxRetry` 转 FAIL 死信，等手动重发（`POST /api/alert/record/{id}/resend` 快照重投不重渲染）。

## 接收人与静默

- `AlertReceiverSolver` 与异常同款六类选择器（USER/ROLE/DEPT/DEPT_ROLE/BIZ_FIELD/ORG_TREE_UP）+ `fallback-role-code` 兜底（空则记录 FAIL）；首投快照 receiver_ids/receiver_names。
- 静默 `platform_alert_silence`：维度匹配 + 时间窗，RESOLVE 通知由 allowResolve 豁免；支持 `match-preview` 预演。

## 配置键（`geelato.alert.engine.*`）

| 键 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | false | 引擎与渲染总开关 |
| `message-base-url` | —（启用时必填） | geelato-message 服务地址 |
| `message-timeout-ms` | 10000 | enqueue 超时 |
| `message-system-code` / `message-api-key` | — | enqueue 鉴权 |
| `fallback-role-code` | — | 接收人求解兜底角色 |
| `tick-seconds` | 5 | 主 tick 周期 |
| `deliver-batch-size` | 20 | 投递批量 |
| `max-retry` / `backoff-base-seconds` | 3 / 60 | 重试次数与退避基数 |
| `schedule-tick-seconds` | 30 | SCHEDULE 轮询周期 |
| `source-lookback-seconds` | 600 | 重启水位回看（防漏扫） |
| `source-batch-size` | 200 | 源扫描批量 |

## 当前状态

- **后端引擎全链路已实现**并有单测：匹配/聚合/冲刷/渲染/投递/静默/重复提醒/调度/管理端点；规则 YAML 种子（ALR-001~007）与模板种子 SQL 已备。
- **事件源泛化**：`EXCEPTION` 为唯一内置源，其余事件源为设计+骨架（`AlertSources.KNOWN`）。
- **前端未上线**：规则编辑器/模板工作室/告警记录页为设计原型（bea/page 12-14 号原型页）。
