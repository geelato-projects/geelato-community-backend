---
title: 调度任务使用
sidebar_label: 任务使用
---

# 调度任务使用

本页说明如何配置和管理 `platform_schedule` 定时任务。机制与集群行为见[调度总览](overview.md)。

## 配置一个定时任务

1. **准备执行体**：
   - `sql` 型：先在平台 SQL 管理（`platform_sql`）中登记 SQL（`key_name`）；
   - `java` 型：确认目标 Bean 已在容器中（`class_info = "全类名,方法名"`，方法无参或按框架注入）；
   - `general` 型：在平台 API 管理（`platform_api`）中发布 JS API（`release_content`），脚本内用 `$gl` 服务，`ctx.result` 可跨轮传递。
2. **建任务行**：Designer「调度管理」页或 `POST /api/schedule/createOrUpdate` 创建：`code`（唯一）、`expression`（cron）、`type`、`params`（JSON 参数）、`app_id`。
3. **启用**：`status=1`（管理页开关或 `GET /api/schedule/status/{id}/{status}`）。新启用任务在**下一次重启装载**或热刷新后生效。
4. **热更新**：运行中修改 `expression` 或参数后，调 `GET /schedule/runtime/{code}/config/refresh` 立即生效，无需重启。

## REST API

**管理面 `/api/schedule`**（`@DesignTimeApiRestController`，带 /api 前缀）

| 端点 | 说明 |
| --- | --- |
| `POST /pageQuery`、`GET /query`、`GET /get/{id}` | 分页 / 列表 / 详情 |
| `POST /createOrUpdate`、`DELETE /isDelete/{id}`、`POST /validate` | 增改 / 软删 / 校验 |
| `GET /status/{id}/{status}` | 启用/停用 |
| `/api/schedule/log`（ScheduleLogController） | 执行日志分页 |

**运行时控制 `/schedule/runtime`**（普通 `@RestController`，**不带 /api 前缀**）

| 端点 | 说明 |
| --- | --- |
| `GET /{code}/start` | 立即执行一次 |
| `GET /{code}/stop` | 取消当前触发 |
| `GET /{code}/config` | 查询已注册触发器（code/cron/上次执行） |
| `GET /{code}/config/refresh` | 热更新（改库后调用） |

## 三种任务类型示例

**sql 型**（定期数据清理）：

```
platform_schedule: { code: "CLEAN_TEMP", type: "sql", expression: "0 0 3 * * ?" }
platform_sql:      { key_name: "CLEAN_TEMP", ... DELETE FROM xxx WHERE create_at < :cutoff }
```

**general 型**（JS 业务任务，跨轮上下文）：

```javascript
// platform_api.release_content，code 指向该 API id
const pending = $gl.sql.query("SELECT COUNT(*) c FROM task WHERE status='PENDING'")
if (pending[0].c > 0) {
  // 处理逻辑
  ctx.result = { lastCount: pending[0].c }   // 写回 platform_schedule.context
}
```

**java 型**：`class_info = "cn.geelato.app.task.ReportTask,buildDailyReport"`。

## 排查与运维

- **任务没触发**：确认 `geelato.schedule=true`、`status=1`、节点为单实例（见总览集群警告）、cron 正确；查 `/schedule/runtime/{code}/config` 是否已注册。
- **执行失败**：查 `platform_schedule_log`（status/result）与 `/api/schedule/log`；注意 `retry_times` 暂未实现自动重试。
- **上下文残留**：`platform_schedule.context` 由任务显式写入；`general` 型任务的 `ctx.result` 会跨轮保留，注意清理策略。
- **多实例**：任务幂等化或仅单实例开 `geelato.schedule=true`。
