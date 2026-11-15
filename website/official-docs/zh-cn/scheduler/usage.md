---
title: 调度任务配置与操作
sidebar_label: 任务配置与操作
---

# 调度任务配置与操作

本页面向需要在平台中配置定时任务的业务开发者与运维人员，说明如何配置一个定时任务、如何启动/停止任务、以及如何查看执行日志与排查问题。调度能力的定位与部署注意见[调度总览](overview.md)。

## 1. 配置一个定时任务

1. **准备执行体**（按任务类型三选一）：
   - `sql` 型：先在平台 **SQL 管理**（`platform_sql`）中登记 SQL（`key_name`）；
   - `java` 型：确认目标 Bean 已在应用容器中，格式为 `class_info = "全类名,方法名"`，方法无参或按框架约定注入；
   - `general` 型：在平台 **API 管理**（`platform_api`）中发布 JS 脚本，脚本内使用 `$gl` 服务，`ctx.result` 可跨轮传递数据。
2. **创建任务**：在管理端/Designer 的「调度管理」页面新建任务，或调用 `POST /api/schedule/createOrUpdate`，填写 `code`（唯一）、`expression`（cron 表达式）、`type`、`params`（JSON 参数）、`app_id` 等字段（字段说明见下节）。
3. **启用任务**：将 `status` 置为 `1`（列表页「启用」按钮，或 `GET /api/schedule/status/{id}/1`）。
4. **使其生效**：新创建/修改的任务在**下一次应用重启装载**或调用热更新接口后生效，详见下文「生效时机」。

## 2. 任务配置字段

任务登记在 `platform_schedule` 表中，主要配置字段如下（完整字段定义见[调度集成与日志实体](../entities/schedule-integration-log.md)）：

| 字段 | 必填 | 说明 |
| :--- | :--- | :--- |
| `code` | ✅ | 任务唯一码；`sql`/`general` 型即指向的 SQL/API 标识 |
| `type` | ✅ | 任务类型：`sql` / `java` / `general` |
| `expression` | ✅ | cron 表达式（6 位：秒 分 时 日 月 周，日/周位支持 `?`），如 `0 0 3 * * ?` 表示每天凌晨 3 点 |
| `app_id` | 建议 | 所属应用 |
| `name` | 建议 | 任务显示名称 |
| `params` | 可选 | JSON 格式参数，任务执行时可读取 |
| `class_info` | 按类型 | 仅 `java` 型使用，格式 `"全类名,方法名"` |
| `status` | — | 任务状态：`0` 停止 / `1` 启动，新建默认停止 |
| `retry_times` | — | 重试次数（已预留，自动重试暂未实现） |
| `context` | — | 跨轮持久化上下文，由任务自行写入（如 `general` 型的 `ctx.result`） |
| `description` | 可选 | 任务描述 |

`last_start_time` / `last_finish_time` 由调度器在每次执行时自动回写，用于展示最近执行情况。

## 3. 三种类型示例

**sql 型**（定期数据清理）：

```
platform_schedule: { code: "CLEAN_TEMP", type: "sql", expression: "0 0 3 * * ?" }
platform_sql:      { key_name: "CLEAN_TEMP", ... DELETE FROM xxx WHERE create_at < :cutoff }
```

**general 型**（JS 业务任务，跨轮上下文）：

```javascript
// platform_api 中发布的脚本，任务 code 指向该 API
const pending = $gl.sql.query("SELECT COUNT(*) c FROM task WHERE status='PENDING'")
if (pending[0].c > 0) {
  // 处理逻辑
  ctx.result = { lastCount: pending[0].c }   // 写回任务 context，下一轮执行可读取
}
```

**java 型**：`class_info = "cn.geelato.app.task.ReportTask,buildDailyReport"`。

## 4. 启停与运行操作

### 4.1 界面操作

管理端/Designer 的「调度管理」页面提供任务的完整管理入口：

- **新建/编辑任务**：表单填写上述配置字段，支持 cron 校验；
- **启用/停用**：列表页对停止任务提供「启用」、启动任务提供「停用」按钮（均有二次确认）；
- **执行日志**：任务列表可进入对应日志页查看每次执行记录。

### 4.2 REST 管理面 `/api/schedule`（带 `/api` 前缀）

| 端点 | 说明 |
| :--- | :--- |
| `POST /pageQuery`、`GET /query`、`GET /get/{id}` | 任务分页 / 列表 / 详情 |
| `POST /createOrUpdate`、`DELETE /isDelete/{id}`、`POST /validate` | 创建或更新 / 删除 / 校验 |
| `GET /status/{id}/{status}` | 启用（1）/ 停用（0） |
| `/api/schedule/log/pageQuery`、`GET /api/schedule/log/query` | 执行日志分页 / 列表 |

### 4.3 运行时控制 `/schedule/runtime`（**不带 `/api` 前缀**）

| 端点 | 说明 |
| :--- | :--- |
| `GET /{code}/start` | 立即触发执行一次（不影响周期调度） |
| `GET /{code}/stop` | 取消该任务当前一次触发 |
| `GET /{code}/config` | 查询该任务当前已注册的触发配置 |
| `GET /{code}/config/refresh` | 热更新：修改库中配置后调用，免重启生效 |

### 4.4 生效时机

- **新建任务 / 修改已停止任务的启用**：在下一次应用重启装载，或调用 `GET /schedule/runtime/{code}/config/refresh` 后生效；
- **修改运行中任务的 cron 或参数**：调用 `GET /schedule/runtime/{code}/config/refresh` 立即生效，无需重启；
- **停用任务**：立即停止后续触发，进行中的本次执行不受影响。

## 5. 执行日志与排查

每次任务执行都会写入一条日志（`platform_schedule_log`）：`status`（`execute` 执行中 → `success` / `fail`）、`start_at` / `finish_at`、`result`（结果摘要）。可在日志页或 `/api/schedule/log` 查询。

常见问题排查：

- **任务没触发**：依次确认 `geelato.schedule=true` 已开启、任务 `status=1`、节点为单实例或多实例幂等（见[总览集群警告](overview.md)）、cron 表达式正确；再查 `GET /schedule/runtime/{code}/config` 确认任务已注册。
- **执行失败**：查日志 `status=fail` 的 `result` 摘要与 `finish_at`；注意自动重试暂未实现（`retry_times` 不生效），失败任务不会自动重跑。
- **上下文残留**：`context` 由任务显式写入，`general` 型任务的 `ctx.result` 会跨轮保留——如果不需要跨轮传递，请勿设置 `ctx.result`，或自行覆盖清理。
- **多实例重复执行**：见总览「集群行为」——仅单实例开启调度，或确保任务幂等。
