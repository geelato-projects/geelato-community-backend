---
title: 调度总览
sidebar_label: 调度
---

# 调度（Scheduler）

Geelato 提供统一的任务调度中心：定时任务以数据库配置的方式登记在 `platform_schedule` 表中，应用启动时自动装载启用中的任务，按 cron 表达式周期触发；运行期间修改任务配置后热更新即可生效，无需重启应用。

**本页面向**需要在平台中配置定时任务的业务开发者与运维人员，说明哪些任务可以被调度、调度如何启用，以及平台内置周期任务的分布。任务的配置与启停操作见[任务配置与操作](usage.md)。

## 1. 什么任务可以被调度

调度中心支持三种类型的任务，均通过任务唯一码（`code`）指向一个已存在的执行体：

| 类型 (`type`) | 任务如何定义 | 适用场景 |
| :--- | :--- | :--- |
| `sql` | `code` 填平台 **SQL 管理**（`platform_sql`）中登记的 SQL 标识 | 定期数据清理、汇总、状态刷新等配置化 SQL |
| `java` | `code` + `class_info = "全类名,方法名"`，调用应用容器内的 Bean 方法 | 逻辑已随应用开发完成的场景 |
| `general` | `code` 填平台 **API 管理**（`platform_api`）中已发布的 JS 脚本 | 业务编排类任务，脚本内可直接使用 `$gl` 服务 |

:::note
任务模型中的 `retry_times`（重试次数）字段已预留，**自动重试暂未实现**：任务执行失败后不会自动重跑，需人工排查处理。
:::

**执行身份**：任务由调度器以系统身份触发执行——SQL 与脚本不受当前人员的数据权限过滤，但租户隔离依然生效。编写任务 SQL 或脚本时，请据此评估数据的可见范围。

## 2. 启用调度与部署注意

调度能力通过应用配置开启：

```properties
# 调度总开关（默认关闭）
geelato.schedule=true
# 调度线程池大小（缺省 10）
geelato.schedule.thread-pool-size=10
```

:::warning 集群行为
调度器**无集群协调**（无分布式锁/选主）：每个 `geelato.schedule=true` 的节点独立装载并触发全部启用任务。多实例部署时，同一任务会在每个节点各执行一次——请只在单实例开启调度，或确保任务本身幂等。
:::

## 3. 平台模块的内置周期任务

平台部分模块自带周期性引擎动作（如邮件同步、告警扫描），这些动作**不经过调度中心登记**，而是随模块独立运行，各自提供配置开关：

| 模块 | 周期动作 | 配置开关 |
| :--- | :--- | :--- |
| 邮件 | 邮箱账号增量同步 | `geelato.mail.sync.enabled` |
| 站内通知 | 通知外发投递 | `geelato.notification.outbox-enabled` |
| 业务告警 | 告警引擎扫描/投递 | `geelato.alert.engine.enabled` |
| 数据归档 | 按归档模板定时归档 | `geelato.archive.scheduler-enabled` |
| 业务异常 | 异常规则扫描 | `geelato.exception.engine.enabled` |

**选型口径**：业务方在管理界面配置的定时任务（sql/java/general）→ 统一调度中心；模块内部的引擎周期动作 → 各自开关控制，无需登记任务。

## 推荐继续阅读

- [任务配置与操作](usage.md)：如何配置一个定时任务、如何启停与查看执行日志。
- [调度集成与日志实体](../entities/schedule-integration-log.md)：`platform_schedule` / `platform_schedule_log` 完整字段定义。
