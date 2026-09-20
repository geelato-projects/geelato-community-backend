# geelato-search：多编号模糊检索优化

> **实验特性**：本功能的查询优化（L1 改写 + L2 路由）受实验开关管控——
> **构建期白名单**（`ExperimentFeatures.allowed()` 代码常量，打包进 jar 即决定，
> 运行时配置不可改）+ **请求级开启**（前端加 header `X-Gl-Experiments: search`，
> 逗号分隔多值，大小写不敏感）。未开启时 fuzzymatch 完全走原
> `geelato.gfn_fuzzymatch` 存储函数路径。索引同步/补偿/对账等数据准备路径
> **不受实验开关控制**（由 `geelato.search.enabled` 模块开关管），持续预热，
> 任意时刻 header 开启即可用。规范全文见 `cn.geelato.core.experiment.ExperimentGate` javadoc。

```bash
# 单请求开启（curl 示例）
curl -H "X-Gl-Experiments: search" -H "Authorization: ..." \
     -d '{"entityName": {...}}' http://host/api/meta/list
```

解决 `fuzzymatch`（`gfn_fuzzymatch` 存储函数）逐行函数调用全表扫描导致的秒级慢查询
（实测 3s+/144 万行），三层递进、结果集严格等价：

| 层 | 生效条件 | 机制 |
|---|---|---|
| L1 REGEXP 等价改写 | 默认生效，零配置 | WHERE 中 `gfn_fuzzymatch(col,'kw')>0` → `(col<>'' AND col REGEXP ?)`，内建函数替代存储函数（快 1~2 个数量级）；MySQL 8.0.22+ 还会将内建函数条件下推到派生表（vt 视图）内层 |
| L2 Lucene 路由 | 配置搜索域 + reindex 后 | fuzzymatch 条件组路由嵌入式 Lucene 倒排索引（ngram contains），检出 id 集合回注 `id IN (...)`，毫秒级 |
| L3 补偿/对账 | 随 L2 自动 | ORM 保存/删除事件异步同步索引；失败入 `search_sync_fail` 队列退避重试；每日 `update_at` 水位对账兜底直连 SQL 变更 |

## 模块

- `geelato-search-api`：`SearchEngine` 端口 + 平台中立模型 + **TCK 契约测试套件**（新实现必须全绿，保证 embedded→ES 演进语义不漂移）
- `geelato-search-lucene`：嵌入式实现（Lucene 9，NRT + NGram(2,2) + SpanNear contains），Spring Boot Starter 自动装配

## 等价性硬约束（违反即回退，不静默改变结果集）

- 原函数语义：多关键词 OR 正则包含（全角/连续逗号清洗、仅转义 `\` 和 `.`、大小写随 collation 不敏感）
- REGEXP 改写：pattern 逐步复刻函数体（含连续逗号不幂等、空关键字恒假 `a^` 等怪癖）
- Lucene 路由仅接收：无正则元字符、各词长度 ≥2、逐词 pattern 重拼 == 整体 pattern 的关键字；
  or 组内条件须全部为 fuzzymatch 且关键字一致；命中超上限（默认 5 万）不截断、放弃路由
- 验证：`FuzzymatchSupportTest`（清洗逐条对齐）、`SearchEngineContractTest` TCK、
  `FuzzymatchEquivalenceIT`（MySQL 容器内三形态命中集合对照，需 Docker）

## 启用步骤（L2）

1. 引入依赖（quickstart 已引入）；
2. 配置搜索域（`geelato.search.domains.*`，样例见 quickstart `application.properties`）；
3. 启动后触发存量重建（见下方管理端点），轮询进度至 `finished=true`；
4. 此后该域的 fuzzymatch 条件自动路由到索引（reindexed 水位门控，未重建的域不路由）。

## 管理端点（/api/search/*）

| 端点 | 说明 |
|---|---|
| `POST /api/search/reindex/{domainId}` | 触发存量重建（异步，立即返回进度对象；同域重复触发幂等，进行中直接返回当前进度） |
| `GET /api/search/reindex/{domainId}/progress` | 重建进度：done 已写入文档数、skipped 主行不可读跳过数、finished、error |
| `GET /api/search/domains` | 域清单与状态：reindexed、reindexRunning、indexedDocs |
| `GET /api/search/health` | 引擎健康 + 补偿队列（PENDING/DEAD 计数，DEAD > 0 需人工介入） |

重建机制：域级互斥、主键游标分页（默认 500/批，`geelato.search.reindex-batch-size`）、
**批式回表**（主行与每个子实体各一次 `pk IN (批)` 查询，DB 往返 O(批数)——
百万级重建约 4,000 次批量读而非百万次点查，单连接、语句快照一致、低峰执行即可）；
重建期间检索持续可用（旧索引渐进覆盖），完成后写 marker 水位并放行路由。
索引数据可疑时重复触发即可（整文档替换，幂等）。对账任务（每日水位扫描）同样走批式回表。

## 常用配置

```properties
geelato.search.enabled=true                # false 时查询走 L1 兜底，写入零额外开销
geelato.search.index-root-dir=data/search-index
geelato.search.max-ids=50000               # id 集合上限，超限放弃路由
geelato.search.compensation-cron=0 * * * * ?    # 补偿任务
geelato.search.reconcile-cron=0 0 2 * * ?       # 对账任务
```

## 补偿表

引擎首次装配自动建表：`search_sync_fail`（补偿队列，PENDING→退避重试→DEAD 告警）、
`search_sync_state`（对账水位）。DEAD 记录需人工介入（健康告警暴露计数）。
