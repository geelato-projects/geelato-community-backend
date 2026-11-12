---
title: 业务异常总览
sidebar_label: 业务异常
---

# 业务异常（geelato-exception）

业务异常预警中心是一套**规则化判定 → 一人一实例 → 处置闭环**的业务异常纳管引擎：把"委托受理滞留""超授信放单""逾期未还箱"这类业务口径沉淀为可配置的异常规则，由脚本判定管道批量/实时发现命中，再为每个"规则 × 业务对象 × 参与人"维护唯一的异常实例，直到闭环。

模块坐标 `cn.geelato:geelato-exception`。**引擎默认关闭**（`geelato.exception.engine.enabled` 缺省 false）；规则管理 REST 不受开关控制，引擎 Bean（扫描/事件/提交链）仅在开关开启后装配。

## 核心概念

| 概念 | 说明 |
| --- | --- |
| 异常规则 | 表 `platform_exception_rule`：`rule_code`（`EXC-{域码}-{三位序号}`）、`domain_code`（ODR/BL/FIN/CUS/SLOT/QLT…）、`level`（P0~P3）、`rule_kind`（ALERT 普通告警 / ESCALATE 升级链，`escalation_of_rule_code` 指向上级规则） |
| 触发方式 | `trigger_type`：`CHANGE`（实体变更事件复评）/ `SCAN`（定时全量扫描）/ `BOTH`，SCAN 配 `cron_expression` |
| 判定管道 | `pipeline_json.steps[{name, lang, content}]`，逐步执行 geelato-script 单脚本（js/python），上一步输出注入下一步 `$input`；最终步返回 **hits 契约**数组 |
| hits 契约 | 命中条目类型化载体 `Hit`：**`bizId` 唯一必填**，其余 `bizType`/`bizLabel`/`title`/`anchorTime`/`snapshot` 可选；契约外字段进 `extras`；三时点校验（保存静态检查 / 启用门禁 dry-run / 运行期兜底） |
| 参与者选择器 | `owner_selector` 六类：`USER`（指定人）、`ROLE`（角色全员）、`DEPT`（部门全员）、`DEPT_ROLE`（部门+角色，可取业务组织）、`BIZ_FIELD`（命中对象业务字段，缺省 ownerId）、`ORG_TREE_UP`（沿组织树向上 level 层的升级链） |
| 一人一实例 | 实例表 `platform_exception`：指纹 `active_fingerprint = {rule_code}\|{biz_type}\|{biz_id}\|{participant_id}` 终身唯一；状态 `OPEN/PROCESSING/RESOLVED/IGNORED/ESCALATED`；在途续期（last_seen_at/hit_count）、终态重开、组级 AUTO_RECOVER 自动恢复 |

## 运行机制

```
扫描腿（ExceptionScanScheduler，自管理守护线程，默认 30s tick）
  → 规则行 CAS 抢占（scan_status/scan_runner，stale 回收，集群防重）
  → EngineContextExecutor 重建租户上下文 → 管道 SCAN 全量判定 → 命中提交

事件腿（EntityEventReactor）
  → listen_entities [{entity, operations, role, refField, fields}] 匹配 ORM 变更事件
  → PRIMARY/RELATED 归一（子表 id 换主单 id）→ RECHECK 单对象复评（与扫描同管道口径，不漂移）
  → 事件只影响时效不影响正确性：重复/乱序/幽灵信号无害，判定以复评时库内现查为准

命中提交链（HitSubmitService）
  抑制（storm_json.inhibitedBy 在途命中抑制）→ 熔断（hitLimit 溢出按 overflowPolicy 聚合/丢弃）
  → 冷却（cooldownMinutes）→ 参与者求解 → 一人一实例 upsert → 知会触达（handlers 与 cc 各自成实例）
```

- **扫描调度**不依赖宿主 `@EnableScheduling`（平台模块自管理调度惯例）；到期基线取最近一次扫描日志 start_at，missed fire 只补一次。
- **升级链**：ESCALATE 规则通过 `$gl.target.targetInstance(bizId)` 取上级规则在途实例，实现"上级规则只对已发生下级异常的对象判定"。
- **与业务告警衔接**：`platform_exception` 实例生命周期事件是告警中心（geelato-alert）当前唯一内置事件源，见[业务告警](../alert/overview.md)。

## 启用与配置

```properties
# 引擎总开关（默认 false；关闭时规则管理仍可用，仅不跑判定）
geelato.exception.engine.enabled=true
```

| 配置（`geelato.exception.engine.*`） | 默认 | 说明 |
| --- | --- | --- |
| `scan.tick-seconds` | 30 | 扫描 tick 周期 |
| `scan.thread-pool-size` | 2 | 扫描线程池 |
| `scan.lock-stale-minutes` | 30 | CAS 抢占超时回收 |
| `pipeline.step-timeout-ms` / `pipeline-timeout-ms` | 30000 / 120000 | 单步/整管道超时 |
| `pipeline.trial-timeout-ms` | 30000 | 试跑超时 |
| `pipeline.step-output-max-bytes` | 2MB | 单步输出大小熔断 |
| `sql.connect-id` | primary | 管道取数默认连接 |
| `http.enabled` / `allowlist` / `timeout-ms` | false / 空 / 5000 | 管道出网白名单（与脚本底座 `geelato.script.engine.http.enabled` 是两级开关） |
| `trigger.queue-capacity` | 1000 | 变更事件有界队列（满则丢弃留痕） |

脚本执行底座（geelato-script，前缀 `geelato.script.engine`）三通道默认开启：`python-enabled=true`、`http.enabled=true`（空白名单仍拒绝）、`dao.write-enabled=true`（`dao.dry-run=true` 缺省仍回滚）。

## 判定脚本能力（exception-judge profile）

异常判定脚本运行在 geelato-script 的 `exception-judge` profile 下：**不限定命名空间与变量**——SDK 内置 kit 与消费方注册的注解 kit 全部可见，仅收紧超时预算。常用入口：

- `$gl.sql` / `$gl.dao`：管道取数（SQL 查询与 ORM 写通道）
- `$gl.target.targetInstance(bizId)`：升级链取上级规则在途实例
- 联邦查询 ConnectPoolProvider：按 connect-id 跨数据源取数

脚本编写与契约细节见[业务异常规则与使用](usage.md)。

## 当前状态

- **后端引擎全链路已实现**：管道判定、hits 契约、一人一实例、扫描/事件双腿、升级链、工作台 REST、YAML 导入导出、8 条实际业务种子规则（默认停用），配套单测。
- **前端工作台未上线**：`/exception/*` API 已备，前端页面为设计原型（bea/page 09-11 号原型页），实体可视化规则编辑（V2）处于设计阶段。
