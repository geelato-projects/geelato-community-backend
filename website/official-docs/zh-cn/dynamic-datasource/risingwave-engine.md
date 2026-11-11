---
title: RisingWave 引擎
sidebar_label: RisingWave 引擎
---

# RisingWave 引擎

RisingWave 是物化视图加速框架的首个引擎适配器：PostgreSQL 协议（默认端口 `4566`），原生 `mysql-cdc` 连接器直读 MySQL binlog 建摄取通道，物化视图增量维护。

## 接入前提

**源库（MySQL）**：

- `binlog_format=ROW`、`binlog_row_image=FULL`（`/api/model/mv/precheck` 会逐项校验）
- 摄取账号具备 `REPLICATION SLAVE` 与 `REPLICATION CLIENT` 权限（`GRANT REPLICATION SLAVE, REPLICATION CLIENT ON *.* TO ...`）
- 源表须有主键（CDC 依赖）
- RisingWave 所在网络可达源库与自身的 binlog 位点存储

**引擎连接**：在 `/api/model/connect` 登记，`db_type=risingwave`，`db_schema` 填 `public`（或实际建物化视图的 schema），端口默认 `4566`。适配器启动时自动把 `risingwave` 注册为 `postgresql` 方言家族（`Dialects.registerAlias`），驱动、URL 模板、建池与测试连接均按 PG 家族生效——框架层不含任何 RisingWave 概念。

## 摄取模型

- **两段式（RW 3.0 API）**：①`CREATE SOURCE {通道名} WITH (connector='mysql-cdc', hostname, port, username, password, database.name)`——仅连接定义、不含表清单（1.x 的 table.name.list 已废弃，单表 CDC CREATE TABLE 亦已禁用）；②每物理表 `CREATE TABLE {通道名}_{表} FROM {通道名} TABLE '{库}.{表}'`（**FROM 后直接跟 source 名，无 SOURCE 关键字**）——**schema 由引擎自动推导**，无需列定义与类型映射
- 通道名 `gl_{连接id}_{库名}_{视图名}`（按视图隔离）；每表独立挂靠共享 source，**天然支持追加表**（表清单变化=补建新表，无需重建）
- 源表须有主键（CDC upsert 键），缺失时引擎建表报错（错误信息含上下文）
- 停用/删除：dropSource 先删前缀 CDC 表再删共享 source

## 版本差异处理

引擎系统目录（`rw_sources` / `rw_relations` / `rw_materialized_views`）与回填进度列在不同版本间存在差异，适配器按实际返回列解析，解析不出返回 `UNKNOWN`（不猜测）；建通道/建视图 DDL 集中在适配器内，升级引擎版本只影响该文件。接入新版本实例后建议先跑一遍 `precheck` 与 `view/create` 冒烟。

## 与查询链路的对接

启用后平台侧是"同名双记录"：原视图行不动，另有一条 `view_type=materialized` 的同名副本行指向引擎连接（见[物化视图加速](materialized-view.md)）；MetaManager 加载同名多行时以 materialized 优先注册，实体的 `connect_id` 指向引擎，查询经既有动态数据源路由链直达 RisingWave。分页 `limit N offset M`、双引号标识符、count 包裹对 RisingWave 均兼容，查询构建器无任何引擎分支；物化视图名统一小写（与视图行 `afterSet` 折叠一致）。写操作由平台只读门禁拦截，不会到达引擎。
