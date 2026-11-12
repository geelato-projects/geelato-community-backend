---
title: 业务异常规则与使用
sidebar_label: 规则与使用
---

# 业务异常规则与使用

本页说明如何定义异常规则、编写判定管道脚本，以及可用的 REST API。总览见[业务异常](overview.md)。

## 规则定义

规则存储在 `platform_exception_rule`（DDL `db/exception-schema.sql`，`CREATE TABLE IF NOT EXISTS` 幂等）。关键列：

| 列 | 说明 |
| --- | --- |
| `rule_code` / `rule_name` / `domain_code` | 编码（`EXC-{域码}-{三位序号}`，UK）、名称、域码 |
| `level` | P0~P3 |
| `rule_kind` / `escalation_of_rule_code` | ALERT / ESCALATE（升级链指向上级规则编码） |
| `trigger_type` / `cron_expression` | CHANGE / SCAN / BOTH；SCAN 的 cron |
| `listen_entities` | 事件腿订阅：`[{entity, operations, role, refField, fields}]` |
| `pipeline_json` | 判定管道 `{steps:[{name, lang, content}]}` |
| `threshold_json` | 阈值参数，脚本中以 `$params` 读取 |
| `owner_selector` | 参与者选择器 `{handlers:[…], cc:[…]}`（六类选择器，与告警同款） |
| `storm_json` | `{hitLimit, overflowPolicy, cooldownMinutes, inhibitedBy}` 风暴控制 |
| `resolve_policy` / `mutual_exclusive_group` / `flow_template` | 闭环策略 ANY/ALL/SELF、互斥组、流程模板 STANDARD/INTERCEPT/NOTICE |
| `biz_url_template` | "去处理"跳转模板（`{bizId}` 占位） |
| `engine_version` / `version` | 脚本契约快照 / 口径版本 |

### YAML 导入导出

`POST /api/exception/rule/import-yaml`、`POST /import-yaml-batch`、`GET /{id}/export-yaml` 支持 YAML 无损 round-trip，适合把规则放进版本库管理。规则目录素材（含升级链 `-E1/-E2` 变体）见工程 `bea/rules*` 目录。

## 判定管道编写

管道是若干单脚本的顺序执行：`steps[{name, lang, content}]`，`lang` 支持 `js`（GraalVM，IIFE 取 `return`）与 `python`（须赋值 `__result__`）；上一步输出注入下一步 `$input`。

- 绑定上下文：`$ctx{trigger, bizId, rule{code, level, kind}}`、阈值 `$params`（threshold_json）、前序步骤 `$input`。
- 取数：`$gl.sql.query(sql, $params)` 支持 `:name` 具名参数；`$gl.dao` ORM 写通道（`geelato.script.engine.dao.write-enabled=true` 且 `dao.dry-run=true` 缺省回滚）；`$gl.target.targetInstance(bizId)` 供升级链。
- 出口契约：最终步返回 hits 数组——每条 `{bizId 必填, bizType?, bizLabel?, title?, anchorTime?, snapshot?, …extras}`；步骤出口自动 snake→camelCase 归一，并做大小熔断（step-output-max-bytes）。
- 校验三时点：保存时静态检查（最终步 js 须见 `return`、python 须见 `__result__`）、启用门禁 dry-run（TRIAL 态真实执行）、运行期兜底（非数组整体丢弃 `HITS_NOT_ARRAY`；缺 bizId 丢条留痕；title/snapshot/anchorTime 缺失仅留痕不丢条）。

### 示例（种子规则 EXC-BL-015 未收款放单，单步 js 管道）

```javascript
// threshold_json: {"daysAfer":7,"lookbackDays":90}
const rows = $gl.sql.query(`
  SELECT 'ORDER' AS biz_type,
         b.order_id AS biz_id, e.eco_no AS biz_label,
         e.operator_id, e.operator_name,
         COALESCE(g.grant_time, b.update_at) AS grant_time,
         DATEDIFF(NOW(), COALESCE(g.grant_time, b.update_at)) AS days_since_grant
  FROM il_order_bol b
  JOIN il_eco e ON e.id = b.order_id AND e.del_status = 0
  LEFT JOIN (
    SELECT order_id, MAX(biz_status_time) AS grant_time
    FROM il_order_bol_grant WHERE del_status = 0 GROUP BY order_id
  ) g ON g.order_id = b.order_id
  WHERE b.del_status = 0
    AND b.bol_status IN ('2','3','4')
    AND DATEDIFF(NOW(), COALESCE(g.grant_time, b.update_at)) >= :daysAfer
    AND COALESCE(g.grant_time, b.update_at) >= DATE_SUB(NOW(), INTERVAL :lookbackDays DAY)
    AND EXISTS (
      SELECT 1 FROM il_so_container_fee_detail f
      WHERE f.order_id = b.order_id AND f.del_status = 0
        AND f.fee_type = '1' AND f.amount_not_written_off > 0
    )
`, $params)

return rows.map(r => ({
  bizId: r.biz_id,
  bizType: r.biz_type,
  bizLabel: r.biz_label,
  title: `订单 ${r.biz_label} 已放单 ${r.days_since_grant} 天，应收仍未核销`,
  anchorTime: r.grant_time,
  snapshot: { operatorName: r.operator_name, daysSinceGrant: r.days_since_grant }
}))
```

:::warning 性能提示
SCAN 扫描腿的条件查询在库内全量执行，大表规则先核对应有索引（工程 `bea/rules-practical/_scan_rule_indexes.sql` 提供扫描腿索引前置清单），避免逐行全表扫。
:::

## REST API

:::note
端点声明自 `@ApiRuntimeRestController`，在带 `/api` 前缀的宿主中实际路径带 `/api`。规则管理 REST 不受引擎开关控制；引擎未开启时试跑/扫描/重算返回明确错误。
:::

**规则管理 `/api/exception/rule`**

| 端点 | 说明 |
| --- | --- |
| `POST /pageQuery` | 规则分页（应用严格隔离，无 appId fail-closed 返回空集） |
| `GET /{id}`、`POST /save` | 详情 / 保存 |
| `POST /{id}/enable`、`POST /{id}/disable` | 启停（启用走契约门禁 dry-run） |
| `POST /import-yaml`、`POST /import-yaml-batch`、`GET /{id}/export-yaml` | YAML 导入导出 |
| `POST /{id}/trial-run` | 试跑（真实执行引擎 TRIAL 态，受限超时） |
| `POST /{id}/scan?appId` | 手动触发单规则扫描 |
| `GET /escalation-chain/{ruleCode}` | 升级链查询 |
| `POST /contract-check` | 管道契约静态检查 |

**工作台 `/api/exception`**

| 端点 | 说明 |
| --- | --- |
| `POST /pageQuery`、`GET /summary`、`GET /domain-stats` | 实例分页 / 汇总 / 域统计 |
| `GET /{id}`、`GET /{id}/steps`、`GET /{id}/handles` | 实例详情 / 处置步骤 / 轨迹 |
| `POST /{id}/claim`、`/note`、`/resolve`、`/ignore`、`/escalate` | 认领 / 备注 / 闭环 / 忽略 / 升级 |
| `POST /{id}/recalculate` | 手工重算（复评同一内核） |

**管理 `/api/exception/admin`**

| 端点 | 说明 |
| --- | --- |
| `POST /entity-event` | 变更事件入口（EntityEventReactor） |
| `POST /scan`、`GET /scan-logs` | 手动全量扫描 / 扫描日志 |
| `POST /archive` | 实例归档 |

## 内置种子规则

`db/exception-rule-practical-init.sql` 提供 8 条实际业务口径规则（**默认停用**，核阅后启用），均为单步 js 管道 + `SCAN` 触发：

| 规则 | 业务口径 | 域 / 级别 |
| --- | --- | --- |
| EXC-ODR-001 | 委托受理滞留 | ODR |
| EXC-ODR-018 | 截关前未进港 | ODR |
| EXC-ODR-019 | 到港超期未提柜 | ODR |
| EXC-ODR-020 | 提柜超期未还箱 | ODR |
| EXC-BL-015 | 未收款放单 | BL / P0 |
| EXC-BL-016 | 逾期放单 | BL |
| EXC-CUS-001 | 客户超授信 | CUS |
| EXC-FIN-018 | 进口订单结算逾期 | FIN |
