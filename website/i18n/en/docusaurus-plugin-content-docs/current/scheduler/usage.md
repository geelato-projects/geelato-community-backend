---
id: usage
title: Job Configuration & Operations
sidebar_label: Job Configuration & Operations
---

# Job Configuration & Operations

This page is for business developers and operations staff who need to configure scheduled jobs on the platform. It covers how to configure a scheduled job, how to start/stop it, and how to view execution logs and troubleshoot problems. For the positioning of the scheduling capability and deployment notes, see [Scheduler Overview](overview.md).

## 1. Configuring a scheduled job

1. **Prepare the executable** (one of the three types):
   - `sql`: register the SQL in platform **SQL management** (`platform_sql`) first;
   - `java`: make sure the target Bean exists in the application container, formatted as `class_info = "fully.qualified.ClassName,methodName"`, with a no-arg method or framework-convention injection;
   - `general`: publish a JS script in platform **API management** (`platform_api`); the script can use `$gl` services, and `ctx.result` carries data across runs.
2. **Create the job**: in the admin/Designer "Schedule Management" page, or via `POST /api/schedule/createOrUpdate`, fill in `code` (unique), `expression` (cron), `type`, `params` (JSON parameters), `app_id`, etc. (see the field table below).
3. **Enable the job**: set `status` to `1` (the "Enable" button on the list page, or `GET /api/schedule/status/{id}/1`).
4. **Make it effective**: a newly created or modified job takes effect on the **next application restart**, or after calling the hot-refresh endpoint — see "When changes take effect" below.

## 2. Job configuration fields

Jobs are registered in the `platform_schedule` table. The main configuration fields are listed below (full field definitions in [Schedule & Log Entities](../entities/schedule-integration-log.md)):

| Field | Required | Description |
| :--- | :--- | :--- |
| `code` | ✅ | Unique job code; for `sql`/`general` it is also the referenced SQL/API identifier |
| `type` | ✅ | Job type: `sql` / `java` / `general` |
| `expression` | ✅ | Cron expression (6 fields: second minute hour day month weekday, `?` supported in day/weekday), e.g. `0 0 3 * * ?` = 3:00 AM daily |
| `app_id` | Recommended | Owning application |
| `name` | Recommended | Display name of the job |
| `params` | Optional | JSON parameters readable during execution |
| `class_info` | By type | Only for `java` jobs, formatted as `"fully.qualified.ClassName,methodName"` |
| `status` | — | Job status: `0` stopped / `1` enabled; new jobs default to stopped |
| `retry_times` | — | Retry count (reserved; automatic retry not yet implemented) |
| `context` | — | Persisted cross-run context written by the job itself (e.g. `ctx.result` of `general` jobs) |
| `description` | Optional | Job description |

`last_start_time` / `last_finish_time` are written back automatically by the scheduler on each execution, for showing the latest run.

## 3. Examples of the three job types

**sql** (periodic data cleanup):

```
platform_schedule: { code: "CLEAN_TEMP", type: "sql", expression: "0 0 3 * * ?" }
platform_sql:      { key_name: "CLEAN_TEMP", ... DELETE FROM xxx WHERE create_at < :cutoff }
```

**general** (JS business job with cross-run context):

```javascript
// Script published in platform_api; the job's code points to this API
const pending = $gl.sql.query("SELECT COUNT(*) c FROM task WHERE status='PENDING'")
if (pending[0].c > 0) {
  // processing logic
  ctx.result = { lastCount: pending[0].c }   // written back to the job context, readable next run
}
```

**java**: `class_info = "cn.geelato.app.task.ReportTask,buildDailyReport"`.

## 4. Start/stop and runtime operations

### 4.1 UI operations

The "Schedule Management" page in the admin/Designer provides full management for jobs:

- **Create/edit jobs**: a form for the configuration fields above, with cron validation;
- **Enable/disable**: the list page offers "Enable" for stopped jobs and "Disable" for enabled ones (both with confirmation);
- **Execution logs**: each job in the list can open its log page showing every execution record.

### 4.2 REST management plane `/api/schedule` (with the `/api` prefix)

| Endpoint | Description |
| :--- | :--- |
| `POST /pageQuery`, `GET /query`, `GET /get/{id}` | Job pagination / list / detail |
| `POST /createOrUpdate`, `DELETE /isDelete/{id}`, `POST /validate` | Create or update / delete / validate |
| `GET /status/{id}/{status}` | Enable (1) / disable (0) |
| `/api/schedule/log/pageQuery`, `GET /api/schedule/log/query` | Execution log pagination / list |

### 4.3 Runtime control `/schedule/runtime` (**without** the `/api` prefix)

| Endpoint | Description |
| :--- | :--- |
| `GET /{code}/start` | Trigger an immediate one-off execution (periodic schedule unaffected) |
| `GET /{code}/stop` | Cancel the current pending trigger of the job |
| `GET /{code}/config` | Query the trigger configuration currently registered for the job |
| `GET /{code}/config/refresh` | Hot refresh: call after changing the DB configuration, effective without restart |

### 4.4 When changes take effect

- **Newly created jobs / enabling a stopped job**: effective on the next application restart, or after calling `GET /schedule/runtime/{code}/config/refresh`;
- **Changing the cron or params of a running job**: call `GET /schedule/runtime/{code}/config/refresh` to take effect immediately, no restart needed;
- **Disabling a job**: subsequent triggers stop immediately; an execution already in progress is not interrupted.

## 5. Execution logs and troubleshooting

Every execution writes one log entry (`platform_schedule_log`): `status` (`execute` running → `success` / `fail`), `start_at` / `finish_at`, and `result` (result summary). View them on the log page or via `/api/schedule/log`.

Common troubleshooting:

- **Job not triggering**: confirm `geelato.schedule=true` is on, the job `status=1`, the node is a single instance or jobs are idempotent across instances (see the [cluster warning](overview.md)), and the cron expression is correct; then check `GET /schedule/runtime/{code}/config` to confirm the job is registered.
- **Execution failures**: check `result` summaries and `finish_at` of `status=fail` log entries; note that automatic retry is not yet implemented (`retry_times` has no effect) — failed jobs do not re-run automatically.
- **Residual context**: `context` is written explicitly by the job; the `ctx.result` of `general` jobs persists across runs — if you don't need cross-run data, don't set `ctx.result`, or overwrite/clean it yourself.
- **Duplicate execution on multiple instances**: see "Clustering behavior" in the overview — enable scheduling on a single instance only, or make jobs idempotent.
