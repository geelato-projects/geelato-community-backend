---
title: 内置 Kit
sidebar_label: 内置 Kit
---

# 内置 Kit

kit 是脚本调用宿主能力的受控通道，`built=true` 的 kit 挂在 `$gl` 对象下。`ScriptEngineBuilder.build()` 默认注册六个内置 kit；消费方显式注册同名 kit 时优先消费方（见[自定义 Kit](/docs/script/custom-kit)）。脚本内 `$gl.help()` 可速查当前可见的 kit 与方法。

| kit | 用途 |
| :--- | :--- |
| `$gl.sql` | 命名参数 SQL 桥（含跨库路由） |
| `$gl.mql` | MQL 元数据查询（对齐平台 `/meta/*` 接口） |
| `$gl.http` | HTTP 出网（白名单管控） |
| `$gl.encrypt` | 摘要与 AES 加解密 |
| `$gl.ctx` | 执行上下文读取（与 `$ctx` 同源） |
| `$gl.parameter` | 参数输入读取（与 `$params` 同源） |

## $gl.sql：SQL 桥

数据源由宿主装配，脚本侧只管语句与参数。参数为命名参数（`:name`），查询受行数上限（`sql.row-limit`，缺省 10000）与查询超时（`sql.query-timeout-ms`，缺省 10s）收口。

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
const rows = $gl.sql.query(`
  SELECT id AS biz_id, eco_no AS biz_no, not_written_off
  FROM grant_apply
  WHERE not_written_off > :minAmount
`, { minAmount: $gl.parameter.get('minAmount') });
```

### 跨库查询

`$gl.sql` 会自动识别语句引用的物理表（依据 `platform_dev_table.connect_id` / `@Entity(connectId)` / catalog 映射的连接登记），按表所在库与执行库的关系三段路由：

| 场景 | 行为 |
| :--- | :--- |
| 全部在执行库本库（含未登记表） | 原样直连执行，零开销 |
| 同实例跨库 | 自动加库名前缀（如 `` `bizdb`.`t` ``）后仍在执行库执行；事务与 dry-run 回滚语义不变 |
| 跨实例 / 异构库 | Apache Calcite 联邦执行：单源过滤与投影下推各库，跨源 join/聚合在应用内存计算（非分布式引擎，大结果集受行数上限与超时收口） |

边界：跨实例**写**语句直接拒绝（无跨库事务保证）；未登记表、已带库名限定的表、CTE 别名、无法解析的语句均原样透传；登记的连接在 `platform_dev_db_connect` 中无配置则硬失败。同实例前缀改写只替换表名文本区间，语句其余部分（含注释与 hint）保持原文不变。路由决策记入 trace（`sql/route` 条目）与 `KIT-XDB` 日志。

## $gl.mql：MQL 元数据查询

入参为 MQL JSON 文本（语法见 [MQL 使用指引](/docs/mql/usage)），方法面对齐平台 `/meta/*` 接口（MetaController），按实体元数据自动路由数据源与身份。

| 方法 | 对齐接口 | 说明 |
| :--- | :--- | :--- |
| `$gl.mql.list(mqlJson)` | `/meta/list` | 单实体查询，返回 `{data, total, page, size}` |
| `$gl.mql.multiList(mqlJson)` | `/meta/multiList` | 多实体查询，返回 `{data: {实体名: {data, total, page, size}}}` |
| `$gl.mql.save(mqlJson)` | `/meta/save/{biz}` | 保存，返回 `{pk, dryRun}`；受写通道门控 |
| `$gl.mql.batchSave(mqlJson)` | `/meta/batchSave` | 批量保存，返回 `{pks, dryRun}`；受写通道门控 |
| `$gl.mql.multiSave(mqlJson)` | `/meta/multiSave` | 多实体保存，返回 `{count, dryRun}`；受写通道门控 |
| `$gl.mql.delete(entityName, id)` | `/meta/delete/{biz}/{id}` | 按 id 删除（id 支持逗号分隔多值），返回 `{affected, dryRun}`；受写通道门控 |
| `$gl.mql.deleteByGql(mqlJson)` | `/meta/delete2/{biz}` | 按条件删除，返回 `{affected, dryRun}`；受写通道门控 |
| `$gl.mql.explain(mqlJson, op)` | — | 解释不执行（op=list/save/delete），返回 `{sql, params, countSql}` |

```javascript
const page = $gl.mql.list('{ "platform_user": { "@fs": "id,userName", "del_status|eq": 0, "@p": "1,20" } }');
return page.data.map(u => ({ bizType: 'USER', bizId: u.ID, hitBrief: u.USERNAME }));
```

## $gl.http：HTTP 出网

三重管控：总开关 `http.enabled`（缺省开，但**白名单为空时仍拒绝所有请求**——出网必须显式配置 allowlist）、主机白名单 `http.allowlist`（条目支持精确 host、`*.` 前缀域名、可选 `scheme://` 限定）、单请求超时不超过 `http.timeout-ms`（缺省 5s）。不做证书绕过。

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

AES 复用平台 `AesUtils` 口径（AES/GCM，Base64(IV+密文)），密文与平台其他模块互操作。密钥经 `geelato.script.engine.encrypt.aes-key` 配置，未配置时 `aes`/`aesDecrypt` 抛错。执行留痕只记输入长度，不落敏感明文。

| 方法 | 说明 |
| :--- | :--- |
| `$gl.encrypt.md5(text)` | MD5 摘要，32 位小写十六进制 |
| `$gl.encrypt.md5Short(text)` | MD5 摘要 16 位（32 位截取 [8,24)） |
| `$gl.encrypt.aes(text)` | AES 加密 |
| `$gl.encrypt.aesDecrypt(cipher)` | AES 解密 |

## $gl.ctx 与 $gl.parameter

`$ctx`/`$params` 的 kit 形态入口，与注入变量同源（含义见[内置上下文](/docs/script/context)）。区别在于可见性：变量形态受场景档案的变量白名单过滤，kit 形态受命名空间白名单约束。

| 方法 | 说明 |
| :--- | :--- |
| `$gl.ctx.get(key)` / `$gl.ctx.all()` | 取上下文单键值（缺失返回 null）/ 完整 Map |
| `$gl.parameter.get(key)` / `$gl.parameter.all()` | 取参数单键值（缺失返回 null）/ 完整 Map |

## $gl.help()：kit 速查

脚本内直接打印当前可见的 kit 清单与方法名：

```javascript
return $gl.help();        // 全部 kit 速查
// return $gl.help('sql'); // 单 kit 详情（方法 + 示例 + 说明）
```

## kit 目录与 IDE 提示

- **能力清单**：`GET /api/script/catalog?profile=&version=`（需 `geelato.script.rest.enabled=true`），按场景档案过滤，是速查面板与编辑器自动补全的数据源；
- **TypeScript 声明**：宿主可用 `KitDtsGenerator.generate(...)` 把 catalog 生成为 `.d.ts` 声明文件，规则作者把产物放进工程即可在 IDE 中获得 `$gl`/`$kit` 的智能提示。
