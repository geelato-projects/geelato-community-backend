---
title: 脚本编写手册
sidebar_label: 脚本编写手册
---

# 脚本编写手册

本页说明平台脚本的编写规范：脚本结构、变量体系、六个内置 kit（`$gl.*`）的 API 与示例、写通道与 dry-run、错误处理与最佳实践。

## 脚本结构

脚本是一段以 `return` 结尾的表达式或语句块，返回值即执行结果。语言缺省 js（`lang` 可选 python）。

```javascript
// 单步脚本：筛出未核销金额超阈值的单据
const rows = $gl.sql.query(`
  SELECT id AS biz_id, eco_no AS biz_no, not_written_off
  FROM grant_apply
  WHERE not_written_off > :minAmount
`, { minAmount: $gl.parameter.get('minAmount') });

return rows.filter(r => r.NOT_WRITTEN_OFF > 0)
    .map(r => ({ bizType: 'BOL_GRANT', bizId: r.BIZ_ID, bizNo: r.BIZ_NO }));
```

多步管道（如异常判定管道 `pipeline_json.steps`）中，第二步起可读上一步输出 `$input`。

## 变量体系

| 变量 | 说明 | 示例 |
| :--- | :--- | :--- |
| `$ctx` / `$gl.ctx.get(key)` / `$gl.ctx.all()` | 调用方注入的业务上下文 | `$ctx.bizId`、`$gl.ctx.get('trigger')` |
| `$params` / `$gl.parameter.get(key)` / `$gl.parameter.all()` | 脚本参数输入（阈值等） | `$params.days`、`$gl.parameter.get('days')` |
| `$input` | 管道中上一步输出 | `$input.filter(...)` |
| `$env` | 执行元数据（只读）：execId、engineVersion、profileId、tenantCode、user 等 | `$env.execId` |
| `console` | 日志：log/info/warn/error/debug | `console.log('rows', rows.length)` |

`$ctx`/`$params` 是注入变量形态（可能被场景档案的变量白名单过滤）；`$gl.ctx`/`$gl.parameter` 是 kit 形态，与前者同源，不受变量白名单约束。两种写法按场景档案能力择一使用。

## $gl.sql：SQL 桥

数据源由宿主装配（`geelato.script.engine.sql.connect-id` 等定位策略），脚本侧只管语句与参数。参数为命名参数（`:name`），查询受行数上限（`sql.row-limit`，缺省 10000）与查询超时（`sql.query-timeout-ms`，缺省 10s）收口。

| 方法 | 说明 |
| :--- | :--- |
| `$gl.sql.query(sql, params)` | 查询返回行数组（字段名→值）；行数计入本次执行的 rowCount |
| `$gl.sql.queryOne(sql, params)` | 查询单行，无结果返回 null |
| `$gl.sql.queryValue(sql, params)` | 查询单值，无结果返回 null |
| `$gl.sql.update(sql, params)` | 写语句（INSERT/UPDATE/DELETE），返回 `{affected, dryRun}` |
| `$gl.sql.execute(sql)` | 执行 DDL/无参语句，门控同 update |

```javascript
const total = $gl.sql.queryValue('SELECT COUNT(*) FROM grant_apply', {});
const row = $gl.sql.queryOne('SELECT * FROM grant_apply WHERE id = :id', { id: '1' });
```

## $gl.mql：MQL 元数据查询

入参为 MQL JSON 文本（语法见 [MQL 使用指引](/docs/mql/usage)），方法面对齐平台 `/meta/*` 接口（MetaController），按实体元数据自动路由数据源与身份。

| 方法 | 对齐接口 | 说明 |
| :--- | :--- | :--- |
| `$gl.mql.list(mqlJson)` | `/meta/list` | 单实体查询，返回 `{data, total, page, size}` |
| `$gl.mql.multiList(mqlJson)` | `/meta/multiList` | 多实体查询，返回 `{data: {实体名: {data, total, page, size}}}` |
| `$gl.mql.save(mqlJson)` | `/meta/save/{biz}` | 保存，返回 `{pk, dryRun}`；受写通道门控 |
| `$gl.mql.batchSave(mqlJson)` | `/meta/batchSave` | 批量保存，返回 `{pks, dryRun}`；受写通道门控 |
| `$gl.mql.multiSave(mqlJson)` | `/meta/multiSave` | 多实体保存，返回 `{count, dryRun}`；受写通道门控 |
| `$gl.mql.delete(entityName, id)` | `/meta/delete/{biz}/{id}` | 按 id 删除（id 支持逗号分隔多值），返回 `{affected, dryRun}` |
| `$gl.mql.deleteByGql(mqlJson)` | `/meta/delete2/{biz}` | 按条件删除，返回 `{affected, dryRun}`；受写通道门控 |
| `$gl.mql.explain(mqlJson, op)` | — | 解释不执行（op=list/save/delete），返回 `{sql, params, countSql}` |

```javascript
const page = $gl.mql.list('{ "platform_user": { "@fs": "id,userName", "del_status|eq": 0, "@p": "1,20" } }');
return page.data.map(u => ({ bizType: 'USER', bizId: u.ID, hitBrief: u.USERNAME }));
```

## $gl.http：HTTP 出网

三重管控：总开关 `http.enabled`（缺省关）、主机白名单 `http.allowlist`（条目支持精确 host、`*.` 前缀域名、可选 `scheme://` 限定）、单请求超时不超过 `http.timeout-ms`。不做证书绕过。

| 方法 | 说明 |
| :--- | :--- |
| `$gl.http.get(url, params, headers)` | GET，返回响应体字符串 |
| `$gl.http.post(url, body, headers)` | POST（body 为字符串原样、对象自动 JSON），返回响应体字符串 |
| `$gl.http.put(url, body, headers)` / `$gl.http.patch(...)` | PUT/PATCH，同 post |
| `$gl.http.delete(url, headers)` | DELETE，返回响应体字符串 |
| `$gl.http.request({method, url, params, body, headers, timeoutMs})` | 完整请求，返回 `{status, headers, body}` |

```javascript
const resp = $gl.http.request({ method: 'POST', url: baseUrl, body: { code: 'X1' } });
if (resp.status !== 200) { console.warn('bad status', resp.status); }
return JSON.parse(resp.body);
```

## $gl.encrypt：摘要与 AES

AES 复用平台 `AesUtils` 口径（AES/GCM，Base64(IV+密文)），密文与平台其他模块互操作。密钥经 `geelato.script.engine.encrypt.aes-key` 配置，未配置时 `aes`/`aesDecrypt` 抛错。

| 方法 | 说明 |
| :--- | :--- |
| `$gl.encrypt.md5(text)` | MD5 摘要，32 位小写十六进制 |
| `$gl.encrypt.md5Short(text)` | MD5 摘要 16 位（32 位截取 [8,24)） |
| `$gl.encrypt.aes(text)` | AES 加密 |
| `$gl.encrypt.aesDecrypt(cipher)` | AES 解密 |

## 写通道与 dry-run

写方法（`$gl.sql.update/execute`、`$gl.mql.save/batchSave/multiSave/delete/deleteByGql`）受统一门控：

- **总开关**：`geelato.script.engine.dao.write-enabled` 缺省 `false`，关闭时写方法抛错拒绝；
- **dry-run**：`geelato.script.engine.dao.dry-run` 缺省 `true`——写语句在事务内真实执行后回滚（验证约束与 SQL 正确性），返回值带 `dryRun: true` 标记；执行级可用 `ScriptExecution.dryRun` 覆盖（如启用门禁时真实落库）。

## 错误处理

kit 抛出的错误以脚本 `RUNTIME` 异常呈现（消息含精确原因与配置项，如"SQL 写通道未启用（geelato.script.engine.dao.write-enabled）"）；执行结果层面 `ScriptResult.error` 携带类型（SYNTAX/RUNTIME/TIMEOUT）、行列号与栈。脚本内可用 try/catch 捕获后转业务语义：

```javascript
try {
  return $gl.sql.query('...', {});
} catch (e) {
  console.error('query failed', e.message);
  return [];
}
```

## 最佳实践

- **只取所需**：SELECT 明确列清单，配合 `@p` 分页（MQL）或 WHERE 收窄窗口（SQL），避免整表扫描撞行数上限；
- **参数化**：一律命名参数（`:name`），杜绝字符串拼接 SQL；
- **可解释优先**：复杂判定先经 `$gl.mql.explain` 验证生成的 SQL 再上线；
- **日志留痕**：关键分支用 `console.log/warn` 留下排查线索（随 ScriptResult 与扫描日志返回）；
- **失败要显式**：不吞异常、不以空数组兜底掩盖错误——异常规则的判定管道对非数组返回值整体丢弃并留痕。
