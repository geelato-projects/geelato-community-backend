---
title: 调度总览
sidebar_label: 调度
---

# 调度（geelato-schedule）

Geelato 的通用调度能力由 `geelato-schedule` 提供（独立仓库，`geelato-schedule-core` 纯执行抽象 + `geelato-schedule-starter` Spring Boot 装配）：**数据库配置驱动、进程内触发**——任务存于 `platform_schedule` 表，应用启动时装载启用任务并按 cron 触发，改库后热更新免重启。

:::warning 集群行为
调度器**无集群协调**（无分布式锁/选主）：每个 `geelato.schedule=true` 的节点独立装载并触发全部启用任务。多实例部署时，同一任务会在每个节点各执行一次——通用任务请只在单实例开启调度，或确保任务本身幂等。
:::

## 启用方式

```properties
# 调度总开关（quickstart 默认 ${GEELATO_SCHEDULE_OPTION:false}）
geelato.schedule=true
# 调度线程池大小
geelato.schedule.thread-pool-size=10
```

装配类 `DynamicLiteSchedulingConfigurer`（`@EnableScheduling @EnableAsync`）经传统 `spring.factories` 注册，仅当 `geelato.schedule=true` 才装配；同时注册健康端点（"调度模块 HEALTH"）。

## 任务模型

任务表 `platform_schedule`（实体 `Schedule`，catalog=platform）：`app_id`、`name`、`code`（任务唯一码）、`class_info`、`expression`（cron）、`type`、`params`、`status`（0 停 / 1 启）、`retry_times`、`last_start_time` / `last_finish_time`、`context`（跨轮上下文）。执行日志落 `platform_schedule_log`。

三种执行体类型（`type`）：

| 类型 | 执行体 | 说明 |
| --- | --- | --- |
| `sql` | `SqlKeyExecution` | `code` 即 `platform_sql.id`，按 `key_name` 经 Dao.executeKey 执行配置化 SQL |
| `java` | `ClassExecution` | `class_info = "类名,方法名"`，反射调用当前容器 Bean 的方法 |
| `general` | `GraalJsExecution` | `code` 即 `platform_api.id`，取 `release_content` 包 IIFE 模板经 GraalVM 执行，注入 `$gl` 变量/服务；`ctx.result` 回写 `platform_schedule.context` 供下轮读取 |

:::note
`retry_times` 字段已建模并传入 Cron，但执行层暂未实现重试循环。
:::

## 运行机制

- **装载**：启动时固定 SQL 查 `status=1` 的任务，逐条包成 `GeelatoTriggerTask`（CronTrigger）注册到线程池。
- **热更新**：运行时改库后调 `GET /schedule/runtime/{code}/config/refresh`，改 cron 免重启。
- **执行上下文**：每次执行以系统用户 `system-schedule` 构造（跳过数据权限、保留租户隔离），经 `SecurityContextRunnable.wrap` 包装并在结束后清理 ThreadLocal。
- **日志**：`ScheduleLogHelper` 写 `platform_schedule_log` 并回写 `last_start_time` / `last_finish_time`；`ScheduleContextHelper` 读写跨轮上下文。

## 平台模块的调度惯例（第二条线）

平台宿主**刻意不全局开启 `@EnableScheduling`**，需要周期动作的模块统一使用**自管理 ScheduledExecutorService 守护线程**模式（各有独立开关、互不依赖）：

| 模块 | 调度器 | 开关 |
| --- | --- | --- |
| 邮件同步 | `MailSyncScheduleTask`（60s 扫描账号级增量） | `geelato.mail.sync.enabled` |
| 站内通知 | `NotificationOutboxScheduler`（惯例源头） | 随模块 |
| 业务告警 | `AlertEngineScheduler`（主 tick + schedule tick） | `geelato.alert.engine.enabled` |
| 数据归档 | `ArchiveScheduler`（每日 schedule-time） | `geelato.archive.scheduler-enabled` |
| 业务异常 | `ExceptionScanScheduler`（30s tick 扫描腿） | `geelato.exception.engine.enabled` |

**选型口径**：用户在 Designer 里配置的业务级定时任务（sql/java/general）→ geelato-schedule；模块内部的周期性引擎动作 → 自管理守护线程。归档预置模板 `tpl-schedule-log` 反过来把 `platform_schedule_log`（90 天）列为归档对象。

## 管理面

- Designer 管理页：geelato-front `packages/gl-ide-arco/src/views/sidebar/schedule/`。
- 管理端点（`@DesignTimeApiRestController("/schedule")` → `/api/schedule`）：`POST /pageQuery`、`GET /query`、`GET /get/{id}`、`POST /createOrUpdate`、`DELETE /isDelete/{id}`、`POST /validate`、`GET /status/{id}/{status}`；日志 `/api/schedule/log`。
- 运行时控制：`GET /schedule/runtime/{code}/start`（立即执行一次）、`/stop`、`/config/refresh`（热更新）、`/config`（查询已注册触发器）——普通 `@RestController`，**不带 /api 前缀**。

任务配置操作见[调度任务使用](usage.md)。
