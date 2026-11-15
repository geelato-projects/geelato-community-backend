---
title: 调度集成与日志
sidebar_label: 调度集成与日志
---

# 调度集成与日志

本页覆盖调度任务、ORM 钩子与各类日志共 7 个实体。OrmHook + OrmHookLog 构成"配置 + 发件箱"的实体事件外推机制；Schedule + ScheduleLog 构成定时调度对；EncodingLog 是单据编码/流水号生成记录；AppPageLog 与 PlatformExceptionLog 分别是页面操作与平台错误日志。

## Schedule 调度任务

定时调度任务定义：cron 表达式驱动，支持 java 类型任务与参数、重试、持久化上下文。类级标题误写为"应用"，按实际语义撰写。继承 `BaseEntity`，公共字段见[概览](overview.md#公共基类字段)。调度引擎视角见[调度模块](../scheduler/overview.md)。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 所属应用 |
| name | name | String | 名称 |
| code | code | String | 编码 |
| classInfo | class_info | String | type=java 时为"类名，方法名" |
| expression | expression | String | cron 表达式 |
| type | type | String | 类型 |
| params | params | String | 参数 |
| status | status | Integer | 状态：0 停止、1 启动，默认 0 |
| description | description | String | 描述 |
| retryTimes | retry_times | Integer | 重试次数 |
| extra | extra | String | 冗余字段 |
| lastStartTime | last_start_time | Date | 上次任务开始时间 |
| lastFinishTime | last_finish_time | Date | 上次任务结束时间 |
| context | context | String | 任务持久化上下文 |

## OrmHook ORM实体钩子配置

ORM 实体钩子配置：一条 Hook = 名称 + 地址 + 启停。平台内任何实体的 insert/update/delete 事务提交后，向地址 POST 完整载荷 JSON（entityName/eventType/values），下游自行分发处理。纯附加特性：业务事务内零额外操作，触发与执行全部异步，经发件箱 platform_orm_hook_log 重试/死信，失败永不上抛；钩子机制自身的两张表不触发（防递归）。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| title | title | String | 钩子名称，非空，长度 128 |
| httpUrl | http_url | String | 地址，下游系统接收 POST 的 URL，非空，长度 512 |
| enableStatus | enable_status | int | 是否启用：0 禁用、1 启用，默认 1 |

## AppPageLog 页面操作日志

页面（设计器）操作日志，保存页面源文件内容快照。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 应用 Id，所属应用 |
| pageId | page_id | String | 页面 Id，关联的页面 |
| code | code | String | 编码 |
| label | label | String | 标题 |
| type | type | String | 类型，如 `api`/`form`/`table` |
| version | version | int | 版本 |
| extendId | extend_id | String | 扩展信息，扩展 id（如对应的叶子节点 id） |
| description | description | String | 描述 |
| sourceContent | source_content | String | 源文件内容，longText，非空 |

## ScheduleLog 调度日志

调度任务执行日志，一次执行一行。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| appId | app_id | String | 所属应用 |
| scheduleId | schedule_id | String | 所属调度 ID |
| scheduleCode | schedule_code | String | 调度编码 |
| scheduleName | schedule_name | String | 所属调度名称 |
| scheduleInfo | schedule_info | String | 调度信息：表达式、参数、重试次数 |
| type | type | String | 类型：开始、执行中、结束 |
| startAt | start_at | Date | 执行开始时间 |
| finishAt | finish_at | Date | 执行结束时间 |
| status | status | String | 状态：成功、失败 |
| result | result | String | 执行结果 |

## OrmHookLog ORM钩子执行发件箱

ORM 钩子执行发件箱，每次触发一行。事务提交后（afterCommit 回调）异步写入（ready）并立即尝试执行一次；失败由调度器异步扫描、抢占（CAS → processing）：成功 → success，失败 → 指数退避重试 → 死信（dead，可手动重放）。执行时按 hook_id 回读**当前**配置（配置被删/禁用则死信）；本表只存触发上下文快照，不复制动作配置，修改配置对未完成的重试立即生效。本表读写全部走 JdbcTemplate 直 SQL（不经 ORM save），从根上避免事件递归。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| hookId | hook_id | String | 钩子配置 ID，非空，长度 32 |
| eventId | event_id | String | 触发事件 ID（ORM 事件的 eventId），长度 36 |
| entityName | entity_name | String | 实体名称，非空，长度 64 |
| eventType | event_type | String | 事件类型：insert / update / delete，非空，长度 16 |
| opType | op_type | String | 操作类型：Insert / Update / Delete，长度 16 |
| actionType | action_type | String | 动作类型，触发时刻的信息性快照：script / http；实际执行按当前配置，非空，长度 16 |
| payloadJson | payload_json | String | 触发载荷 JSON：新值快照 values、会话信息 session、eventId 等 |
| status | status | String | 执行状态：ready / processing / success / dead，长度 16 |
| retryCount | retry_count | int | 重试次数 |
| nextRetryAt | next_retry_at | Date | 下次重试时间 |
| errorMsg | error_msg | String | 错误信息，长度 512 |

## EncodingLog 编码记录

单据编码/流水号生成记录：按模板记录每次生成的实例与流水号。继承 `BaseEntity`。注意：该实体 catalog 为 `email`，落邮件库（历史归属），是本模块仅有的两个非 platform catalog 实体之一。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| encodingId | encoding_id | String | 编码模板 ID |
| template | template | String | 模板 |
| example | example | String | 实例（生成的编码值） |
| exampleDate | example_date | String | 时间 |
| exampleSerial | example_serial | String | 流水 |
| enableStatus | enable_status | int | 状态。有 `enable_status` 字段但未实现 `EntityEnableAble` 接口 |
| description | description | String | 备注 |

## PlatformExceptionLog 平台错误日志

平台异常/错误日志。继承 `BaseEntity`，catalog 为 `platform-log`（独立日志库）。错误码体系见[错误码参考](../reference/error-codes.md)。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| happenedTime | happened_time | Date | 发生时间 |
| exceptionClass | exception_class | String | 错误类对象，长度 255 |
| exceptionStacktrace | exception_stacktrace | String | 错误堆栈 |
| appId | app_id | String | 所属应用，长度 255 |
| exceptionCode | exception_code | String | 错误编码，长度 255 |
| tenantCode | tenant_code | String | 租户编码。覆写基类字段以对齐表列长度 varchar(64) |

## 推荐继续阅读

- [调度模块](../scheduler/overview.md)——Schedule 的执行引擎
- [ORM 事件特性](../orm/event-features.md)——实体事件机制，OrmHook 的触发源
- [全平台实体索引](platform-entity-index.md)——日志类实体的跨模块全景
