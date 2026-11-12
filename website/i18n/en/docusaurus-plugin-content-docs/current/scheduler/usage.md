---
id: usage
title: Working with Scheduled Jobs
sidebar_label: Working with Jobs
---

# Working with Scheduled Jobs

How to configure and manage `platform_schedule` jobs. Mechanics and cluster behavior: [Scheduler Overview](overview.md).

## Configuring a Job

1. **Prepare the executor**:
   - `sql`: register the SQL in platform SQL management (`platform_sql`);
   - `java`: ensure the target bean exists (`class_info = "FQCN,methodName"`);
   - `general`: publish a JS API in platform API management (`platform_api`, `release_content`); use `$gl` services; `ctx.result` carries state across runs.
2. **Create the job row**: via the Designer schedule page or `POST /api/schedule/createOrUpdate`: `code` (unique), `expression` (cron), `type`, `params` (JSON), `app_id`.
3. **Enable**: `status=1` (page switch or `GET /api/schedule/status/{id}/{status}`). Newly enabled jobs take effect at the next restart load or hot refresh.
4. **Hot update**: after changing `expression` or params, call `GET /schedule/runtime/{code}/config/refresh` — effective immediately, no restart.

## REST API

**Management `/api/schedule`** (design-time, /api-prefixed)

| Endpoint | Description |
| --- | --- |
| `POST /pageQuery`, `GET /query`, `GET /get/{id}` | Page / list / detail |
| `POST /createOrUpdate`, `DELETE /isDelete/{id}`, `POST /validate` | Upsert / soft delete / validate |
| `GET /status/{id}/{status}` | Enable/disable |
| `/api/schedule/log` | Execution log paging |

**Runtime control `/schedule/runtime`** (plain `@RestController`, **no /api prefix**)

| Endpoint | Description |
| --- | --- |
| `GET /{code}/start` | Run once now |
| `GET /{code}/stop` | Cancel current trigger |
| `GET /{code}/config` | Registered trigger (code/cron/last run) |
| `GET /{code}/config/refresh` | Hot update after row edits |

## Examples by Type

**sql** (periodic cleanup):

```
platform_schedule: { code: "CLEAN_TEMP", type: "sql", expression: "0 0 3 * * ?" }
platform_sql:      { key_name: "CLEAN_TEMP", ... DELETE FROM xxx WHERE create_at < :cutoff }
```

**general** (JS job with cross-run context):

```javascript
const pending = $gl.sql.query("SELECT COUNT(*) c FROM task WHERE status='PENDING'")
if (pending[0].c > 0) {
  // processing
  ctx.result = { lastCount: pending[0].c }   // persisted to platform_schedule.context
}
```

**java**: `class_info = "cn.geelato.app.task.ReportTask,buildDailyReport"`.

## Troubleshooting

- **Job not firing**: check `geelato.schedule=true`, `status=1`, single instance (cluster warning in the overview), the cron, and `/schedule/runtime/{code}/config` for registration.
- **Failures**: check `platform_schedule_log` (status/result) and `/api/schedule/log`; note `retry_times` auto-retry is not implemented yet.
- **Context residue**: `platform_schedule.context` is written explicitly by jobs; `general` jobs' `ctx.result` persists across runs — plan cleanup.
- **Multiple instances**: make jobs idempotent or enable `geelato.schedule=true` on a single instance only.
