---
id: overview
title: Scheduler Overview
sidebar_label: Scheduler
---

# Scheduler

Geelato provides a unified job scheduling center: scheduled jobs are registered as database configuration in the `platform_schedule` table. On application startup, enabled jobs are loaded automatically and triggered periodically by cron expressions; changes made while running take effect through hot refresh, with no restart required.

**This page is for** business developers and operations staff who need to configure scheduled jobs on the platform. It covers which jobs can be scheduled, how to enable scheduling, and where platform built-in periodic tasks live. For job configuration and start/stop operations, see [Job Configuration & Operations](usage.md).

## 1. What jobs can be scheduled

The scheduling center supports three job types, each pointing via a unique code (`code`) to an existing executable:

| Type (`type`) | How the job is defined | Typical use cases |
| :--- | :--- | :--- |
| `sql` | `code` refers to a SQL registered in platform **SQL management** (`platform_sql`) | Periodic data cleanup, aggregation, status refresh via configured SQL |
| `java` | `code` + `class_info = "fully.qualified.ClassName,methodName"`, invoking a Bean method inside the application container | Logic that has already been developed within the application |
| `general` | `code` refers to a JS script published in platform **API management** (`platform_api`) | Business orchestration jobs; scripts can use `$gl` services directly |

:::note
The `retry_times` field in the job model is reserved, but **automatic retry is not yet implemented**: a failed job will not be re-run automatically and needs manual handling.
:::

**Execution identity**: jobs are triggered by the scheduler under a system identity — SQL and scripts are not filtered by the current user's data permissions, while tenant isolation still applies. Take this into account when assessing data visibility in job SQL or scripts.

## 2. Enabling scheduling and deployment notes

Scheduling is enabled through application configuration:

```properties
# Scheduling master switch (off by default)
geelato.schedule=true
# Scheduling thread pool size (default 10)
geelato.schedule.thread-pool-size=10
```

:::warning Clustering behavior
The scheduler has **no cluster coordination** (no distributed lock or leader election): every node with `geelato.schedule=true` independently loads and triggers all enabled jobs. When running multiple instances, the same job executes once per node — enable scheduling on a single instance only, or make sure the job itself is idempotent.
:::

## 3. Built-in periodic tasks of platform modules

Some platform modules ship their own periodic engine actions (mail sync, alert scanning, etc.). These actions are **not registered in the scheduling center**; they run independently within each module and expose their own configuration switches:

| Module | Periodic action | Configuration switch |
| :--- | :--- | :--- |
| Mail | Incremental mailbox account sync | `geelato.mail.sync.enabled` |
| In-app notifications | Notification delivery | `geelato.notification.outbox-enabled` |
| Business alerts | Alert engine scan/delivery | `geelato.alert.engine.enabled` |
| Data archiving | Scheduled archiving by archive templates | `geelato.archive.scheduler-enabled` |
| Business exceptions | Exception rule scanning | `geelato.exception.engine.enabled` |

**Selection guideline**: business jobs configured through the admin UI (sql/java/general) → the unified scheduling center; periodic engine actions inside modules → controlled by their own switches, no job registration needed.

## Recommended reading

- [Job Configuration & Operations](usage.md): how to configure a scheduled job, start/stop it, and view execution logs.
- [Schedule & Log Entities](../entities/schedule-integration-log.md): full field definitions of `platform_schedule` / `platform_schedule_log`.
