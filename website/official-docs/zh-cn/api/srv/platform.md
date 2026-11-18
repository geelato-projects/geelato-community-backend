---
title: API · 应用与平台配置
sidebar_label: 应用与平台配置
---
# 应用与平台配置（platform）

13 个 Controller，共 80 个端点。标注 ⚙ 的为设计态接口。

## AppController — `/app`

应用注册与版本包导入、按用户身份过滤可见应用、应用页面权限查询。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为应用（`App`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<App>>`：应用列表。 |

### queryByUser 按用户查应用

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/queryByUser` |
| 参数 | `tenantCode`：query，string，必填，租户编码。<br />`userId`：query，string，可选，用户 ID，缺省取当前登录用户。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：`data` 为该用户可见的应用列表，按用户类型过滤：员工/虚拟员工仅见普通应用（企业内部或企业内外部），租户管理员与系统可见普通及平台应用，企业外部人员仅见企业外部普通应用。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/get/{id}` |
| 参数 | `id`：path，string，应用 ID。 |
| 返回 | `ApiResult<App>`：应用详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/createOrUpdate` |
| 参数 | `form`：body，`App` 实体；`id` 非空更新、为空新建。<br />`useType`：query，string，可选；取值 `import` 时走导入流程（要求 `form.id` 非空，按已有 ID 导入应用）。 |
| 返回 | `ApiResult<App>`：保存或导入后的应用。 |

### isDelete 删除应用

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/app/isDelete/{id}` |
| 参数 | `id`：path，string，应用 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；应用不存在时报错（软删除）。 |

### validate 校验唯一性

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/validate/{type}` |
| 参数 | `type`：path，string，校验类型：`code` 校验应用编码可用性、`id` 校验应用 ID 是否已存在。<br />`form`：body，`App` 实体，提供待校验的 `code`、`id`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示可用（编码未被占用或 ID 不存在）；`type` 非法时返回失败。 |

### queryPermissionByPage 应用页面权限查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/queryPermissionByPage` |
| 参数 | `appId`：query，string，可选，按应用过滤。<br />`tenantCode`：query，string，可选，按租户过滤。<br />`type`：query，string，可选，权限类型。<br />`treeId`：query，string，可选，应用页面树节点 ID。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：`data` 为页面权限记录列表（权限 ID、名称、编码、类型、对象、规则及所属页面树节点 ID）。 |

### queryRolePermissionByPage 角色页面权限查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/queryRolePermissionByPage` |
| 参数 | `appId`：query，string，可选，按应用过滤。<br />`roleId`：query，string，可选，角色 ID，支持逗号分隔多个。<br />`type`：query，string，可选，权限类型。<br />`tenantCode`：query，string，可选，按租户过滤。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：`data` 为角色与页面权限关联记录（角色、权限及所属应用）。 |

### upload 上传应用包

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/upload/{fileId}` |
| 参数 | `fileId`：path，string，已上传的应用包附件 ID（`.gdp` 压缩包）。 |
| 返回 | `ApiResult<App>`：`data` 为导入/更新后的应用；包内需含 `.gdp` 描述文件（源应用 ID、名称、编码、版本），文件不存在或解析失败时报错。 |

## AppMultiLangController — `/app/multiLang`

应用多语言资源维护与语言包内容读取。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/multiLang/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为应用多语言（`AppMultiLang`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/multiLang/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<AppMultiLang>>`：应用多语言列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/multiLang/get/{id}` |
| 参数 | `id`：path，string，多语言记录 ID。 |
| 返回 | `ApiResult<AppMultiLang>`：多语言记录详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/multiLang/createOrUpdate` |
| 参数 | `form`：body，`AppMultiLang` 实体；`id` 非空更新、为空新建。 |
| 返回 | `ApiResult<AppMultiLang>`：保存后的多语言记录。 |

### isDelete 删除多语言记录

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/app/multiLang/isDelete/{id}` |
| 参数 | `id`：path，string，多语言记录 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### queryAml 查询语言包内容

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/multiLang/queryAml/{appId}/{langType}` |
| 参数 | `appId`：path，string，应用 ID。<br />`langType`：path，string，语言类型。 |
| 返回 | `ApiResult<String>`：`data` 为该应用指定语言的语言包文件内容（用途固定为 `WEBAPP`，取最新一条记录并读取其语言包文件；无匹配时 `data` 为空）。 |

## AppSqlMapController — `/app/sql`

应用间 SQL 授权映射：管理应用引用其他应用自定义 SQL 的授权关系。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/sql/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为应用 SQL 授权（`AppSqlMap`）分页数据。 |

### pageQueryOf 分页查询（关联视图）

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/sql/pageQueryOf` |
| 参数 | `query`：query，键值对过滤参数，原样传入命名查询（如 `appId`、`tenantCode`）。<br />分页参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为应用与 SQL 授权关联记录（Map 结构）分页数据（命名查询 `page_query_platform_app_r_sql`）。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/sql/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<AppSqlMap>>`：应用 SQL 授权列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/sql/get/{id}` |
| 参数 | `id`：path，string，授权记录 ID。 |
| 返回 | `ApiResult<AppSqlMap>`：授权记录详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/sql/createOrUpdate` |
| 参数 | `form`：body，`AppSqlMap` 实体；`id` 非空更新、为空新建；保存前执行字段预处理（`after`）。 |
| 返回 | `ApiResult<AppSqlMap>`：保存后的授权记录。 |

### isDelete 删除授权

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/app/sql/isDelete/{id}` |
| 参数 | `id`：path，string，授权记录 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

## AppTableMapController — `/app/table`

应用与数据表的映射：管理应用可访问的数据表授权关系。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/table/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md)；参数经 JSON 请求体传入（GET 请求携带 body）。 |
| 返回 | `ApiPagedResult`：`data` 为应用表映射（`AppTableMap`）分页数据。 |

### pageQueryOf 分页查询（关联视图）

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/table/pageQueryOf` |
| 参数 | `query`：query，键值对过滤参数，原样传入命名查询（如 `appId`、`tenantCode`）。<br />分页参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为应用与数据表映射关联记录（Map 结构）分页数据（命名查询 `page_query_platform_app_r_table`）。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/table/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<AppTableMap>>`：应用表映射列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/table/get/{id}` |
| 参数 | `id`：path，string，映射记录 ID。 |
| 返回 | `ApiResult<AppTableMap>`：映射记录详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/table/createOrUpdate` |
| 参数 | `form`：body，`AppTableMap` 实体；`id` 非空更新、为空新建；保存前执行字段预处理（`after`）。 |
| 返回 | `ApiResult<AppTableMap>`：保存后的映射记录。 |

### isDelete 删除映射

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/app/table/isDelete/{id}` |
| 参数 | `id`：path，string，映射记录 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

## AppViewMapController — `/app/view`

应用与视图的映射：管理应用可访问的视图授权关系。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/view/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为应用视图映射（`AppViewMap`）分页数据。 |

### pageQueryOf 分页查询（关联视图）

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/view/pageQueryOf` |
| 参数 | `query`：query，键值对过滤参数，原样传入命名查询（如 `appId`、`tenantCode`）。<br />分页参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为应用与视图映射关联记录（Map 结构）分页数据（命名查询 `page_query_platform_app_r_view`）。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/view/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<AppViewMap>>`：应用视图映射列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/view/get/{id}` |
| 参数 | `id`：path，string，映射记录 ID。 |
| 返回 | `ApiResult<AppViewMap>`：映射记录详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/view/createOrUpdate` |
| 参数 | `form`：body，`AppViewMap` 实体；`id` 非空更新、为空新建；保存前执行字段预处理（`after`）。 |
| 返回 | `ApiResult<AppViewMap>`：保存后的映射记录。 |

### isDelete 删除映射

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/app/view/isDelete/{id}` |
| 参数 | `id`：path，string，映射记录 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

## BizDataController — `/bizData`

业务数据归属者批量移交：跨实体修改创建人等归属字段。

### changeOwner 批量移交数据归属

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/bizData/changeOwner` |
| 参数 | `entityIdMap`：body，`Map<String,List<String>>`，键为实体名称，值为该实体待移交的主键 ID 列表。<br />`owner`：query，string，必填，目标归属用户 ID。 |
| 返回 | `ApiResult<Map<String,Integer>>`：`data` 为各实体成功更新的记录数。管理员可移交任意非平台实体的数据；非管理员仅能移交自己创建的数据，且实体须含归属字段；平台类实体（`platform` 目录/实体名/表名前缀）禁止移交；存在越权记录、非法实体或未知用户时整体失败并附拒绝明细。 |

## DictController — `/dict`

数据字典维护，变更后发布字典升级事件以刷新字典缓存。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为字典（`Dict`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/dict/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Dict>>`：字典列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/dict/get/{id}` |
| 参数 | `id`：path，string，字典 ID。 |
| 返回 | `ApiResult<Dict>`：字典详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/createOrUpdate` |
| 参数 | `form`：body，`Dict` 实体；`id` 非空更新、为空新建。 |
| 返回 | `ApiResult<Dict>`：保存后的字典；保存后发布字典升级事件。 |

### isDelete 删除字典

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/dict/isDelete/{id}` |
| 参数 | `id`：path，string，字典 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除后发布字典升级事件。 |

### validate 校验编码可用性

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/validate` |
| 参数 | `form`：body，`Dict` 实体，提供待校验的 `dictCode`、`id`、`appId`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示 `dictCode` 在应用内可用。 |

## DictItemController — `/dict/item`

字典项维护与树形查询，变更后发布字典升级事件。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/item/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为字典项（`DictItem`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/dict/item/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<DictItem>>`：字典项列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/dict/item/get/{id}` |
| 参数 | `id`：path，string，字典项 ID。 |
| 返回 | `ApiResult<DictItem>`：字典项详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/item/createOrUpdate` |
| 参数 | `form`：body，`DictItem` 实体；`id` 非空更新、为空新建。 |
| 返回 | `ApiResult<DictItem>`：保存后的字典项；保存后发布字典升级事件。 |

### batchCreateOrUpdate 批量保存

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/item/batchCreateOrUpdate` |
| 参数 | `forms`：body，`List<DictItem>`，字典项列表。<br />`dictId`：query，string，字典 ID。<br />`parentId`：query，string，父字典项 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；批量保存该字典（父项）下的字典项，成功后发布字典升级事件。 |

### isDelete 删除字典项

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/dict/item/isDelete/{id}` |
| 参数 | `id`：path，string，字典项 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除并将该项置为停用，发布字典升级事件。 |

### queryItemByDictCode 按编码查字典项树

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/dict/item/queryItemByDictCode/{dictCode}` |
| 参数 | `dictCode`：path，string，字典编码。 |
| 返回 | `ApiResult<List<DictItem>>`：`data` 为该字典下启用状态字典项的树形结构（`pid` 为空的项作为根节点，子项挂在 `children` 上）。 |

### validate 校验编码可用性

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/item/validate` |
| 参数 | `form`：body，`DictItem` 实体，提供待校验的 `itemCode`、`id`、`dictId`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示 `itemCode` 在该字典内可用。 |

### createDictAndItems 新建字典及字典项

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/dict/item/createDictAndItems` |
| 参数 | `form`：body，`Dict` 实体，`dictItems` 字段携带字典项列表（忽略传入的项 ID 与 `seqNo`，按列表顺序生成）。 |
| 返回 | `ApiResult<Dict>`：`data` 为新建的字典（含生成的字典项）；成功后发布字典升级事件。 |

### queryItemTagsByDict 查询字典项标签

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/dict/item/queryItemTagsByDict/{dictString}` |
| 参数 | `dictString`：path，string，字典 ID 或字典编码。 |
| 返回 | `ApiResult<Set<String>>`：`data` 为该字典全部启用字典项的标签集合（`itemTag` 按逗号拆分去重）；字典不存在时为空集合。 |

## ScheduleController ⚙ — `/schedule`

定时任务管理（设计态）：任务的查询、维护、编码校验与状态启停。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/schedule/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为定时任务（`Schedule`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/schedule/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Schedule>>`：定时任务列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/schedule/get/{id}` |
| 参数 | `id`：path，string，任务 ID。 |
| 返回 | `ApiResult<Schedule>`：定时任务详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/schedule/createOrUpdate` |
| 参数 | `form`：body，`Schedule` 实体；`id` 非空更新、为空新建；新建且类型为 `JAVA` 时自动生成任务编码 `code`。 |
| 返回 | `ApiResult<Schedule>`：保存后的定时任务。 |

### isDelete 删除任务

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/schedule/isDelete/{id}` |
| 参数 | `id`：path，string，任务 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### validate 校验编码可用性

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/schedule/validate` |
| 参数 | `form`：body，`Schedule` 实体，提供待校验的 `code`、`id`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示 `code` 可用。 |

### status 修改状态

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/schedule/status/{id}/{status}` |
| 参数 | `id`：path，string，任务 ID。<br />`status`：path，int，目标状态。 |
| 返回 | `ApiResult<Schedule>`：更新状态后的定时任务。 |

## ScheduleLogController ⚙ — `/schedule/log`

定时任务执行日志（设计态）的查询与维护。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/schedule/log/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为任务日志（`ScheduleLog`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/schedule/log/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<ScheduleLog>>`：任务日志列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/schedule/log/get/{id}` |
| 参数 | `id`：path，string，日志 ID。 |
| 返回 | `ApiResult<ScheduleLog>`：任务日志详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/schedule/log/createOrUpdate` |
| 参数 | `form`：body，`ScheduleLog` 实体；`id` 非空更新、为空新建。 |
| 返回 | `ApiResult<ScheduleLog>`：保存后的任务日志。 |

### isDelete 删除日志

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/schedule/log/isDelete/{id}` |
| 参数 | `id`：path，string，日志 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

## SqlController ⚙ — `/sql`

自定义 SQL 管理（设计态）：SQL 的维护、编码校验与脚本缓存刷新。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sql/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为自定义 SQL（`CustomSql`）分页数据。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sql/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<CustomSql>>`：自定义 SQL 列表。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sql/get/{id}` |
| 参数 | `id`：path，string，SQL ID。 |
| 返回 | `ApiResult<CustomSql>`：自定义 SQL 详情。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sql/createOrUpdate` |
| 参数 | `form`：body，`CustomSql` 实体；`id` 非空更新、为空新建；保存前执行字段预处理（`afterSet`）。 |
| 返回 | `ApiResult<CustomSql>`：保存后的自定义 SQL。 |

### isDelete 删除 SQL

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/sql/isDelete/{id}` |
| 参数 | `id`：path，string，SQL ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### validate 校验键可用性

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sql/validate` |
| 参数 | `form`：body，`CustomSql` 实体，提供待校验的 `keyName`、`id`、`appId`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示 `keyName` 可用。 |

### refresh 刷新 SQL 缓存

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sql/refresh/{sqlKey}` |
| 参数 | `sqlKey`：path，string，SQL 脚本 key。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；按 key 刷新已注册的 SQL 脚本缓存。 |

### queryByApp 按应用查 SQL

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sql/query/{appId}` |
| 参数 | `appId`：path，string，应用 ID。 |
| 返回 | `ApiResult<List<CustomSql>>`：`data` 为该应用的自定义 SQL，以及其他应用授权（启用且审批同意）给该应用的 SQL 合并列表。 |

## SysConfigController — `/sys/config`

系统配置项管理：配置键值维护、加密配置读取与环境配置缓存刷新。

### pageQuery 分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sys/config/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为系统配置（`SysConfig`）分页数据；上传类配置项附带附件名称（`configAssist`）。 |

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sys/config/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<SysConfig>>`：系统配置列表，同样附带 `configAssist`。 |

### get 查询详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sys/config/get/{id}` |
| 参数 | `id`：path，string，配置 ID。<br />`encrypt`：query，boolean，可选，默认 `false`；为 `true` 且配置已加密时返回解密后的值。 |
| 返回 | `ApiResult<SysConfig>`：配置详情（SM2 密钥字段不返回）。 |

### createOrUpdate 新建或更新

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sys/config/createOrUpdate` |
| 参数 | `form`：body，`SysConfig` 实体；`id` 非空更新、为空新建；保存前执行字段预处理（`afterSet`）。 |
| 返回 | `ApiResult<SysConfig>`：保存后的配置；启用状态的配置保存后刷新环境配置缓存。 |

### isDelete 删除配置

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/sys/config/isDelete/{id}` |
| 参数 | `id`：path，string，配置 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；删除并将配置置为停用。 |

### validate 校验键可用性

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/sys/config/validate` |
| 参数 | `form`：body，`SysConfig` 实体，提供待校验的 `configKey`、`id`、`tenantCode`。 |
| 返回 | `ApiResult<Boolean>`：`true` 表示 `configKey` 可用。 |

### getValue 按键取值

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/sys/config/getValue/{key}` |
| 参数 | `key`：path，string，配置键。 |
| 返回 | `ApiResult<String>`：`data` 为启用状态配置的值；无匹配配置时为空字符串。 |

## TreeNodeController — `/treeNode`

树形节点（`TreeNode`）的查询与删除。

### query 列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/treeNode/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<TreeNode>>`：树节点列表。 |

### isDelete 删除节点

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/treeNode/isDelete/{id}` |
| 参数 | `id`：path，string，节点 ID。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |
