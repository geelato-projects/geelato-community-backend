---
title: 脚本使用手册
sidebar_label: 脚本使用手册
---

# 脚本使用手册

本页说明如何执行一段脚本：脚本结构、SDK 与 REST 两种执行入口、引擎版本的指定与管理、写通道与 dry-run、错误处理与最佳实践。脚本内可用的变量与 kit 见[内置上下文](/docs/script/context)与[内置 Kit](/docs/script/kits)。

## 脚本结构

脚本是一段以 `return` 结尾的表达式或语句块，返回值即执行结果。语言缺省 js（`lang` 可选 python，需 GraalVM 运行时组件在位）。

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

## 执行脚本（SDK）

SDK 门面为 `cn.geelato.script.core.ScriptEngine`，组装 `ScriptExecution` 后提交 `execute`：

```java
ScriptResult result = scriptEngine.execute(ScriptExecution.builder()
        .scriptName("EXC-BL-005")                 // 脚本定位标识，仅用于执行日志
        .engineVersion("v1")                       // 引擎版本（空 = 缺省版本）
        .profileId("scan")                         // 场景档案（空 = default）
        .lang("js")                                // js（缺省）/ python
        .content(scriptContent)                    // 脚本内容
        .params(Map.of("days", 90))                // 业务输入，注入为 $params
        .bindings(Map.of("$ctx", Map.of("bizId", "10234")))
        .timeoutMs(5000L)                          // 超时毫秒（缺省用 profile 的 defaultTimeoutMs）
        .dryRun(true)                              // 写通道 dry-run 覆盖（null 用引擎配置）
        .build());

if ("SUCCESS".equals(result.getStatus())) {
    Object value = result.getValue();              // 脚本 return 值（List/Map/String/Number/Boolean/null）
} else {
    ScriptError error = result.getError();         // 类型 + 行列号 + 栈
}
```

`ScriptExecution` 主要字段：

| 字段 | 说明 |
| :--- | :--- |
| `lang` / `content` | 语言（js 缺省 / python）与脚本内容 |
| `scriptName` | 脚本定位标识（规则 code、模板 code 等），仅用于执行日志与调试 Source 名，不注入脚本 |
| `engineVersion` | 引擎版本，空取 `geelato.script.engine.default-version`（缺省 `v1`） |
| `profileId` | 场景能力档案 id，空取 `default` |
| `bindings` | 注入变量，key 为 `$ctx`/`$params` 等带 `$` 前缀名，受 profile 变量白名单过滤 |
| `params` | 业务输入便捷字段，引擎归一注入为 `$params`（显式 bindings 已含 `$params` 时不覆盖） |
| `attributes` | 执行级属性袋，kit 经 KitContext 读取，**不注入脚本** |
| `timeoutMs` | 本次执行超时毫秒（缺省用 profile 的 `defaultTimeoutMs`，default 档案为 30s） |
| `identity` | 显式身份快照（异步消费方传入；缺省在调用线程捕获） |
| `dryRun` | 写通道 dry-run 执行级覆盖（null 用引擎配置 `dao.dry-run`） |

返回的 `ScriptResult`：`status`（SUCCESS/ERROR/TIMEOUT）、`value`（返回值）、`console`（脚本日志）、`costMs`、`rowCount`（SQL 行数计数）、`engineVersion`（实际执行的版本）、`execId`、`trace`（kit 调用踪迹，上限 100 条）、`deprecationWarning`、`error`（类型 SYNTAX/RUNTIME/TIMEOUT + 行列号 + 栈）。

### 语法预检

不执行脚本、只做语法检查，产出问题定位清单（`code`/`message`/`line`/`col`），适合保存前校验：

```java
List<ScriptIssue> issues = scriptEngine.parseOnly("js", content, "v1");
```

### 执行日志

每次执行生成 `execId` 串联全链路日志（`SCRIPT-START`/`SCRIPT-BODY`/`SCRIPT-PARAM`/`SCRIPT-DONE`/`SCRIPT-FAIL`，kit 调用为 `KIT`/`KIT-DRYRB`）。在 geelato-web-quickstart 宿主中，`cn.geelato.script` 包日志路由到 `${LOG_DIR}/script/` 目录，失败时自动附带栈帧与末尾 10 条 console。

## 执行脚本（REST）

REST 端点是测试/调试辅助通道，**默认不装配**：`geelato.script.rest.enabled=true` 时开启，仅限内网可信环境。请求体不收 `attributes`/`identity`，避免任意属性与身份伪造。

```properties
geelato.script.rest.enabled=true
```

| 端点 | 说明 |
| :--- | :--- |
| `POST /api/script/execute` | 执行脚本，请求体 `lang`/`content`/`scriptName`/`engineVersion`/`profileId`/`params`/`bindings`/`timeoutMs`，返回 `ScriptResult` |
| `POST /api/script/parse` | 语法预检（不执行），请求体 `lang`/`content`/`engineVersion`，返回问题清单 |
| `GET /api/script/catalog?profile=&version=` | kit 能力清单（速查面板 / 编辑器自动补全的数据源） |
| `GET /api/script/versions` | 引擎版本清单（id、状态、迁移提示、命名空间集合） |

```bash
curl -X POST http://127.0.0.1:8080/api/script/execute \
  -H 'Content-Type: application/json' \
  -d '{"scriptName":"demo","lang":"js","engineVersion":"v1",
       "params":{"days":90},
       "content":"return $gl.parameter.get(\"days\") * 2;"}'
```

配套的调试台页面见[在线调试](/docs/script/debugging)。

## 指定引擎版本

引擎版本（EngineRevision）是**脚本可观察契约的冻结快照**：kit 集合与方法签名、语言级别（`esVersion`，缺省 2022）、变量约定。破坏性变更时注册新版本并存路由，老版本可标记弃用、下线，老脚本继续按快照执行不受影响。

### 如何指定

- SDK：`ScriptExecution.engineVersion`，空值取缺省版本（`geelato.script.engine.default-version`，缺省 `v1`）；
- REST：请求体 `engineVersion`，空 = 缺省版本；
- 返回值 `ScriptResult.engineVersion` 恒为实际执行的版本，与 `$env.engineVersion` 同源。

### 版本状态与执行行为

| 状态 | 执行行为 |
| :--- | :--- |
| CURRENT | 正常执行 |
| DEPRECATED | 仍执行成功，但 `ScriptResult.deprecationWarning` 回填迁移提示，console 首条追加 `[warn]` |
| RETIRED | 直接拒绝（`引擎版本已下线: …`），须迁移后重发 |
| 未知版本 | 启动执行即报错（`未知引擎版本: …（可用: …）`），错误信息列出全部可用版本 |

### 版本管理

版本状态运行时可热切换，不重启生效。管理端点默认不装配，`geelato.script.admin.enabled=true` 时开启（与 `rest.enabled` 相互独立）：

| 端点 | 说明 |
| :--- | :--- |
| `GET /api/script/admin/versions` | 全部版本清单 |
| `POST /api/script/admin/version/default` | 热切缺省版本（body `versionId`；已下线版本不可设为缺省） |
| `POST /api/script/admin/version/deprecate` | 标记弃用（body `versionId`/`migrateHint`；至少保留一个 CURRENT 版本） |
| `POST /api/script/admin/version/retire` | 下线（须先标记弃用） |

新版本通常在宿主装配期派生注册（以基线版本快照为底声明 kit 增删覆盖），见[自定义 Kit：版本化固化](/docs/script/custom-kit#版本化固化)。

## 写通道与 dry-run

写方法（`$gl.sql.update/execute`、`$gl.mql.save/batchSave/multiSave/delete/deleteByGql`）受统一门控：

- **总开关**：`geelato.script.engine.dao.write-enabled` 缺省 `true`，关闭时写方法抛错拒绝；
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
- **日志留痕**：关键分支用 `console.log/warn` 留下排查线索（随 ScriptResult 与执行日志返回）；
- **失败要显式**：不吞异常、不以空数组兜底掩盖错误——异常规则的判定管道对非数组返回值整体丢弃并留痕。
