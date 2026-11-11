---
title: 物化视图加速
sidebar_label: 物化视图加速
---

# 物化视图加速

慢的多表查询可以改由外部引擎维护的**物化视图**承载：源库（MySQL）的写入照旧，引擎经摄取通道增量同步并物化查询结果；启用后，对视图的查询直接落在物化视图上——**调用方的查询语句零改动**（无感切换）。

框架**引擎中立**：编排、元数据、路由、只读门禁不感知具体引擎，引擎（首个为 RisingWave）以适配器接入；后续更换/新增引擎不影响调用方与既有视图。

## 工作原理（双记录 + 加载物化优先）

```
MySQL 源表（写路径不变）
   │  摄取通道（引擎自决；RisingWave 用 mysql-cdc 直读 binlog）
   ▼
外部引擎：摄取通道 → 物化视图（增量维护，秒级新鲜度）
   ▲
   │  platform_dev_view 同名 materialized 副本行（connect_id=引擎连接）
既有查询（/api/meta/list、MQL、dao）——视图名不变；MetaManager 加载同名多行时
以 materialized 行优先注册，实体 connectId 指向引擎，经既有动态数据源路由链直达引擎
```

落位与语义：

| 数据 | 落位 |
|---|---|
| 引擎连接 | `platform_dev_db_connect` 一行（如 `db_type=risingwave`） |
| 原视图 | `platform_dev_view` 原视图行**完全不动**（default/custom/virtual 照旧） |
| 物化视图副本 | `platform_dev_view` **新增一条同名 materialized 行**：复制原视图数据，仅 `connect_id` 改为引擎连接、`view_type=materialized`、`view_construct=**原始建表语句**`（以源表名书写；查询构建对 materialized 不消费它，仍按物理视图名直查）、`view_column=引擎内省列 JSON` |
| 元数据注册 | `MetaManager` 同名多行时 **materialized 优先**（与加载顺序无关）；停用（软删副本行）后原视图自动接管注册 |
| 摄取通道 / 回填进度 | **不落平台**——以引擎为事实源，实时内省 |

:::info 为什么是同名双记录
物化视图是既有视图的加速副本而非新视图：原视图行保留完整定义与回退能力；同名双行使实体注册天然形成"物化优先"的路由接管，调用方与查询语句零改动。视图管理界面的视图类型仍只有 default/custom/virtual——物化视图由原视图编辑弹窗中的"物化视图"页签以开关方式管理。
:::

**新鲜度语义**：摄取为增量 CDC（秒~分钟级），查询读到的是**最终一致**的数据；初始回填完成前（status=BACKFILLING）查询结果不完整。不接受该语义的查询请停用物化视图（查询立即回原库视图）。

**只读硬失败**：启用期间对视图实体的写操作（save/insert/update/delete 等）在 Dao 层直接抛出明确异常，写操作请指向源库原表。

## 使用流程

设计时 API 前缀 `/api/model/mv`（DesignTime）。引擎连接需先在 `/api/model/connect` 登记。前端在视图编辑弹窗的"物化视图"页签以开关驱动全流程。

### 1. precheck —— 前置检查

```json
POST /api/model/mv/precheck
{
  "engineConnectId": "引擎连接id",
  "originConnectId": "MySQL源连接id",
  "tables": ["orders", "customers"],
  "viewName": "v_order_summary",
  "database": "可选，默认取源连接 db_name"
}
```

逐项返回检查结果（不中途失败，便于一次性看全）：引擎适配器、源库连通、`binlog_format=ROW`、`binlog_row_image=FULL`、`REPLICATION SLAVE/CLIENT` 权限、源表存在、视图名引擎侧状态。

### 2. view/create —— 启用（引擎建物化视图 + 新增副本行）

```json
POST /api/model/mv/view/create
{
  "engineConnectId": "引擎连接id",
  "viewName": "v_order_summary",
  "originConnectId": "MySQL源连接id",
  "tables": ["orders", "customers"],
  "originSql": "select o.id, o.amount, c.name from orders o join customers c on o.customer_id = c.id",
  "database": "可选",
  "recreate": false
}
```

`originSql` 用**源库表名**书写（前端默认预填原视图的视图语句）；框架先把语句中的视图引用自动展开为底层物理表（mysql-cdc 只能摄取物理表），再经词法改写把表引用替换为引擎内摄取表名后建物化视图、内省列，并在 platform_dev_view 新增/更新同名 materialized 副本行（建表语句存入 `view_construct`）。摄取通道按（源连接+库+视图）确定性命名、**按视图隔离**——每个物理表一条独立的 CDC 摄取表（RW 2.x/3.x API），重复调用幂等、**天然支持追加表**（表清单变化=补建新表）。`recreate=false` 且引擎已存在同名物化视图时**直接复用**（停用后再启用免回填）；`recreate=true` 先删再建（改定义重建，回填重来）。响应含 `originSql`/`engineSql` 两份定义、列清单与回填状态。

### 3. view/status —— 状态

```json
POST /api/model/mv/view/status
{ "engineConnectId": "引擎连接id", "viewName": "v_order_summary" }
```

实时查询引擎系统目录：`READY`（回填完成，可承载查询）/ `BACKFILLING`（结果不完整）/ `NOT_EXISTS` / `UNKNOWN`（引擎版本差异，进度不可解析），并返回引擎内当前定义（`definition`）与平台侧启用状态（`enabled`）。

### 4. view/disable —— 停用（无感回退）

```json
POST /api/model/mv/view/disable
{ "viewName": "v_order_summary" }
```

软删 materialized 副本行——查询**立即恢复原库视图**；引擎内物化视图与摄取通道保留，再次启用（view/create，recreate=false）直接复用、免回填。误操作代价趋近于零。

### 5. view/drop —— 彻底删除

```json
POST /api/model/mv/view/drop
{ "viewName": "v_order_summary", "dropSource": "可选", "originConnectId": "dropSource 时必填", "database": "可选" }
```

引擎 dropView + 副本行软删；显式 `dropSource=true` 时连带删除摄取通道（若仍有其他物化视图引用会失败并提示）。

## 引擎接入（SPI）

新增引擎两步，框架（orm/core 不感知任何具体引擎类型）与调用方不动：

1. 实现适配器 `cn.geelato.web.platform.srv.mv.engine.MaterializedViewEngine`（Spring Bean，按 `supports(dbType)` 认领连接）：摄取通道、建视图 DDL、状态与列内省、删除；失败一律硬失败并带可定位上下文；
2. 适配器启动时 `Dialects.registerAlias(dbType, 内置方言家族)` 声明 JDBC 形态（如 RisingWave 注册为 `postgresql` 家族）——驱动、URL 模板、建池与测试连接自动按家族生效，无需改动框架任何分支。

首个引擎 RisingWave 的接入前提与配置见 [RisingWave 引擎](risingwave-engine.md)。
