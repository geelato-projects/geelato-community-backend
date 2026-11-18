---
title: API · 元数据
sidebar_label: 元数据
---
# 元数据（meta）

2 个 Controller，共 26 个端点。标注 ⚙ 的为设计态接口。MetaController 合并了原运行态与设计态两个控制器（同前缀 `/api/meta`），其运行态端点直接从原始 request 读取 GQL 查询体（body 不是普通 JSON Map），GQL/MQL 语义见 [MQL 文档](../../mql/overview.md)。

## MetaController — `/meta`

元数据服务：同一前缀下同时提供运行态（GQL 查询/保存/删除）与设计态（实体元数据定义、Java 类源与在线 DB 源冲突诊断/同步、反向代码生成）接口。

### list GQL 查询列表

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/list`、`POST\|GET /api/meta/list/*` |
| 参数 | `withMeta`：query，boolean，可选，默认 `true`，是否随数据返回字段元数据。<br />GQL 查询体（原始 request 读取）：单实体对象或实体数组，分页（`@p`）、字段（`@fs`）、视图模板参数（`@pf`）等语义见 [MQL 文档](../../mql/overview.md)。 |
| 返回 | `ApiPagedResult`：`data` 为记录 Map 列表，`page`/`size`/`dataSize`/`total` 随查询结果回填，`meta` 与 `cache` 有值时一并返回（签名声明为原始 `ApiPagedResult<?>`）。 |

### multiList GQL 多实体查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/multiList` |
| 参数 | `withMeta`：query，boolean，可选，默认 `true`，是否随数据返回字段元数据。<br />GQL 查询体（原始 request 读取）：实体数组，各实体可带各自的分页与过滤。 |
| 返回 | `ApiMultiPagedResult`：`data` 为 `Map<实体名, PageData>`，每个 PageData 含该实体的 `data`/`total`/`page`/`size`/`dataSize`/`meta`；整体 `cache` 有值时返回。 |

### save GQL 保存单实体

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/save/{biz}` |
| 参数 | `biz`：path，string，必填，业务实体名。<br />GQL 保存体（原始 request 读取）。 |
| 返回 | `ApiMetaResult`：`data` 为保存后的主键值（string）；失败时为 `saveFail`/`transactionRollback` 等标记。 |

### batchSave GQL 批量保存

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/batchSave` |
| 参数 | GQL 批量保存体（原始 request 读取）：多条保存指令在一个事务内逐条执行。 |
| 返回 | `ApiMetaResult`：`data` 为保存成功的主键值列表（string 数组）；任一条失败即整体回滚。 |

### multiSave GQL 多实体保存

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/multiSave` |
| 参数 | GQL 保存体（原始 request 读取）：多实体保存指令在一个事务内执行。 |
| 返回 | `ApiMetaResult`：`data` 为保存的主键值列表（string 数组）。 |

### delete 按主键删除记录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/delete/{biz}/{id}` |
| 参数 | `biz`：path，string，必填，业务实体名。<br />`id`：path，string，必填，记录主键。 |
| 返回 | `ApiResult<Integer>`：删除的记录数。 |

### deleteByGql 按条件删除记录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/delete2/{biz}` |
| 参数 | `biz`：path，string，必填，业务实体名。<br />GQL 删除体（原始 request 读取）：删除条件随 GQL 表达。 |
| 返回 | `ApiResult<Integer>`：删除的记录数。 |

### treeNodeList 树节点列表查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/tree/{biz}` |
| 参数 | `entity`：query，string，必填，与 `platform_tree_node` 关联的业务实体名（实体需带 `tree_node_id` 字段）。<br />`treeId`：query，long，必填，树 ID。<br />`biz`：path，string，路径占位（方法体未使用）。 |
| 返回 | `ApiResult`：树节点列表（子节点挂在 `children` 下）；实体不合法时失败并返回原因。 |

### uniqueness 唯一性校验

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/uniqueness` |
| 参数 | GQL 查询体（原始 request 读取）：查重条件随 GQL 表达；未传 `@fs` 时自动补 `@fs=id`，未传 `@p` 时自动补 `@p=1,10`。 |
| 返回 | `ApiResult<Boolean>`：查重查询 total 为 0 时返回 `true`（无重复，签名声明为 `ApiResult<?>`）。 |

### defined 实体字段元数据

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/defined/{entity}` |
| 参数 | `entity`：path，string，必填，实体名称。 |
| 返回 | `ApiMetaResult`：`data` 为该实体的全部简单字段元数据；实体不存在时失败（`not found meta defined`）。 |

### fullDefined 实体完整元数据

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/fullDefined/{entity}` |
| 参数 | `entity`：path，string，必填，实体名称。 |
| 返回 | `ApiMetaResult`：`data` 为该实体的完整元数据（EntityMeta）。 |

### entityNames 实体名称列表

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/entityNames` |
| 参数 | 无 |
| 返回 | `ApiResult`：已加载的全部实体名称列表。 |

### queryLiteEntities 精简实体元数据

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/entityLiteMetas` |
| 参数 | 无 |
| 返回 | `ApiResult`：精简版实体元数据信息列表。 |

### conflicts 冲突实体清单

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/conflicts` |
| 参数 | 无 |
| 返回 | `ApiResult`：`data` 为 `Map<实体名, 差异明细>`，列出同时存在于 Java 类源与在线 DB 源的冲突实体及字段级差异（用于诊断重复实体导致 CRUD 无法构造 SQL 的问题）。 |

### conflict 单实体冲突明细

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/conflicts/{entity}` |
| 参数 | `entity`：path，string，必填，实体名称。 |
| 返回 | `ApiResult`：该实体的字段级差异明细；不存在冲突时返回空差异。 |

### sync 同步实体元数据

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/sync/{entity}` |
| 参数 | `entity`：path，string，必填，实体名称。<br />`direction`：query，string，可选，默认 `db-to-class`，当前仅支持该值（其他值直接失败）。 |
| 返回 | `ApiResult`：重新从数据库加载该实体在线定义并刷新主缓存（db-to-class 方向），返回同步后的最新差异校验结果。 |

### codegen 反向生成源码

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/meta/codegen/{entity}` |
| 参数 | `entity`：path，string，必填，实体名称。<br />`packageName`：query，string，可选，生成类所属包名，默认 `cn.geelato.meta`。 |
| 返回 | `ApiResult<Map<String,Object>>`：`className`、`fileName`、`packageName`、`source`（带 `@Entity`/`@Col`/`@Title`/`@Id` 注解的 .java 源码文本，仅预览，不入库不落盘）。 |

### codegenDownload 下载生成源码

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/meta/codegen/{entity}/download` |
| 参数 | `entity`：path，string，必填，实体名称。<br />`packageName`：query，string，可选，默认 `cn.geelato.meta`。 |
| 返回 | 直接写响应流（无 JSON body）：以 `text/plain` 流式下载生成的 `{ClassName}.java` 源码文件；实体元数据不存在时返回 404。 |

## MetaDdlController ⚙ — `/meta/ddl`

设计态 DDL 操作：按实体/应用重建数据库表与视图、视图语句校验、在线元数据缓存刷新。

### recreate 重建数据表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/table/{entity}` |
| 参数 | `entity`：path，string，必填，实体名称（确定需要操作的数据库表）。 |
| 返回 | `ApiMetaResult`：按实体名称重建或创建数据库表（需切换数据库）；成功时 `data` 为空，失败时返回错误信息。 |

### createOrUpdateTableByAppId 按应用重建数据表

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/tables/{appId}` |
| 参数 | `appId`：path，string，必填，应用 ID（确定操作范围；租户代码取当前会话）。 |
| 返回 | `ApiMetaResult`：`data` 为 `Map<实体名, 结果>`（`true` 成功、`false` 失败、`"ignore"` 非手工创建来源跳过）；应用下无启用表时 `data` 为空；出错时 `data` 为已完成部分的结果并附带错误信息。 |

### createOrUpdateViewByAppId 按应用重建视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/views/{appId}` |
| 参数 | `appId`：path，string，必填，应用 ID（租户代码取当前会话）。 |
| 返回 | `ApiMetaResult`：`data` 为 `Map<视图名, 结果>`（`true` 成功；虚拟视图、物化视图、缺少可关联连接/表等情况返回说明文本）；应用下无视图时 `data` 为空。 |

### createOrUpdateViewById 按 ID 重建视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/viewOne/{id}` |
| 参数 | `id`：path，string，必填，视图 ID。 |
| 返回 | `ApiMetaResult`：创建或更新单个视图（虚拟视图、物化视图拒绝执行）；成功时 `data` 为空。 |

### createOrUpdateViewByEntity 按语句创建视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/view/{view}` |
| 参数 | `view`：path，string，必填，视图名。<br />`connectId`：body，string，必填，数据库连接 ID。<br />`sql`：body，string，必填，视图语句（body 为 JSON 对象，取 `connectId`、`sql` 字段）。 |
| 返回 | `ApiMetaResult`：先校验视图语句再创建/更新视图（需切换数据库）；成功时 `data` 为空。 |

### validateView 视图语句校验

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/view/valid/{connectId}` |
| 参数 | `connectId`：path，string，必填，数据库连接 ID。<br />`sql`：body，string，必填，待验证的视图语句（body 为 JSON 对象，取 `sql` 字段）。 |
| 返回 | `ApiMetaResult<Boolean>`：视图语句是否有效（校验异常时仍返回成功结构，`data` 为 `false` 并附带错误信息）。 |

### validateView 按实体校验视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/view/valid/{connectId}/{entityName}` |
| 参数 | `connectId`：path，string，必填，数据库连接 ID。<br />`entityName`：path，string，必填，实体名称。 |
| 返回 | `ApiMetaResult<Map<String,String>>`：`data` 为 `Map<视图名, 结果>`（`"true"` 有效、`"false"` 无效或异常信息）；无匹配视图时为空 Map。 |

### refreshRedis 刷新元数据缓存

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/meta/ddl/redis/refresh` |
| 参数 | `tableId`：body，string，表 ID。<br />`entityName`：body，string，实体名。<br />`connectId`：body，string，连接 ID。<br />`appId`：body，string，应用 ID。<br />`tenantCode`：body，string，可选，租户代码（未传时取当前会话租户）。（body 为 JSON 对象） |
| 返回 | `ApiMetaResult`：按传入条件重新解析在线 DB 元数据并刷新缓存（仅操作元数据所在库）；成功时 `data` 为空。 |
