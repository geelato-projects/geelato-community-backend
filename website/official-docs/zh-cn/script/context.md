---
title: 内置上下文
sidebar_label: 内置上下文
---

# 内置上下文

脚本执行时，引擎按场景档案注入一批预置变量。每个变量是什么、来自哪里、能做什么，本页逐一说明。

| 变量 | 来源 | 一句话说明 |
| :--- | :--- | :--- |
| `console` | 引擎 | 日志输出，随 ScriptResult 返回，恒可见 |
| `$env` | 引擎 | 执行元数据（execId、版本、租户、用户等），只读，恒可见 |
| `$ctx` | 调用方 bindings | 业务上下文（触发模式、业务单号等） |
| `$params` | 调用方 params | 脚本参数输入（阈值、天数等） |
| `$input` | 调用方 bindings | 管道场景中上一步的输出 |
| `$gl` | 引擎 | 内置与自定义 kit 的挂载对象，见[内置 Kit](/docs/script/kits) |

## console

标准日志对象，提供 `log`/`info`/`warn`/`error`/`debug` 五个方法（`debug` 按 `log` 记录）。输出不打印到控制台，而是收集后随 `ScriptResult.console` 返回，并进入执行日志（失败时自动附带末尾 10 条）。

约束：

- 单条超过 2000 字符截断；
- 总量上限 100 条，超出后静默丢弃；
- 序列化对象时最多展开 30 个成员、数组最多 50 个元素。

```javascript
console.log('rows', rows.length);
console.warn('fallback branch', $ctx.bizId);
```

## $env

执行元数据，引擎组装、只读。用于在脚本内感知"本次执行是谁、在什么配置下跑"：

| 键 | 说明 |
| :--- | :--- |
| `execId` | 本次执行的唯一 id（与 ScriptResult.execId、执行日志同源） |
| `lang` | 脚本语言（js/python） |
| `engineVersion` | 实际执行的引擎版本 |
| `profileId` | 场景档案 id |
| `startedAt` | 执行开始时间（`yyyy-MM-dd HH:mm:ss`） |
| `timeoutMs` | 本次执行超时毫秒 |
| `tenantCode` / `tenantName` | 租户编码/名称（无身份快照时为 null） |
| `appId` / `appName` | 应用 id/名称 |
| `user` | 用户对象：`id`、`userId`、`userName`、`loginName`、`orgId`、`orgName`、`deptId` |
| `attrs` | 宿主经 `attributes` 中 `env.` 前缀键透传的扩展项（无则不存在） |

```javascript
console.log('running under', $env.engineVersion, 'as', $env.user.userName);
```

## $ctx

调用方注入的**业务上下文**：触发模式、业务单号、单据快照等场景数据，经 `ScriptExecution.bindings` 的 `$ctx` 键传入。

```javascript
const bizId = $ctx.bizId;
const trigger = $ctx.trigger;   // 如 SCAN / RECHECK / TRIAL
```

与 `$gl.ctx.get(key)`/`$gl.ctx.all()` 同源（同一份数据的两种入口）。

## $params

脚本的**参数输入**：阈值、天数等由调用方配置的数值，经 `ScriptExecution.params` 便捷字段传入，引擎归一注入为 `$params`（若显式 bindings 已含 `$params`，以 bindings 为准不覆盖）。

```javascript
const days = $params.days ?? 90;
```

与 `$gl.parameter.get(key)`/`$gl.parameter.all()` 同源。

## $input

多步管道（如异常判定管道）中的约定变量：从第二步起，上一步脚本的返回值经 bindings 注入为 `$input`。引擎不对其做特殊处理，形态由管道各步的约定决定。

```javascript
// 第二步：消费第一步筛出的候选列表
return $input.filter(r => r.amount > $params.minAmount);
```

## 可见性规则

变量注入受**场景档案（ScriptProfile）**收口，与引擎版本正交（版本定"哪个版本的 kit"，档案定"哪些能力对本次执行可见"）：

- **变量白名单 `variables`**：只有名单内的变量名会被真正注入（`$ctx` 不在名单则脚本内不可见）；含 `"*"` 表示全部放行；`console` 与 `$env` 恒可见，不受该名单约束；
- **kit 白名单 `namespaces`**：`$gl.ctx`/`$gl.parameter` 等 kit 形态入口受命名空间白名单约束，而非变量白名单——即使 `$ctx` 变量被过滤，`$gl.ctx` 仍可能可用（反之亦然），按场景档案的能力择一使用；
- **冻结 `frozenBindings`**：档案开启后，Map/List 型注入变量会做冻结处理，脚本内不可覆盖成员；
- 缺省 `default` 档案全量放行（namespaces/variables 均为 `*`，不冻结，缺省超时 30s），收窄能力边界由宿主按场景定义档案（如告警渲染档案只放开模板 kit）。

宿主侧注入示例（谁给了脚本这些变量）：

```java
scriptEngine.execute(ScriptExecution.builder()
        .content(content)
        .params(Map.of("days", 90))                          // → $params
        .bindings(Map.of("$ctx", Map.of("bizId", "10234")))  // → $ctx
        .build());
```
