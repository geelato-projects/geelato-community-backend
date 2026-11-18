---
title: API · 数据建模
sidebar_label: 数据建模
---
# 数据建模（model）

7 个 Controller，共 48 个端点。本域全部为设计态接口（@DesignTimeApiRestController），主要供设计器使用。

## DevDbConnectController ⚙ — `/model/connect`

设计态数据库连接（ConnectMeta）管理：增删改查、连接测试与动态数据源刷新。

### pageQuery 分页查询连接

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/connect/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult<ConnectMeta>`：数据库连接配置分页列表（连接名、数据库类型、主机、端口、库名、schema、用户名等）。 |

### query 全量查询连接

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/connect/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<ConnectMeta>>`：连接配置列表（签名声明为原始 `ApiResult`）。 |

### get 连接详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/connect/get/{id}` |
| 参数 | `id`：path，string，必填，连接 ID。 |
| 返回 | `ApiResult<ConnectMeta>`：连接配置详情（签名声明为原始 `ApiResult`）。 |

### createOrUpdate 新建或更新连接

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/connect/createOrUpdate` |
| 参数 | `form`：body，ConnectMeta 对象，必填，常用字段：连接名 `dbConnectName`、数据库类型 `dbType`、主机 `dbHostnameIp`、端口 `dbPort`、数据库 `dbName`、schema `dbSchema`、用户名 `dbUserName`、密码 `dbPassword`。 |
| 返回 | `ApiResult<ConnectMeta>`：先按 类型+主机+端口+库名+应用+租户 查重（重复时报错）；`id` 非空走更新（schema 变更时同步升级元数据），否则创建并立即刷新动态数据源，返回保存后的连接。 |

### isDelete 删除连接

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/model/connect/isDelete/{id}` |
| 参数 | `id`：path，string，必填，连接 ID。 |
| 返回 | `ApiResult<NullResult>`：连接已被表/视图/检查约束关联时失败；否则置禁用后逻辑删除，`data` 为空。 |

### jdbcConnect 连接测试

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/connect/jdbcConnect` |
| 参数 | `form`：body，ConnectMeta 对象，必填，按 `dbType` 定位驱动与 URL 格式进行测试。 |
| 返回 | `ApiResult<Boolean>`：是否可建立有效 JDBC 连接。 |

### batchCreate 批量复制连接

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/connect/batchCreate` |
| 参数 | `appId`：body，string，必填，目标应用 ID。<br />`connectIds`：body，string 数组，必填，源连接 ID 列表。<br />`userName`：body，string，必填，数据库用户名。<br />`password`：body，string，必填，数据库密码。 |
| 返回 | `ApiResult<NullResult>`：按源连接复制为新连接（覆盖用户名、密码与应用），已存在相同连接时报错；`data` 为空。 |

### refresh 刷新单个数据源

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/connect/refresh/{id}` |
| 参数 | `id`：path，string，必填，连接 ID。 |
| 返回 | `ApiResult<Boolean>`：刷新是否成功；连接不存在或动态数据源能力未启用时失败。 |

### refreshAll 刷新全部数据源

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/connect/refreshAll` |
| 参数 | 无 |
| 返回 | `ApiResult<Integer>`：刷新的数据源数量；动态数据源能力未启用时失败。 |

## DevTableCheckController ⚙ — `/model/table/check`

设计态数据表检查约束（TableCheck）的增删改查与重名校验。

### pageQuery 分页查询检查

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/check/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult<DataItems>`：`data` 为 `DataItems`（`total` 总数 + `items` 检查约束记录列表）。 |

### query 全量查询检查

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/check/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<TableCheck>>`：检查约束列表（签名声明为原始 `ApiResult`）。 |

### get 检查详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/check/get/{id}` |
| 参数 | `id`：path，string，必填，检查约束 ID。 |
| 返回 | `ApiResult<TableCheck>`：检查约束详情（签名声明为原始 `ApiResult`）。 |

### createOrUpdate 新建或更新检查

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/check/createOrUpdate` |
| 参数 | `form`：body，TableCheck 对象，必填，常用字段：标题 `title`、编码 `code`、类型 `type`、检查子句 `checkClause`、所属表 `tableId`、关联字段 `columnId`（`connectId`、`tableName` 等由所属表自动回填）。 |
| 返回 | `ApiResult<TableCheck>`：`synced` 置为未同步后保存，并刷新所属表的实体元数据缓存（签名声明为原始 `ApiResult`）。 |

### isDelete 删除检查

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/model/table/check/isDelete/{id}` |
| 参数 | `id`：path，string，必填，检查约束 ID。 |
| 返回 | `ApiResult<NullResult>`：逻辑删除并刷新所属表的实体元数据缓存，`data` 为空。 |

### validate 检查重名校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/check/validate` |
| 参数 | `form`：body，TableCheck 对象，必填，`tableId` 与 `type` 不能为空，以 `code` 为查重键。 |
| 返回 | `ApiResult<Boolean>`：先查 `platform_dev_table_check` 是否已存在同名同类型记录，再切换至目标连接检查数据库中的同名检查约束；两者均不存在返回 `true`（连接不存在或已存在返回 `false`）。 |

## DevTableColumnController ⚙ — `/model/table/column`

设计态数据表字段（ColumnMeta）的增删改查、字段模板与选择类型查询、重名校验。

### pageQuery 分页查询字段

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/column/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult<ColumnMeta>`：字段分页列表。 |

### query 全量查询字段

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/column/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<ColumnMeta>>`：字段列表（签名声明为原始 `ApiResult`）。 |

### get 字段详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/column/get/{id}` |
| 参数 | `id`：path，string，必填，字段 ID。 |
| 返回 | `ApiResult<ColumnMeta>`：字段详情（签名声明为原始 `ApiResult`）。 |

### createOrUpdate 新建或更新字段

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/column/createOrUpdate` |
| 参数 | `form`：body，ColumnMeta 对象，必填，常用字段：字段名 `name`、标题 `title`、字段类型 `type`/`dataType`、所属表 `tableId`、选择类型 `selectType`、自动添加 `autoAdd`/`autoName` 等。 |
| 返回 | `ApiResult<ColumnMeta>`：更新时若字段改名，先在数据库复制旧字段并联动修正字段权限；新建时标记未同步并重置表默认字段权限；选择类型为组织/用户且开启自动添加时自动生成关联字段；最后刷新默认视图与实体元数据缓存（签名声明为原始 `ApiResult`）。 |

### insertCommon 复制字段到表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/column/insertCommon` |
| 参数 | `tableId`：body，string，必填，目标表 ID。<br />`columnIds`：body，string，必填，待复制的字段 ID，多个以逗号分隔。<br />`tableName`：body，string，代码读取但未参与逻辑。 |
| 返回 | `ApiResult<NullResult>`：将既有字段复制到目标表（同名跳过、标记未同步），并刷新默认视图与实体元数据缓存；`data` 为空。 |

### isDelete 删除字段

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/model/table/column/isDelete/{id}` |
| 参数 | `id`：path，string，必填，字段 ID。 |
| 返回 | `ApiResult<NullResult>`：逻辑删除并刷新默认视图与实体元数据缓存，`data` 为空。 |

### queryDefaultMeta 默认字段模板

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/column/queryDefaultMeta` |
| 参数 | 无 |
| 返回 | `ApiResult<List<ColumnMeta>>`：内置默认字段模板列表。 |

### selectType 选择类型列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/column/selectType` |
| 参数 | 无 |
| 返回 | `ApiResult<List<ColumnSelectType>>`：字段可选的数据类型（分组 `group`、标题 `label`、取值 `value`、数据库类型 `mysql`、是否禁用、默认长度、取值范围等）。 |

### validate 字段重名校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/column/validate` |
| 参数 | `form`：body，ColumnMeta 对象，必填，使用 `name`、`tableId`、`appId`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：校验同一表下字段名是否重复（查 `platform_dev_column`），可用返回 `true`。 |

## DevTableController ⚙ — `/model/table`

设计态数据表（TableMeta）的增删改查、复制、升级、默认视图与按库重置。

### pageQuery 分页查询表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult<TableMeta>`：数据表分页列表。 |

### query 全量查询表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<TableMeta>>`：数据表列表。 |

### get 表详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/get/{id}` |
| 参数 | `id`：path，string，必填，表 ID。 |
| 返回 | `ApiResult<TableMeta>`：数据表详情。 |

### createOrUpdate 新建或更新表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/createOrUpdate` |
| 参数 | `form`：body，TableMeta 对象，必填，常用字段：实体名 `entityName`、标题 `title`、连接 `connectId`、表类型 `tableType`、来源 `sourceType` 等（`tableSchema`、`dbType` 由连接自动回填）。 |
| 返回 | `ApiResult<TableMeta>`：新建时标记未同步、初始化表默认权限，非第三方来源自动创建默认字段；更新时实体名变更会备份旧表并在数据库重命名（联动权限修正与默认视图刷新）；最后刷新实体元数据缓存，返回保存后的表（签名声明为 `ApiResult<?>`）。 |

### copy 复制表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/copy` |
| 参数 | `id`：body，string，必填，源表 ID。<br />`entityName`：body，string，必填，新表实体名。<br />`title`：body，string，可选，新表标题。<br />`connectId`：body，string，可选，新表连接 ID。<br />`tableComment`：body，string，可选，新表注释。<br />`appId`：body，string，可选，应用 ID。<br />`tenantCode`：body，string，可选，租户代码。 |
| 返回 | `ApiResult<TableMeta>`：复制源表及其字段为新表（来源类型重置为手工创建），初始化默认权限并刷新实体缓存。 |

### upgradesTable 升级表结构

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/upgradesTable` |
| 参数 | `tableId`：body，string，必填，目标表 ID。<br />`columnNames`：body，string，必填，逗号分隔的升级模板字段名。<br />`upgradeType`：body，string，可选，含 `app` 时开启跨应用、含 `workflow` 时开启跨流程。 |
| 返回 | `ApiResult<NullResult>`：为表补齐升级模板中缺失的字段，按 `upgradeType` 更新跨应用/跨流程标记并刷新实体缓存；成功时 `data` 为空（签名声明为 `ApiResult<?>`）。 |

### isDelete 删除表

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/model/table/isDelete/{id}` |
| 参数 | `id`：path，string，必填，表 ID。 |
| 返回 | `ApiResult<NullResult>`：逻辑删除（数据库表改名加 `_d` 时间戳后缀、标题与注释加"已删除"前缀），并移除实体元数据缓存；`data` 为空。 |

### queryDefaultView 默认视图SQL

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/queryDefaultView/{entityName}` |
| 参数 | `entityName`：path，string，必填，实体名。 |
| 返回 | `ApiResult<String>`：按实体字段生成的默认视图 SQL（`viewConstruct`）。 |

### resetDefaultView 重置默认视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/resetDefaultView` |
| 参数 | `form`：body，TableMeta 对象，必填，`entityName` 必填（另以 `id`、`connectId` 匹配表记录）。 |
| 返回 | `ApiResult<NullResult>`：为匹配的表重建或更新默认视图，`data` 为空。 |

### resetModelFormTable 按库重置表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/reset/{tableId}` |
| 参数 | `tableId`：path，string，必填，表 ID；禁用状态的表不可重置。 |
| 返回 | `ApiMetaResult<NullResult>`：按数据库实际结构反向同步表定义（更新表信息并重置字段与外键；库中无此表时逻辑删除表），`data` 为空（含 `meta` 扩展）。 |

### validate 表名重名校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/validate` |
| 参数 | `form`：body，TableMeta 对象，必填，使用 `entityName`、`connectId`、`appId`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：校验同一连接下实体名是否重复；若开启冲突检测且与已加载的 Java 内置实体同名，直接返回失败消息。 |

## DevTableForeignController ⚙ — `/model/table/foreign`

设计态数据表外键/关联关系（TableForeign）的增删改查。

### pageQuery 分页查询外键

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/foreign/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult<TableForeign>`：外键关系分页列表。 |

### query 全量查询外键

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/foreign/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<TableForeign>>`：外键关系列表（签名声明为原始 `ApiResult`）。 |

### get 外键详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/table/foreign/get/{id}` |
| 参数 | `id`：path，string，必填，外键 ID。 |
| 返回 | `ApiResult<TableForeign>`：外键关系详情。 |

### createOrUpdate 新建或更新外键

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/foreign/createOrUpdate` |
| 参数 | `form`：body，TableForeign 对象，必填，常用字段：主表 `mainTable`/`mainTableId`/`mainTableCol`、外键表 `foreignTable`/`foreignTableId`/`foreignTableCol`、删除动作 `deleteAction`、更新动作 `updateAction` 等。 |
| 返回 | `ApiResult<TableForeign>`：`id` 非空更新，否则新建（签名声明为原始 `ApiResult`）。 |

### isDelete 删除外键

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/model/table/foreign/isDelete/{id}` |
| 参数 | `id`：path，string，必填，外键 ID。 |
| 返回 | `ApiResult<NullResult>`：置禁用后逻辑删除，`data` 为空。 |

## DevTableImportController ⚙ — `/model/table/import`

以原始 JSON 原样导入表/字段/检查约束，可保留外部主键或由系统重建主键。

### importRaw 原样导入保留ID

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/import/preserveId` |
| 参数 | `bodyJson`：body，string（原始 JSON 文本），必填，导入 JSON 须包含 `table`（对象）与 `columns`（数组）节点，`checks`（数组）可选；三部分所有记录必须携带 `id`。<br />`ddl`：query，boolean，可选，默认 `true`，导入成功后是否立即创建/更新数据库表结构。 |
| 返回 | `ApiResult<Map<String,Object>>`：导入计数 `tables`/`columns`/`checks`；所有记录按外部 ID 原样保存（`forceId` 保留主键），审计字段覆盖为当前用户/租户/时间，完成后刷新实体元数据缓存。 |

### importCreate 新建导入生成ID

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/table/import/create` |
| 参数 | `bodyJson`：body，string（原始 JSON 文本），必填，导入 JSON 须包含 `table`（对象）与 `columns`（数组）节点，`checks`（数组）可选；三部分所有记录禁止携带 `id`。<br />`ddl`：query，boolean，可选，默认 `true`，导入成功后是否立即创建/更新数据库表结构。 |
| 返回 | `ApiResult<Map<String,Object>>`：导入计数 `tables`/`columns`/`checks`；所有记录由系统生成新 ID 保存，审计字段覆盖为当前用户/租户/时间，完成后刷新实体元数据缓存。 |

## DevViewController ⚙ — `/model/view`

设计态视图（TableView）的增删改查与重名校验。

### pageQuery 分页查询视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/view/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult<TableView>`：视图分页列表。 |

### query 全量查询视图

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/view/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<TableView>>`：视图列表（签名声明为原始 `ApiResult`）。 |

### get 视图详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/model/view/get/{id}` |
| 参数 | `id`：path，string，必填，视图 ID。 |
| 返回 | `ApiResult<TableView>`：视图详情，视图列配置 `viewColumn` 已转换为字段元数据结构。 |

### createOrUpdate 新建或更新视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/view/createOrUpdate` |
| 参数 | `form`：body，TableView 对象，必填，常用字段：视图名 `viewName`、视图类型 `viewType`、构造 SQL `viewConstruct`、视图列 `viewColumn`、连接 `connectId` 等。 |
| 返回 | `ApiResult<TableView>`：保存前将视图列映射为数据库对象结构，保存后按视图名刷新元数据缓存（签名声明为原始 `ApiResult`）。 |

### isDelete 删除视图

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/model/view/isDelete/{id}` |
| 参数 | `id`：path，string，必填，视图 ID。 |
| 返回 | `ApiResult<NullResult>`：置禁用后逻辑删除（数据库视图改名归档，物化视图除外），并移除视图元数据缓存；`data` 为空。 |

### validate 视图重名校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/view/validate` |
| 参数 | `form`：body，TableView 对象，必填，使用 `viewName`、`connectId`、`appId`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：校验同一连接下视图名是否重复（查 `platform_dev_view`），可用返回 `true`。 |
