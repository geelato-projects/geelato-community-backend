---
title: 打包内容与运行作用
sidebar_label: 打包内容
---

# 打包内容与运行作用

本页说明应用包携带哪些内容，以及各类内容在应用运行期分别起什么作用。打包范围由平台表白名单与实体的数据打包策略共同决定：19 张平台表按应用维度全量打包，业务数据表按建模时配置的 `pack_bus_data` 策略决定是否携带。

## 打包范围机制

打包时对每张表执行 `select *`，按以下维度圈定行范围：

| 表 | 打包维度 | 说明 |
| --- | --- | --- |
| `platform_app` | 按 `id` | 应用本体单行，写入包头 `appCode/appName`，不进入 `appMetaList` |
| `platform_tree_node` | 按 `tree_id` | 菜单树以应用 id 作为树根 |
| 其余平台表（17 张） | 按 `app_id` | 白名单见下文各层 |
| 业务数据表 | 按 `app_id` | 仅为建模时 `pack_bus_data` 大于 0 且启用的实体表 |

可打包的平台表由白名单 `PlatformTableConstant` 限定，应用包不可能携带白名单之外的表（如租户、用户、平台级配置）。

## 应用本体

| 表 | 实体 | 携带内容 |
| --- | --- | --- |
| `platform_app` | `App` | 应用名称/编码/图标/主题/首页链接/水印、设计器文件树 `tree`（jsTree JSON）、依赖声明 `depend_app_code`、应用密钥 `app_key` 与 `token`、版本信息 `version_info` |

运行期作用：应用注册与进入的依据——用户可见的应用列表由"角色-应用"关系（`platform_role_r_app`）计算；`depend_app_code` 声明依赖的应用模块编码（如 `dev,sys`），供前端启动时加载依赖模块；包头的 `basePlatformVersion` 取自平台应用 `geelato_admin` 的 `version_info`，部署校验失败时的升级提示以此为准。

## 界面层

| 表 | 实体 | 携带内容 | 运行期作用 |
| --- | --- | --- | --- |
| `platform_app_page` | `AppPage` | 页面源内容 `source_content` 与发布内容 `release_content`（组件树 JSON）、签出状态 | 运行态前端经 `GET /api/page/getPageAndCustom/{pageId}` 拉取 `release_content` 渲染页面；页面内嵌的事件逻辑随页面 JSON 一起打包 |
| `platform_tree_node` | `TreeNode` | 菜单树（`tree_id` 为应用 id），叶子节点 `extend_id` 指向页面 | 前端按应用拉取树节点构造导航菜单；点击菜单按 `extend_id` 定位页面渲染 |

## 数据层

| 表 | 实体 | 携带内容 | 运行期作用 |
| --- | --- | --- | --- |
| `platform_dev_db_connect` | `ConnectMeta` | 数据库连接定义（dbType/地址/库名/账号等） | 启动后注册为动态数据源；实体的 `connect_id` 将其路由到所属库 |
| `platform_dev_table` | `TableMeta` | 实体定义：实体名/表名/所属数据源/`pack_bus_data` 数据打包策略/跨应用标记 | 与下列三张表一起在启动时由 `MetaManager` 解析为内存 `EntityMeta`，驱动 ORM 读写、通用 GQL 接口（`/api/meta/list|save|delete|tree`）与 MQL 命令，无需为每张表编写 Java 代码 |
| `platform_dev_column` | `ColumnMeta` | 字段定义：列名/类型/标题/校验 | 同上；接口出参、页面表单字段均由字段元数据推导 |
| `platform_dev_table_foreign` | `TableForeign` | 外键/约束 | 关联关系校验与级联依据 |
| `platform_dev_view` | `TableView` | 视图定义（含物化视图） | 视图查询与物化加速 |

数据层是应用包的核心：部署落库后按实体逐个刷新元数据缓存，模型变更即热生效。

## 业务数据

业务数据是否随包携带由建模时配置的数据打包策略决定（`platform_dev_table.pack_bus_data`）：

| 策略值 | 含义 | 打包行为 | 部署行为 |
| --- | --- | --- | --- |
| `0` | 不打包 | 不进包 | — |
| `1` | 增量 | 携带该表本应用全部行 | 不清空目标库，已存在同 id 的行跳过（幂等合并） |
| `2` | 全量 | 携带该表本应用全部行 | 清空目标库本应用旧行后插入包内行 |

典型用法：字典性质的初始化数据、基础参数用增量；需要随应用整体迁移替换的数据用全量。策略在模型设计器的实体表单中配置。

## 治理层

| 表 | 实体 | 携带内容 | 运行期作用 |
| --- | --- | --- | --- |
| `platform_role` | `Role` | 角色（`type` 区分应用级/平台级） | 授权体系的主体 |
| `platform_permission` | `Permission` | 权限点与数据规则 `rule`（MQL 片段） | `rule` 在数据读写时拼入查询，实现行级数据过滤 |
| `platform_role_r_permission` | `RolePermissionMap` | 角色-权限关系 | 决定角色拥有的权限点 |
| `platform_role_r_tree_node` | `RoleTreeNodeMap` | 角色-菜单关系 | 决定角色可见菜单 |
| `platform_role_r_app` | `RoleAppMap` | 角色-应用关系 | 决定用户可进入哪些应用 |
| `platform_dict` / `platform_dict_item` | `Dict` / `DictItem` | 字典与字典项 | 前端下拉/标签渲染与取值翻译（`GET /api/system/dictionary/{code}`） |
| `platform_sys_config` | `SysConfig` | 应用配置键值 | 与平台级、租户级配置三层合并后经 `GET /api/config` 下发 |
| `platform_export_template` | `ExportTemplate` | 导出模板 | 列表/报表导出的格式定义 |
| `platform_encoding` | `Encoding` | 编码规则 | 业务单号/编码的按模板生成 |
| `platform_resources` | `Resources` | 资源元数据行（路径/名称/类型等） | 资源索引；**不含文件二进制内容**（见下） |

## 不在打包清单中的内容

:::warning 跨环境迁移需另行处理
以下内容**不随应用包携带**，迁移应用时需单独安排：

| 内容 | 对应表 | 说明 |
| --- | --- | --- |
| 服务接口 | `platform_api` | 独立的服务接口脚本（GraalJS），不在白名单中；页面内嵌逻辑不受影响，但依赖独立接口的页面部署后接口缺失 |
| 接口编排 | `platform_sql` | 自定义 SQL 编排（`/api/sk/{key}`） |
| 定时任务 | `platform_schedule` | cron 任务定义 |
| 文件二进制内容 | `platform_file` 等 | `platform_resources` 仅携带资源元数据行，附件/图片等文件本体需按文件存储另行迁移 |
| 被引用的其他应用内容 | — | 跨应用引用的实体/页面不随本包携带（见下节） |
:::

## 依赖与跨应用引用

- 应用间依赖通过 `platform_app.depend_app_code` 声明（逗号分隔的应用编码），打包**不做依赖分析**：被依赖应用的内容不会自动带入包内，部署前需人工保证目标环境已存在被依赖应用。
- 跨应用模型/视图/接口编排的授权挂载（`platform_app_r_table/view/sql`）不在打包清单中，目标环境需重新授权。
- 实体元数据全局按实体名注册（不过滤应用），跨应用引用实体可直接访问，访问控制交由权限规则；`platform_dev_table.across_app` 仅为模型级标记。

## 推荐继续阅读

- [打包流程](packaging-flow.md)：如何把上述内容打成 `.zgdp`
- [应用包部署](deployment-flow.md)：覆盖与增量两种落库策略如何应用这些内容
- [元数据说明](../metadata/overview.md)：`EntityMeta`/`MetaManager` 的解析与加载机制
