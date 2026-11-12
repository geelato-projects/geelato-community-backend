---
title: 内置维护
sidebar_label: 内置维护
---

# 内置维护（geelato-maintenance）

内置维护模块面向**基于 Geelato 二次开发的团队**，提供两组开发期自检工具：

- **实体三者同步校验与补偿**（`cn.geelato.metasync`）：检查"Java 实体类 ↔ 实体定义（platform_dev_table）↔ 物理表结构"三方是否一致，并按基准方向补偿修复。
- **MQL Playground**（`cn.geelato.mqltest`）：MQL 在线调试与测试套件，支持 explain 预览、真实执行、身份模拟与场景化回归。

模块坐标 `cn.geelato:geelato-maintenance`，quickstart 已引入；不属于脚手架标准件，业务工程需显式添加依赖。

## 启用方式

模块注册了两个 Spring Boot 自动装配类，各由独立属性开关控制，**默认关闭**：

| 开关 | 控制 | 默认值 |
| --- | --- | --- |
| `geelato.meta.sync.enabled` | 三者同步校验工具（装配页 + REST） | false |
| `geelato.mql.playground.enabled` | MQL Playground（装配页 + REST） | false |

quickstart 的 `application.properties` 默认将两者设为 `true` 并注明**生产环境应设为 false**。两个工具页面的访问路径已内置在安全拦截默认放行名单中，无需额外配置。

## 实体三者同步校验与补偿

"三者"指同一实体的三种存在形态。任何一侧被手工改动（改表、改类、在设计器里改定义）都可能与另两侧漂移，导致运行期查询与预期不符。

| 形态 | 来源 |
| --- | --- |
| Java 类 | 实体类上的 `@Model` / `@Col` 等注解 |
| 实体定义 | 平台设计器表 `platform_dev_table` 及列定义 |
| 物理表 | 数据库 information_schema 中的真实表结构 |

### 页面与 REST

- 页面：`GET /meta-sync.html`（标题"实体三者同步校验工具"）
- REST 前缀：`/api/meta-sync`（宿主启用 `/api` 前缀时；下同）

| 端点 | 说明 |
| --- | --- |
| `GET/POST /api/meta-sync/scan?baseline=table\|java\|meta` | 全量扫描三方，按基准对比；基准决定"以谁为准" |
| `GET /api/meta-sync/check/{tableName}` | 单表一致性检查 |
| `GET /api/meta-sync/diff/{tableName}` | 字段级差异明细（`FieldDiff` / `EntitySyncReport`） |
| `GET /api/meta-sync/entities` | 列出可检查实体 |
| `POST /api/meta-sync/fix/table-to-meta` | 以物理表为准，修正 `platform_dev_table` 元数据 |
| `POST /api/meta-sync/fix/gen-java` | 以物理表/元数据为准，生成 Java 实体源码 |
| `POST /api/meta-sync/fix/java-to-meta` | 以 Java 类为准，修正元数据 |

配置项：`geelato.meta.scan-package-names`（默认 `cn.geelato`），控制 Java 类扫描范围。模块自身无业务表，读取 `platform_dev_table` 与 information_schema。

## MQL Playground

- 页面：`GET /mql-playground.html`（内置 SQL 格式化）
- REST 前缀：`/api/mql`

| 端点 | 说明 |
| --- | --- |
| `POST /api/mql/explain` | MQL→SQL dry-run 预览（query/save/delete 三类），无数据源也可用 |
| `POST /api/mql/validate` | MQL 语法校验 |
| `POST /api/mql/execute` | 主库真实执行（需 primaryJdbcTemplate） |
| `GET /api/mql/entities`、`GET /api/mql/schema/{entity}` | 实体清单与 schema |
| `GET /api/mql/identities/tenants`、`GET /api/mql/identities/users` | 供 runAs 身份模拟（依赖 EnvStore/OrgProvider），使租户与数据权限注入器按生产等效生效 |
| `GET /api/mql/scenarios`、`POST /api/mql/scenarios/run/{id}`、`POST /api/mql/scenarios/runAll` | 场景化回归套件 |
| `POST /api/mql/scenarios/initSchema`、`POST /api/mql/scenarios/cleanup` | 场景测试表初始化与清理 |

场景资源位于模块内 `mql-test-scenarios/*.json`（join、json-column、keywords、view、where-operators 等专题），启动时自动注册 MqlTestOrder/OrderItem/Org/User 测试实体，不影响业务实体。

:::tip
排查 MQL 慢查询或结果不符时，先用 `/api/mql/explain` 看生成的 SQL，再用 runAs 模拟真实身份复现数据权限过滤效果；详见[帮助中心：MQL 调试](../help/troubleshooting.md)。
:::

## 生产环境建议

- 两个开关在生产环境置为 `false`，避免调试端点暴露。
- `fix/*` 补偿端点会直接改写元数据或生成代码，只应在开发/联调环境使用，操作前确认基准方向。
