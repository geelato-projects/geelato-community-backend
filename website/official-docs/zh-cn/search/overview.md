---
title: 搜索引擎总览
sidebar_label: 搜索引擎
---

# 搜索引擎（geelato-search）

Geelato 的搜索引擎用于加速 MQL 中的 **`fuzzymatch` 多编号模糊检索**。业务系统里"一张单据的编号分散在主表多列与子表多行，任意编号命中即命中该单据"这类查询，原先依赖数据库存储函数 `gfn_fuzzymatch` 逐行调用，在大表上会退化为全表扫描（实测 144 万行 3 秒以上）。

搜索引擎把这类谓词**等价卸载**到嵌入式检索引擎上，同时保持结果集与原 SQL 严格一致：

| 层 | 生效条件 | 机制 | 状态 |
| --- | --- | --- | --- |
| L1 REGEXP 等价改写 | 默认生效，零配置 | `gfn_fuzzymatch(col,'kw')>0` 在 SQL 层改写为 `(col<>'' AND col REGEXP ?)` | 已实现 |
| L2 Lucene 路由 | 配置搜索域 + 完成存量重建 | fuzzymatch 条件路由到嵌入式 Lucene 倒排索引（NRT + NGram contains），毫秒级检出 id 后回注 `id IN (...)` | 已实现，受实验门禁管控 |
| L3 同步与补偿 | 随 L2 自动启用 | ORM 保存/删除事件异步同步索引；失败进入 `search_sync_fail` 退避重试；每日按 `update_at` 水位对账 | 已实现 |

**边界**：数据源始终是数据库，引擎只承接"哪些 id 命中"这一个谓词；join、分页、排序、数据权限过滤仍由 SQL 完成。因此索引只是加速器，索引落后只影响时效、不影响正确性。

## 核心概念

| 概念 | 说明 |
| --- | --- |
| 搜索域 SearchDomain | 一张单据的检索建模：`id`、`mainEntity`（实体名）、`pkField`（默认 id）、`fields[]`，以及 `children[]` 子实体聚合（`entity` + 外键 `fkField` + `fields[]`）。子实体变更经外键自动重建所属主单文档 |
| 文档 SearchDocument | 文档 id = 主实体主键；`fieldValues` 为多值模型——主表字段单值、子表聚合字段多值 |
| 查询 SearchQuery | 多关键词 OR × 多字段任意位置包含（与 `gfn_fuzzymatch` 多词 OR 语义对齐）；可按 tenantCode/delStatus/appId 过滤；`maxIds` 默认 50000，超限 `truncated=true` |
| 截断语义 | 命中集合被截断后**不可用于等价路由**（回注 id IN 会漏单），路由侧必须放弃路由回退 SQL |

## 快速接入

1. **引入依赖**：quickstart 已引入 `geelato-search-api` + `geelato-search-lucene`，业务工程显式添加即可。
2. **配置搜索域**（`geelato.search.domains.*`）：

```properties
geelato.search.domains[0].id=il_eco
geelato.search.domains[0].main-entity=il_eco
geelato.search.domains[0].pk-field=id
geelato.search.domains[0].fields[0]=soNo
geelato.search.domains[0].fields[1]=mblNo
geelato.search.domains[0].fields[2]=containerNo
# 子实体聚合（一单多柜）：子实体变更经外键自动重建所属订单文档
#geelato.search.domains[0].children[0].entity=il_so_container_fee_detail
#geelato.search.domains[0].children[0].fk-field=orderId
#geelato.search.domains[0].children[0].fields[0]=containerNo
# 视图实体路由映射（vt 派生视图：视图列名 → 域字段名）
#geelato.search.domains[0].route-entities.vt_eco_view.pk-column=id
#geelato.search.domains[0].route-entities.vt_eco_view.column-map.so_no=soNo
```

3. **存量重建**：启动后触发 `POST /api/search/reindex/{domainId}`，轮询 `GET /api/search/reindex/{domainId}/progress` 至 `finished=true`。未完成重建的域不会参与路由。
4. **自动路由**：此后该域实体上的 fuzzymatch 条件自动路由（业务代码零改动）；索引同步挂 ORM 保存/删除事件自动生效。

## 管理与运维

| 端点 | 说明 |
| --- | --- |
| `POST /api/search/reindex/{domainId}` | 触发存量重建（幂等，域级互斥） |
| `GET /api/search/reindex/{domainId}/progress` | 重建进度 |
| `GET /api/search/domains`、`GET /api/search/health`、`GET /api/search/debug/{domainId}` | 域清单 / 引擎健康 / 调试信息 |
| `GET /api/search-ops/consistency`、`/compensation`、`/watermarks` | 运维页只读数据接口 |
| `GET /search-ops.html` | 运维页面（一致性/补偿队列/对账水位） |

自建自动装配注册 `LuceneFuzzymatchRouter`（core 的 `MqlFuzzymatchRouter` SPI 实现）与索引同步监听；补偿 cron 默认每分钟，对账默认每日 02:00。

## 配置键（`geelato.search.*`）

| 键 | 默认值 | 说明 |
| --- | --- | --- |
| `enabled` | true | 总开关；false 时 L1 兜底、索引写入零开销 |
| `index-root-dir` | `data/search-index` | 索引目录（每域一个子目录） |
| `max-ids` | 50000 | 路由命中上限，超限放弃路由 |
| `reindex-batch-size` | 500 | 重建分批大小 |
| `compensation-cron` | `0 * * * * ?` | 补偿队列重试周期 |
| `reconcile-cron` | `0 0 2 * * ?` | 每日水位对账 |
| `retry-backoff-minutes` | `[1,5,30,120]` | 补偿退避梯度（超 `max-retry-count`=16 转 DEAD 待人工） |
| `domains[].{id, main-entity, pk-field, fields[], children[].{entity, fk-field, fields[]}, route-entities.*}` | — | 搜索域定义 |

## 实验门禁

L2 查询路由（含 L1 改写）受**实验特性门禁**管控：构建期白名单常量 `ExperimentFeatures.SEARCH` + 请求头 `X-Gl-Experiments: search`（逗号分隔、大小写不敏感）。**索引同步/补偿/对账不受门禁**（由 `geelato.search.enabled` 控制），因此可先开启同步持续预热索引，再按发布节奏放开查询路由。扩展自定义引擎实现见[搜索引擎扩展](extension.md)。
