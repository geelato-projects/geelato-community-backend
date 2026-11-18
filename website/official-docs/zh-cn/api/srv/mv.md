---
title: API · 物化视图
sidebar_label: 物化视图
---
# 物化视图（mv）

1 个 Controller，共 13 个端点。本域全部为设计态接口（@DesignTimeApiRestController）：物化视图加速管理（引擎中立）。双记录架构——启用时在平台新增同名 materialized 副本行（元数据加载物化优先，查询路由到引擎），停用时软删副本行回原视图，彻底删除走 drop；引擎（如 RisingWave）由连接的 `db_type` 决定，框架按适配器分发。

## MaterializedViewController ⚙ — `/model/mv`

物化视图全生命周期管理：启用前预检、CDC 摄取通道创建、三步 DDL 的预览/校验/单步执行、初始化建视图、启停、元数据刷新、数据对比与删除。所有端点均为 POST，body 为 JSON 对象。

### precheck 启用前预检

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/precheck` |
| 参数 | `engineConnectId`：body，string，必填，引擎连接 ID。<br />`originConnectId`：body，string，必填，源库连接 ID。<br />`viewName`：body，string，可选，视图名（用于预检报告）。<br />`tables`：body，string 数组或逗号分隔字符串，可选，源表清单。<br />`database`：body，string，可选，源库名（缺省取源连接登记的库名）。<br />`snapshot`：body，boolean，可选，默认 `true`，是否执行初始快照回填。 |
| 返回 | `ApiResult<Map<String,Object>>`：`passed` 是否全部通过；`items` 为逐项检查结果（引擎适配器、源表清单非空、源库对象解析展开、引擎侧检查等）。不执行任何 DDL。 |

### createSource 创建 CDC 摄取通道

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/source/create` |
| 参数 | `engineConnectId`：body，string，必填。<br />`originConnectId`：body，string，必填。<br />`viewName`：body，string，必填（自动规范化为小写）。<br />`tables`：body，string 数组或逗号分隔字符串，必填，源表清单（MySQL 视图自动展开为物理表，未解析到物理表时报错）。<br />`database`：body，string，可选，源库名。<br />`snapshot`：body，boolean，可选，默认 `true`。 |
| 返回 | `ApiResult<Map<String,Object>>`：`engineType`、`sourceName`、`physicalTables`、`tableMapping`（源表→引擎摄取表映射）与三步 DDL 载荷 `ddl`；执行 DDL 建立摄取通道并把 DDL JSON 落库到副本行。 |

### previewDdl 预览三步 DDL

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/ddl/preview` |
| 参数 | `engineConnectId`：body，string，必填。<br />`originConnectId`：body，string，必填。<br />`viewName`：body，string，必填。<br />`originSql`：body，string，可选，视图定义 SELECT（未传时回退同名原视图行的定义 SQL）。<br />`tables`：body，string 数组或逗号分隔字符串，可选，源表清单。<br />`database`：body，string，可选，源库名。<br />`snapshot`：body，boolean，可选，默认 `true`。 |
| 返回 | `ApiResult<Map<String,Object>>`：`sourceName`、`expandedSql`、`engineSql`、`physicalTables`、`expandedViews`、`passed` 与 `steps`（每段 DDL 文本及逐项校验结论）；不连接引擎执行任何 DDL，仅首次预览时把 DDL JSON 落库到副本行。 |

### validateDdl 校验三步 DDL

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/ddl/validate` |
| 参数 | 参数与 `previewDdl` 一致，另有 `ddl`：body，object，可选，前端回传（可能已编辑）的三段 DDL 载荷（`sourceDdl`、`cdcTables` 数组（元素含 `originTable`/`engineTable`/`ddl`）、`mvDdl`；CDC 摄取表标识符不可修改）。 |
| 返回 | `ApiResult<Map<String,Object>>`：与预览同构的校验结果（`passed` 与逐段 `steps` 结论）；不执行任何 DDL。 |

### executeStep 单步执行 DDL

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/ddl/step/execute` |
| 参数 | `stepKey`：body，string，必填，步骤标识（`source` / `cdc:<源表名>` / `mv`）。<br />其余参数与 `previewDdl` 一致（含可选的编辑后 `ddl` 载荷）。 |
| 返回 | `ApiResult<Map<String,Object>>`：`executed`/`reused`/`message`/`passed`/`items`/`detail`；校验未通过的步骤禁止执行；`stepKey=mv` 执行成功时附带 `materialized`、`mode`、`columns`、`ddlJson`（同步平台侧副本行与元数据）。 |

### createView 初始化物化视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/create` |
| 参数 | `engineConnectId`：body，string，必填。<br />`originConnectId`：body，string，必填。<br />`viewName`：body，string，必填。<br />`originSql`：body，string，可选，定义 SELECT（未传时回退原视图行）。<br />`tables`：body，string 数组或逗号分隔字符串，可选，源表清单。<br />`database`：body，string，可选，源库名。<br />`snapshot`：body，boolean，可选，默认 `true`。<br />`recreate`：body，boolean，可选，默认 `false`，与旧通道命名冲突或需重建时传 `true`。<br />`ddl`：body，object，可选，编辑后的三段 DDL 载荷（先逐段校验，不通过不执行任何 DDL）。 |
| 返回 | `ApiResult<Map<String,Object>>`：预检（视图展开、SQL 改写、SELECT 语法、CDC 列类型、平台默认字段）全部通过后执行三步 DDL；返回 `mode`、`reused`、`sourceName`、`expandedSql`/`engineSql`、`physicalTables`、`ddlJson`、`columns`、引擎 `status` 与 `message`；回填完成前（status=BACKFILLING）查询结果不完整，应以 `view/status` 确认 READY 后再承载查询。 |

### viewStatus 查询就绪状态

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/status` |
| 参数 | `engineConnectId`：body，string，必填。<br />`viewName`：body，string，必填。 |
| 返回 | `ApiResult<Map<String,Object>>`：`engineType`、`viewName`、`enabled`（平台是否已有启用的 materialized 副本行）与引擎返回的 `status`（用于确认回填 READY）。 |

### enable 启用（保存副本行）

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/enable` |
| 参数 | `engineConnectId`：body，string，必填，引擎连接 ID。<br />`viewName`：body，string，必填，原视图名（物化视图是既有视图的加速副本）。<br />`originSql`：body，string，可选，定义 SELECT（未传时回退原视图行）。 |
| 返回 | `ApiResult<Map<String,Object>>`：仅新建/恢复 materialized 副本行（不初始化引擎），返回 `mode`（created/updated）、`enabled=true` 与说明 `note`（视图列待初始化回填，经 `view/create` 后再承载查询）。 |

### disable 停用物化视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/disable` |
| 参数 | `viewName`：body，string，必填。 |
| 返回 | `ApiResult<Map<String,Object>>`：软删 materialized 副本行并刷新元数据（查询恢复原库视图；引擎内物化视图保留，再次启用可复用免回填），返回 `enabled=false` 与说明 `note`。 |

### refreshMeta 刷新元数据缓存

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/refreshMeta` |
| 参数 | `viewName`：body，string，必填。 |
| 返回 | `ApiResult<Map<String,Object>>`：重载实体元数据并清空数据源路由缓存；副本行列元数据缺失且引擎内视图已存在时补同步列，返回 `syncedColumns`（未触发补同步为 -1）。 |

### compareData 源库数据对比

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/compareData` |
| 参数 | `engineConnectId`：body，string，必填。<br />`originConnectId`：body，string，必填。<br />`viewName`：body，string，必填。<br />`originSql`：body，string，可选，定义 SELECT。<br />`tables`：body，string 数组或逗号分隔字符串，可选，源表清单。<br />`database`：body，string，可选，源库名。 |
| 返回 | `ApiResult<Map<String,Object>>`：`allMatch` 与逐表结果 `tables`（源库行数 vs 引擎摄取表行数 `countMatch`、按 `update_at` 最新行的列级差异 `latestMismatches`），并附物化视图与原视图查询口径的行数对比；大表 COUNT 耗时较长，避免频繁调用。 |

### checkQuery 查询可用性检查

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/checkQuery` |
| 参数 | `engineConnectId`：body，string，必填。<br />`viewName`：body，string，必填。 |
| 返回 | `ApiResult<Map<String,Object>>`：执行 `SELECT * ... LIMIT 1` 与 `COUNT(*)`，返回 `queryOk`、`totalRows`、`columns`、`sampleRow`（一行样例）与引擎 `status`。 |

### drop 删除物化视图

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/model/mv/view/drop` |
| 参数 | `viewName`：body，string，必填。<br />`dropSource`：body，boolean，可选，默认 `false`，是否连同 CDC source 与摄取表一并删除。<br />`originConnectId`：body，string，`dropSource=true` 时必填。<br />`database`：body，string，`dropSource=true` 时必填（源连接未登记库名时）。 |
| 返回 | `ApiResult<Map<String,Object>>`：删除引擎内物化视图并软删副本行；`dropSource=true` 时按共享通道引用情况清理摄取表（仍被其他视图引用的共享 source 保留），返回 `droppedCdcTables`、`sharedSourceKept`。 |
