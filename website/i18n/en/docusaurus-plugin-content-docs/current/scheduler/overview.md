---
id: overview
title: Scheduler Overview
sidebar_label: Scheduler
---

# Scheduler (geelato-schedule)

Geelato's general scheduling capability is provided by `geelato-schedule` (standalone repo: `geelato-schedule-core` pure execution abstraction + `geelato-schedule-starter` Spring Boot wiring): **database-config-driven, in-process triggering** — jobs live in the `platform_schedule` table, are loaded at startup (enabled ones only) and triggered by cron; changes are hot-refreshed without restart.

:::warning Cluster Behavior
The scheduler has **no cluster coordination** (no distributed lock / leader election): every node with `geelato.schedule=true` independently loads and triggers all enabled jobs. With multiple instances the same job runs once per node — either enable scheduling on a single instance only, or make jobs idempotent.
:::

## Enabling

```properties
geelato.schedule=true            # master switch (quickstart default ${GEELATO_SCHEDULE_OPTION:false})
geelato.schedule.thread-pool-size=10
```

`DynamicLiteSchedulingConfigurer` (`@EnableScheduling @EnableAsync`) is registered via classic `spring.factories` and wired only when `geelato.schedule=true`; a health endpoint ("调度模块 HEALTH") is registered alongside.

## Job Model

Job table `platform_schedule` (entity `Schedule`, catalog=platform): `app_id`, `name`, `code` (unique), `class_info`, `expression` (cron), `type`, `params`, `status` (0 off / 1 on), `retry_times`, `last_start_time` / `last_finish_time`, `context` (cross-run state). Execution logs go to `platform_schedule_log`.

Three executor types (`type`):

| Type | Executor | Description |
| --- | --- | --- |
| `sql` | `SqlKeyExecution` | `code` = `platform_sql.id`; executes the configured SQL by `key_name` via Dao.executeKey |
| `java` | `ClassExecution` | `class_info = "className,methodName"`; reflects into a container bean |
| `general` | `GraalJsExecution` | `code` = `platform_api.id`; runs `release_content` (IIFE template) on GraalVM with `$gl` services injected; `ctx.result` is written back to `platform_schedule.context` for the next run |

:::note
`retry_times` is modeled and passed into Cron, but the retry loop is not implemented in the execution layer yet.
:::

## Runtime Model

- **Loading**: at startup a fixed SQL selects `status=1` jobs, each wrapped into a `GeelatoTriggerTask` (CronTrigger) on the pool.
- **Hot refresh**: after editing rows, call `GET /schedule/runtime/{code}/config/refresh` — cron changes apply without restart.
- **Execution context**: each run runs as system user `system-schedule` (skips data-permission filtering, keeps tenant isolation), wrapped by `SecurityContextRunnable.wrap` with ThreadLocal cleanup.
- **Logging**: `ScheduleLogHelper` writes `platform_schedule_log` and updates `last_start_time`/`last_finish_time`; `ScheduleContextHelper` reads/writes cross-run context.

## The Platform Modules' Own Convention (the second line)

The platform host **deliberately does not enable `@EnableScheduling` globally**; modules with periodic work use the **self-managed ScheduledExecutorService daemon-thread** pattern (independent switches, no cross-dependency):

| Module | Scheduler | Switch |
| --- | --- | --- |
| Mail sync | `MailSyncScheduleTask` (60s account-level scan) | `geelato.mail.sync.enabled` |
| In-app notifications | `NotificationOutboxScheduler` (convention origin) | with module |
| Business alert | `AlertEngineScheduler` (main + schedule ticks) | `geelato.alert.engine.enabled` |
| Data archive | `ArchiveScheduler` (daily schedule-time) | `geelato.archive.scheduler-enabled` |
| Business exception | `ExceptionScanScheduler` (30s scan tick) | `geelato.exception.engine.enabled` |

**Choice rule**: business-level scheduled jobs configured by users in the Designer (sql/java/general) → geelato-schedule; module-internal engine cycles → self-managed daemon threads. The archive preset `tpl-schedule-log` in turn archives `platform_schedule_log` (90 days).

## Management Surface

- Designer page: geelato-front `packages/gl-ide-arco/src/views/sidebar/schedule/`.
- Management endpoints (`/api/schedule`): `POST /pageQuery`, `GET /query`, `GET /get/{id}`, `POST /createOrUpdate`, `DELETE /isDelete/{id}`, `POST /validate`, `GET /status/{id}/{status}`; logs `/api/schedule/log`.
- Runtime control: `GET /schedule/runtime/{code}/start` (run once now), `/stop`, `/config/refresh` (hot update), `/config` (registered triggers) — plain `@RestController`, **no /api prefix**.

Usage: [Working with Scheduled Jobs](usage.md).
