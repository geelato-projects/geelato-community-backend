---
title: Scheduling, Integration & Logs
sidebar_label: Scheduling & Logs
---

# Scheduling, Integration & Logs

This page covers 7 entities across scheduled tasks, ORM hooks and various logs. OrmHook + OrmHookLog form the "configuration + outbox" mechanism for pushing entity events outward; Schedule + ScheduleLog form the scheduled-tasking pair; EncodingLog records document number/serial generation; AppPageLog and PlatformExceptionLog are the page operation log and the platform error log.

## Schedule

Scheduled task definition: driven by a cron expression, supporting java-type tasks with parameters, retries and a persisted context. Its class title is mislabeled as "应用" (application); documented here by actual semantics. Extends `BaseEntity`; common fields are listed in [Overview](overview.md#common-base-class-fields). See the [Scheduler module](../scheduler/overview.md) for the scheduling engine view.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Owning application |
| name | name | String | Name |
| code | code | String | Code |
| classInfo | class_info | String | When type=java: "class name, method name" |
| expression | expression | String | Cron expression |
| type | type | String | Type |
| params | params | String | Parameters |
| status | status | Integer | Status: 0 stopped, 1 started; defaults to 0 |
| description | description | String | Description |
| retryTimes | retry_times | Integer | Retry count |
| extra | extra | String | Redundant field |
| lastStartTime | last_start_time | Date | Last task start time |
| lastFinishTime | last_finish_time | Date | Last task finish time |
| context | context | String | Persisted task context |

## OrmHook

ORM entity hook configuration: one Hook = name + URL + on/off. After the transaction of any entity's insert/update/delete commits, the platform POSTs the full payload JSON (entityName/eventType/values) to the URL and the downstream dispatches by itself. Purely additive: zero extra work inside the business transaction; trigger and execution are fully asynchronous, retried/dead-lettered through the outbox platform_orm_hook_log, with failures never thrown upward; the hook mechanism's own two tables do not trigger hooks (recursion guard). Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| title | title | String | Hook name, non-null, length 128 |
| httpUrl | http_url | String | URL, the downstream address that receives the POST after entity events commit, non-null, length 512 |
| enableStatus | enable_status | int | Whether enabled: 0 disabled, 1 enabled; defaults to 1 |

## AppPageLog

Page (designer) operation log, saving a snapshot of the page source content. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Application id, owning application |
| pageId | page_id | String | Page id, the related page |
| code | code | String | Code |
| label | label | String | Title |
| type | type | String | Type, e.g. `api`/`form`/`table` |
| version | version | int | Version |
| extendId | extend_id | String | Extension info, extension id (e.g. the corresponding leaf node id) |
| description | description | String | Description |
| sourceContent | source_content | String | Source content, longText, non-null |

## ScheduleLog

Scheduled task execution log, one row per execution. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| appId | app_id | String | Owning application |
| scheduleId | schedule_id | String | Owning schedule id |
| scheduleCode | schedule_code | String | Schedule code |
| scheduleName | schedule_name | String | Owning schedule name |
| scheduleInfo | schedule_info | String | Schedule info: expression, parameters, retry count |
| type | type | String | Type: start, running, finish |
| startAt | start_at | Date | Execution start time |
| finishAt | finish_at | Date | Execution finish time |
| status | status | String | Status: success, failure |
| result | result | String | Execution result |

## OrmHookLog

The ORM hook execution outbox, one row per trigger. Written asynchronously after transaction commit (afterCommit callback) with status ready and executed once immediately; on failure the scheduler asynchronously scans, claims (CAS → processing) and executes: success → success, failure → exponential-backoff retry → dead letter (dead, manually replayable). At execution time the hook configuration is re-read **current** by hook_id (a deleted/disabled configuration dead-letters); this table stores only the trigger context snapshot and does not copy action configuration, so configuration changes take effect immediately for pending retries. Reads and writes on this table all go through direct JdbcTemplate SQL (not through ORM save), eliminating event recursion at the root. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| hookId | hook_id | String | Hook configuration id, non-null, length 32 |
| eventId | event_id | String | Trigger event id (the ORM event's eventId), length 36 |
| entityName | entity_name | String | Entity name, non-null, length 64 |
| eventType | event_type | String | Event type: insert / update / delete, non-null, length 16 |
| opType | op_type | String | Operation type: Insert / Update / Delete, length 16 |
| actionType | action_type | String | Action type, an informational snapshot at trigger time: script / http; actual execution follows the current configuration, non-null, length 16 |
| payloadJson | payload_json | String | Trigger payload JSON: new-value snapshot values, session info, eventId, etc. |
| status | status | String | Execution status: ready / processing / success / dead, length 16 |
| retryCount | retry_count | int | Retry count |
| nextRetryAt | next_retry_at | Date | Next retry time |
| errorMsg | error_msg | String | Error message, length 512 |

## EncodingLog

Document number/serial generation record: records each generated instance and serial by template. Extends `BaseEntity`. Note: this entity's catalog is `email` — stored in the mail database (historical attribution); one of only two non-platform catalogs in this module.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| encodingId | encoding_id | String | Encoding template id |
| template | template | String | Template |
| example | example | String | Instance (the generated number) |
| exampleDate | example_date | String | Time |
| exampleSerial | example_serial | String | Serial |
| enableStatus | enable_status | int | Status. Carries an `enable_status` field without implementing `EntityEnableAble` |
| description | description | String | Remark |

## PlatformExceptionLog

Platform exception/error log. Extends `BaseEntity`, catalog `platform-log` (separate log database). Error codes are centralized per module in `XxxErrorCodes` enums; see the Chinese page 错误码参考 (`reference/error-codes.md`) for the catalog.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| happenedTime | happened_time | Date | Occurrence time |
| exceptionClass | exception_class | String | Exception class, length 255 |
| exceptionStacktrace | exception_stacktrace | String | Exception stacktrace |
| appId | app_id | String | Owning application, length 255 |
| exceptionCode | exception_code | String | Error code, length 255 |
| tenantCode | tenant_code | String | Tenant code. Overrides the base-class field to align the column with varchar(64) |

## Recommended Reading

- [Scheduler module](../scheduler/overview.md) — the execution engine of Schedule
- [ORM Event Features](../orm/event-features.md) — the entity event mechanism that triggers OrmHook
- [Platform Entity Index](platform-entity-index.md) — the cross-module panorama of log entities
