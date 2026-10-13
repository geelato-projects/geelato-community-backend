---
title: 脚本引擎
sidebar_label: 脚本引擎
---

# 脚本引擎

geelato-script 是 Geelato Framework 的通用脚本执行引擎 SDK，基于 GraalVM Polyglot 沙箱提供安全的脚本计算能力，供异常规则判定管道、告警模板渲染等场景嵌入使用。

## 执行模型

每次脚本执行都是一次独立闭环：

1. 消费方组装 `ScriptExecution`（脚本内容、语言、超时、注入变量、执行属性），提交给 `ScriptEngine.execute`；
2. 引擎解析引擎版本（`EngineRevision` 快照）与场景档案（`ScriptProfile`），按白名单激活可见的 kit；
3. 在独立 Polyglot Context 中注入 `console`、内置 kit（`$gl.*`）、注入变量（`$ctx`/`$params` 等）与执行环境（`$env`）；
4. worker 线程执行脚本，主线程按超时强杀（`context.close(true)`）；
5. 返回 `ScriptResult`：状态（SUCCESS/ERROR/TIMEOUT）、返回值、console 日志、耗时、SQL 行数计数、kit 调用 trace 与结构化错误（类型 + 行列号 + 栈）。

## 沙箱约束

脚本在受限沙箱内运行，以下能力被禁用：

- **无文件与网络原语**：IO、进程创建、原生访问、环境变量均被拒绝（出网只能经 `$gl.http` 白名单通道）；
- **类访问白名单**：仅注解（`@GraalService`/`@GraalFunction`）方法可被调用，`Class`/`ClassLoader`/`System`/`Runtime`/`Thread` 被拒；
- **资源上限**：单执行语句数上限（`statement-limit`，防死循环）+ 执行超时（`timeoutMs`，超时强杀）；
- **kit 方法签名受控**：注册时 fail-fast 校验参数与返回类型的 interop 兼容性。

## 引擎版本与场景档案

- **引擎版本（EngineRevision）**：kit 集合的冻结快照，破坏性变更时新版本并存路由，老版本可标记 DEPRECATED/RETIRED。缺省 `v1`。
- **场景档案（ScriptProfile）**：声明场景可见的 kit 命名空间（`namespaces`）与可注入变量（`variables`），以及缺省超时。消费方按场景收窄能力边界，例如告警渲染档案只放开模板 kit。

## 内置 kit

`ScriptEngineBuilder.build()` 默认注册六个内置 kit（消费方显式注册同名 kit 时优先消费方）：

| kit | 用途 | 关键管控 |
| :--- | :--- | :--- |
| `$gl.sql` | 命名参数 SQL 桥 | 行数上限、查询超时、写通道开关 |
| `$gl.mql` | MQL 元数据查询 | 实体路由、写通道开关 |
| `$gl.http` | HTTP 出网 | 总开关、主机白名单、超时上限 |
| `$gl.encrypt` | 摘要与 AES | 密钥配置，未配置时抛错 |
| `$gl.ctx` | 执行上下文读取 | 与 `$ctx` 注入变量同源 |
| `$gl.parameter` | 参数输入读取 | 与 `$params` 注入变量同源 |

各 kit 的 API 与使用规范见[脚本编写手册](/docs/script/usage)。

## 变量体系

| 变量 | 来源 | 说明 |
| :--- | :--- | :--- |
| `$ctx` / `$gl.ctx` | 调用方 bindings | 业务上下文（触发模式、业务单号等），双入口同源 |
| `$params` / `$gl.parameter` | 调用方 params | 脚本参数输入（阈值等），双入口同源 |
| `$input` | 调用方 bindings | 管道场景中上一步输出 |
| `$env` | 引擎 | 执行元数据（execId、引擎版本、租户、用户等，只读） |
| `console` | 引擎 | 日志输出，随 ScriptResult 返回 |

注入变量受 `ScriptProfile.variables` 白名单过滤；`$gl.ctx`/`$gl.parameter` 为 kit 形态，不受该白名单约束，但受 `namespaces` 白名单约束。

## 宿主装配

Spring 环境经 `geelato.script.engine.*` 配置自动装配（`ScriptEngineAutoConfiguration`），消费方通过 `ScriptEngineCustomizer` Bean 追加 profile、revision、数据源供给（`dataSourceSupplier`）、ORM 供给（`daoSupplier`/`ormWrapper`）等。SQL 与 MQL 通道的数据源、ORM 能力均由宿主装配决定，SDK 不绑死定位机制。

## 推荐阅读

- [脚本编写手册](/docs/script/usage)：语法规范、六个内置 kit 的 API 与示例、写通道与 dry-run、最佳实践。
- [MQL 语法](/docs/mql/overview)：`$gl.mql` 入参的完整语法（字段选择、过滤、分页、排序）。
