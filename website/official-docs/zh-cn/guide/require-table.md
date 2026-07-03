# RequireTable

这篇文档说明基于 `geelato-app-scaffold-starter` 创建业务工程时，脚手架默认依赖哪些基础表，以及这些表如何在启动时自动检测并初始化。

对应脚本目录位于：

- `geelato-app-scaffold-starter/init`

对应启动初始化逻辑位于：

- `cn.geelato.app.scaffold.boot.AppScaffoldSchemaInitializer`

## 为什么需要这些表

`geelato-app-scaffold-starter` 不是一个只提供空壳 Web 启动器的 starter。

它默认承载了一批平台运行时基础能力，例如：

- 元数据表结构
- 字典
- 组织
- 用户
- 角色与权限
- 系统配置

因此，业务工程只要想直接消费脚手架内置能力，就必须具备这些基础表。

换句话说：

- 你的业务表由你自己维护
- 脚手架底座依赖的基础平台表由 starter 负责提供初始化脚本

## 当前 RequireTable 清单

`geelato-app-scaffold-starter/init` 当前包含以下建表脚本：

- `platform_dev_column.sql`
- `platform_dev_db_connect.sql`
- `platform_dev_table.sql`
- `platform_dev_table_check.sql`
- `platform_dev_table_foreign.sql`
- `platform_dev_view.sql`
- `platform_dict.sql`
- `platform_dict_item.sql`
- `platform_org.sql`
- `platform_org_r_user.sql`
- `platform_permission.sql`
- `platform_role.sql`
- `platform_role_r_permission.sql`
- `platform_role_r_user.sql`
- `platform_sys_config.sql`
- `platform_user.sql`
- `platform_user_r_permission.sql`

从职责上可以理解为几类。

### 元数据与设计时基础表

- `platform_dev_table`
- `platform_dev_column`
- `platform_dev_view`
- `platform_dev_table_check`
- `platform_dev_table_foreign`
- `platform_dev_db_connect`

这些表主要承载：

- 表元数据
- 列元数据
- 视图元数据
- 表检查规则
- 外键关系
- 动态数据源连接定义

### 字典能力基础表

- `platform_dict`
- `platform_dict_item`

### 组织与用户基础表

- `platform_org`
- `platform_user`
- `platform_org_r_user`

### 权限模型基础表

- `platform_role`
- `platform_permission`
- `platform_role_r_permission`
- `platform_role_r_user`
- `platform_user_r_permission`

### 系统配置基础表

- `platform_sys_config`

## 这些脚本已经在 starter 里

这些 RequireTable 的建表语句不是要求业务方自己手工复制一遍。

它们已经随 `geelato-app-scaffold-starter` 一起提供。

也就是说，starter 本身已经内置了这些脚本资源，业务工程只要正常依赖 starter，并开启对应能力，就可以在启动时自动走初始化逻辑。

## 启动时如何自动判断并创建

负责这件事的类是：

- `AppScaffoldSchemaInitializer`

它实现了：

- `CommandLineRunner`
- `Ordered`

并以：

- `Ordered.HIGHEST_PRECEDENCE`

优先级在应用启动阶段尽早执行。

## 自动初始化的执行流程

当前逻辑可以概括为：

1. 扫描 `classpath*:geelato/app/scaffold/init/*.sql`
2. 按资源名排序
3. 使用 `dao.getJdbcTemplate().getDataSource().getConnection()` 取得数据库连接
4. 对每个 SQL 文件，根据文件名推导目标表名
5. 通过 `DatabaseMetaData.getTables(...)` 判断该表是否已存在
6. 如果表已存在，则跳过当前脚本
7. 如果表不存在，则用 `ScriptUtils.executeSqlScript(...)` 执行脚本

也就是说，starter 不是“无脑每次全量执行建表 SQL”，而是：

- 先判断表是否存在
- 不存在才执行

## 表名如何推导

当前实现里，表名来源于：

- SQL 文件名去掉扩展名后的部分

例如：

- `platform_user.sql` -> `platform_user`
- `platform_dict_item.sql` -> `platform_dict_item`

因此 starter 初始化脚本的一个重要约定就是：

- 文件名必须和目标表名一致

## 存在检查怎么做

当前 `AppScaffoldSchemaInitializer` 会通过 `DatabaseMetaData` 进行存在性检查，并分别尝试：

- 原始表名
- 大写表名
- 小写表名

这主要是为了兼容不同数据库或不同驱动对表名大小写处理的差异。

## 执行脚本时的编码

脚本执行使用的是：

- `new EncodedResource(resource, "UTF-8")`

这意味着 starter 期望这些 SQL 资源统一采用：

- UTF-8

这也和当前整个项目对 UTF-8 编码一致性的要求保持一致。

## 自动建表的边界

这个自动初始化能力非常适合：

- 新库第一次启动
- 脚手架底座基础表初始化
- 业务工程首次接入 starter

但它不负责：

- 已存在表的结构升级
- 字段新增
- 字段类型变更
- 自动 `ALTER TABLE`
- 复杂版本迁移

因此要把它理解为：

- 首次建表初始化器

而不是：

- 通用数据库迁移框架

## 对业务工程意味着什么

如果你基于 starter 新建业务项目，推荐这样理解表的责任边界：

- starter 负责脚手架底座的 RequireTable
- 业务工程负责自己的业务表
- 业务工程自己的表脚本放在 `src/main/resources/geelato/app/scaffold/init/`

这两类脚本会一起参与“首次建表”语义，但职责不同：

- starter 提供平台底座
- 业务工程提供业务实体表

## 推荐检查项

当你第一次启动业务工程时，建议至少确认：

- starter 已正常被依赖
- `geelato.app.scaffold.enabled=true`
- `geelato.app.scaffold.auto-init-tables=true`
- 主数据库账号有建表权限
- 上述 RequireTable 已在数据库中出现

如果启动后这些表没有出现，优先检查：

- starter 是否被正确引入
- 初始化配置是否开启
- 数据库连接是否指向了正确库
- 当前数据库用户是否有 `CREATE TABLE` 权限

## 推荐继续阅读

- [项目接入](app-scaffold-starter-project-guide.md)
- [系统配置](../system-config/overview.md)
- [动态数据源](../dynamic-datasource/overview.md)
