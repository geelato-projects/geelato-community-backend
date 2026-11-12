---
title: 搜索引擎扩展
sidebar_label: 扩展指南
---

# 搜索引擎扩展指南

搜索引擎按"端口-适配器"分层：`geelato-search-api` 定义契约，`geelato-search-lucene` 是参考实现。业务系统可以替换为其他引擎（如 Elasticsearch），或在不换引擎的情况下扩展路由能力。

## SearchEngine 端口契约

`cn.geelato.search.api.SearchEngine` 是搜索引擎的唯一端口：

| 方法 | 说明 |
| --- | --- |
| `name()` | 引擎名（用于日志与路由判定） |
| `capabilities()` | 能力声明（见下） |
| `upsert(domainId, docs)` | 整文档替换写入（幂等） |
| `delete(domainId, docIds)` | 删除文档 |
| `search(domainId, query)` | 检索，返回 `SearchResult`（ids + truncated 标记） |
| `health()` | `UP / DEGRADED / DOWN` |

`SearchEngineCapabilities` 中与语义相关的核心是**一致性声明**：

- `SYNC_FAIL_FAST`：嵌入式引擎（如 Lucene），写失败快速报错，由补偿队列兜底。
- `ASYNC_EVENTUAL`：远端引擎（如 ES），最终一致——框架据此调整对账与时效预期。

### 实现契约要点

- **结果等价**：`search` 的语义是"多关键词 OR × 多字段任意位置包含"，与 SQL 层 `gfn_fuzzymatch` 多词 OR 严格对齐；实现不一致即路由漏单。
- **tenant/delStatus/appId 过滤字段**：文档写入时已带这些字段，查询侧必须按 `SearchQuery` 过滤。
- **truncated 必须如实上报**：命中数超过 `maxIdsLimit` 时返回 `truncated=true`，路由侧会放弃路由。
- **TCK 契约测试**：`geelato-search-api` 的 `SearchEngineContractTest` 是必须全绿的验收套件，保证实现可互换而语义不漂移。

## 自定义引擎接入步骤

1. 引入 `geelato-search-api` 依赖，实现 `SearchEngine` 接口；
2. 实现类注册为 Spring Bean（框架按"至多一个引擎 Bean"装配，需要多个时用 `@Primary` 控制当前生效者）；
3. 通过 TCK（`SearchEngineContractTest`）验证；
4. 存量数据：实现方提供批量回填通道后，复用既有 `POST /api/search/reindex/{domainId}` 流程（`SearchReindexService` 通过 `SearchEngine.upsert` 写入，对新引擎天然生效）。

索引同步（ORM 保存/删除事件监听）、补偿队列、每日对账与 reindex 编排都是引擎无关的通用设施，自定义引擎自动继承。

## 路由 SPI（MqlFuzzymatchRouter）

L2 路由的接入点是 core 的 `cn.geelato.core.mql.spi.MqlFuzzymatchRouter` SPI：

- `MqlQueryProcessor` 在解析 MQL 时调用 `MqlFuzzymatchRouteResolver`，从 Spring 上下文取**至多一个** Router Bean（多个直接抛错，未注册则 debug 日志并走 SQL 兜底）；
- `geelato-search-lucene` 的 `LuceneFuzzymatchRouter` 是默认实现，路由形态：① 顶层单条件逐条件替换；② or 括号子组整组替换（组内全为 fuzzymatch 且关键字一致）。

### 等价性硬约束

以下任一不满足，路由侧**必须放弃路由、回退 SQL**（info 日志记原因），这是结果等价的底线：

| 约束 | 原因 |
| --- | --- |
| 域未完成存量重建（无 marker 水位） | 索引不全，回注 id IN 会漏单 |
| 关键字含正则元字符 | 与 L1 REGEXP 语义无法对齐 |
| 拆词长度 < 2 | NGram(2,2) 无 1-gram，contains 语义不等价 |
| 拆词重拼 ≠ 整体 pattern | 与存储函数的连逗号等怪癖对齐检查失败 |
| 列无法映射到域字段（含视图 `column-map`） | 路由目标未知 |
| 命中超 `maxIds` 截断 | 截断集合不可用于等价路由 |

空命中用恒假值 `__search_no_match__` 回注，保证"检索无命中 = SQL 无结果"。

## 演进路线

Elasticsearch 实现目前**仅有设计与 TCK 骨架**（`SearchEngineCapabilities.ASYNC_EVENTUAL` 已预留），未提供实现模块。按上述契约实现并通过 TCK 后即可平滑替换，业务侧 MQL 写法与域配置不变。
