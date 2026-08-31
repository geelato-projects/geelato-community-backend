---
title: 常见问题
sidebar_label: 常见问题
---

# 常见问题

本页汇总 Geelato 使用中的高频问题。每条答案均可在对应章节找到更详细的说明。

## 入门与选择

### Q1. 框架有哪两种启动方式？如何选择？

两种标准方式（见 [快速开始](../guide/quick-start)）：

- **脚手架启动**（`geelato-app-scaffold-starter`）：开箱即用的"胖脚手架"，自动初始化约 17 张平台基础表，提供登录、组织、用户、角色、字典、上传、MQL。适合从零开始的新项目。
- **最小化接入**（`geelato-framework-starter`）：仅引入底层依赖的"瘦启动"，零建表要求、对现有项目侵入最小。适合已有 Spring Boot 项目，或仅需使用 ORM/MQL 能力。

### Q2. 可以直接复制 `geelato-app-scaffold` 示例工程作为业务项目起点吗？

可以。复制后需调整 `groupId/artifactId/version`、启动类包名与 `scanBasePackages`、`spring.application.name`、数据库连接，并保留对 `geelato-app-scaffold-starter` 与 `geelato-framework-bom` 的依赖。需区分："项目起步形态"可复制示例工程，但"后续公共能力升级入口"仍应依赖 Starter 与 BOM，而非与官方示例目录长期手工同步。

详见 [脚手架快速启动指南](../guide/app-scaffold-starter-project-guide)。

### Q3. 什么时候该从最小样例切到脚手架？

当项目明确需要登录、MQL、组织与用户、字典、上传等基础后台能力时，不建议继续在最小样例上扩展，应直接采用 `geelato-app-scaffold`。其定位是可直接开工的官方脚手架，而非最小样例。

详见 [新项目最小接入](../guide/minimal-integration)。

### Q4. JWT 和 OAuth2 登录的请求头分别怎么带？

所有脚手架接口以 `/api` 开头，登录成功后需在请求头携带 `Authorization`：

- JWT 登录：`Authorization: JWTBearer {token}`
- OAuth2 登录：`Authorization: Bearer {accessToken}`

详见 [脚手架快速启动指南](../guide/app-scaffold-starter-project-guide)。

## 开发约定

### Q5. 什么时候需要自定义 Controller，什么时候直接用 MQL？

若需求仅为单表增删查改、基础分页过滤、常见 MQL 查询，无需单独编写 Controller，直接使用 `/api/meta/*`。以下场景建议补充业务接口：复杂事务编排、多实体聚合操作、特殊权限校验、非标准返回结构。

详见 [脚手架快速启动指南](../guide/app-scaffold-starter-project-guide)。

### Q6. 业务实体和建表脚本应该放在哪里？

都放在业务工程内，不要下沉到 `geelato-app-scaffold-starter`：

- 实体类：`src/main/java/<业务包>/entity/`
- 建表脚本：`src/main/resources/geelato/app/scaffold/init/`（文件名必须与表名一致，仅用于首次建表，非通用迁移工具）

详见 [脚手架快速启动指南](../guide/app-scaffold-starter-project-guide)。

### Q7. `scanBasePackages` 有什么坑？

`scanBasePackages` 必须同时包含 `cn.geelato` 与业务包：只写业务包会导致框架控制器与自动装配组件缺失；只写 `cn.geelato` 会导致业务实体、Controller、Service 不被扫描。此外，`geelato.meta.scan-package-names` 与 `geelato.graal.scan-package-names` 默认只扫描 `cn.geelato`，业务包未加入时 MQL 将无法识别业务实体。

详见 [脚手架快速启动指南](../guide/app-scaffold-starter-project-guide)、[启动过程](../reference/startup-process)。

## 数据访问

### Q8. MQL 和 Fluent DSL 是什么关系？

两者解决不同层级的问题：MQL 面向前端与平台协议侧，用 JSON 描述查询与写入；Fluent DSL 面向后端 Java 服务，用 `MetaFactory` 链式调用访问数据。推荐边界：页面、平台通用控制器、低代码场景用 MQL；后端标准 CRUD、轻量 join、过程调用用 Fluent DSL；超复杂 SQL 或复杂结果映射用原生 SQL / MyBatis。

详见 [MQL](../mql/overview)。

### Q9. 有多个 `Dao` 时 ORM 绑定哪一个？

自动解析优先绑定 `dynamicDao`（只有它能支撑 `useDataSource(connectId)` 切库），不存在时回退 `primaryDao`，再回退唯一的 Dao Bean——多 `Dao` 场景无需显式配置。`geelato.orm.dao-bean-name` 仅在绑定其他自定义 Dao 时使用。

详见 [Fluent DSL 指引](../orm/fluent-dsl)、[ORM / 数据源扩展](../orm/datasource-extension)。

### Q10. 框架支持哪些数据库？

JDBC 驱动由业务工程自行引入。动态数据源层（`DataSourceFactory`）显式支持 `mysql` 与 `postgresql`/`postgres`（基于 HikariCP），其他 `db_type` 会抛出"不支持的数据库类型"。

详见 [Fluent DSL 指引](../orm/fluent-dsl)、[ORM / 数据源扩展](../orm/datasource-extension)。

## 扩展与插件

### Q11. 插件能通过 HTTP 安装/卸载吗？

不能。当前没有上传/安装或卸载/删除插件的 REST 端点。安装方式是将插件 jar 或目录放入 `plugins` 目录；`/api/pm/switchStatus` 仅做运行期启用/禁用（`startPlugin`/`stopPlugin`），不安装、不卸载、不删除文件。完整物理移除需：停止 → 删除文件 → 重启。

详见 [插件机制概览](../plugin-mechanism/overview)、[插件加载、启停与卸载](../plugin-mechanism/lifecycle)。

### Q12. 自定义 `MetaStore` 启动报 `NoUniqueBeanDefinitionException` 怎么办？

需在自定义实现上加 `@Primary`（默认 `DefaultMetaStore` 是 `@Component` 但无 `@Primary`，注入点无 `@Qualifier`），并确保包被 `geelato.meta.scan-package-names` 覆盖。不要依赖 Bean 名称覆盖（`allow-bean-definition-overriding` 默认 `false`）。

详见 [MetaStore 扩展](../reference/metastore-extension)。

### Q13. 同一类 SPI 注册了多个实现会怎样？

所有查询过滤与字段填充 SPI 遵循统一规则：0 个实现跳过、1 个实现按 `isEnabled()` 生效、多个实现直接报错。需在上层工程收敛为单个启用的实现。

详见 [查询过滤与字段填充 SPI 扩展](../reference/spi-query-filter-and-save-fill-extension)。

### Q14. 动态数据源热刷新改了配置却不生效？

需同时完成两步：`DynamicDataSourceRegistry.refreshDataSource(key)` 重建配置与连接池，再 `DynamicRoutingDataSource.refreshDataSource(key)` 重建路由表。漏掉第二步是"配置已改但路由仍指向旧池"的最常见原因。

详见 [ORM / 数据源扩展](../orm/datasource-extension)。

## 建表与升级

### Q15. `auto-init-tables=true` 会自动 ALTER 已存在的表吗？

不会。它仅适用于"首次建表"，不负责已存在表的字段新增、类型修改、自动 `ALTER TABLE` 或复杂版本迁移。表结构演进需使用项目自有的数据库变更流程，不应依赖"重启应用自动改表"。

详见 [脚手架快速启动指南](../guide/app-scaffold-starter-project-guide)。
