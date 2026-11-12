---
title: 工作流业务接入
sidebar_label: 业务接入
---

# 工作流业务系统接入指南

本页说明如何部署工作流服务、业务系统如何接入（认证、数据源、配置）。端点清单见 [REST API 清单](api.md)，配置项明细见 [Workflow 模块配置](../system-config/workflow.md)。

## 部署形态

- 产物：`geelato-workflow-quickstart` 可执行 jar（仓库 `bin/` 提供 build.bat / run.bat / publish.bat）。
- 端口：`server.port=8080`（前端开发端口 5173）。
- **双数据源**（均 HikariCP，`WorkflowDataSourceConfiguration` 定义）：

| 数据源 | 前缀 | 用途 |
| --- | --- | --- |
| workflow | `spring.datasource.workflow.*` | 流程库：Activiti `ACT_*` 与 `ext_*` 扩展表 |
| platform | `spring.datasource.platform.*` | 平台库桥接：组织/用户/角色快照同步、跨库视图（别名 `primaryDataSource` 兼容框架约定） |

- Activiti 关键参数：`spring.activiti.dbHistoryUsed=true`、`historyLevel=audit`、`databaseSchemaUpdate=false`（表结构由脚本管理，见仓库 `doc/sql/`）。
- 平台库初始化脚本：`ext_hi_action.sql`、`ext_re_procdef.sql`、`ext_to_read*.sql`、视图 `v_platform_wf_my_task.sql` 等。

## 认证接入（三条通道）

1. **本地登录**：`POST /api/auth/login`，从工作流库 `wf_user` 表校验（SHA-1 + 盐 1024 迭代），签发 `JWTBearer` 令牌——适合独立试运行。
2. **统一认证（OAuth2）**：`GET /oauth2/callback` 承接认证中心回调换 token；配置 `geelato.oauth2.url/clientId/clientSecret/redirectUri/frontendRedirectUri`。接入认证中心详见 [Geelato AuthServer](../authentication/overview.md)。
3. **auth-client 安全契约**：业务侧与工作流侧都通过 `geelato.auth-client` 从认证中心取组织/用户/角色数据（`geelato.auth.client.base-url/client-id/client-secret`）。

**组织数据快照**：工作流引擎通过 `WorkflowDBSecurityProvider` + `DBUserSnapshotLoader` / `DBOrgSnapshotLoader` / `DBRoleSnapshotLoader` 从平台库同步组织/用户/角色快照，流程节点选人与权限判定都基于快照。

## 配置要点

| 配置 | 说明 |
| --- | --- |
| `geelato.workflow` | 工作流模块开关（quickstart 默认 true） |
| `geelato.workflow.admin-roles` | 管理员角色 |
| `geelato.workflow.form.default-page-url` | 节点表单页模板（`http://localhost:5173/{pageId}?bizId={bizId}`） |
| `geelato.workflow.attach.store-dir` / `download-url-prefix` | 附件存储与下载前缀 |
| `geelato.workflow.script.sql.connect-id` / `timeout-ms` | 脚本数据源与超时 |
| `geelato.oauth2.*`、`geelato.auth.client.*` | 认证中心与安全契约 |

## 业务系统对接模式

1. **HTTP API 接入**：业务系统在审批动作处调用 `/api/engine/startWorkflow` 发起，处理 `/api/my/pageQueryMyPendingTask` 待办与 `getCurrentTask` 详情，回调表单页完成办理；`queryNextStepPrediction` 可在提交前预演路径。
2. **通知回调**：`NoticeController /send` + Webhook/PlatformNotice 通道把待办事件推给业务系统；失败自动重试（`ext_notice_send_log`）。
3. **附件与表单**：表单页 URL 模板以 `{pageId}/{bizId}` 参数化；附件经 `/api/attach/*` 上传下载。

:::warning MetaConfiguration 注意
quickstart 的 `MetaConfiguration` 在**构造函数**中执行 `MetaManager.singleInstance().scanAndParse("cn.geelato.web", false)` + `parseDBMeta(null)`：扫描包名硬编码为 `cn.geelato.web`，且在构造期即连库解析全部元数据。宿主数据源未就绪、平台表缺失或包名不符时启动即失败；二次定制时注意 MetaManager 是全局单例，避免状态污染。
:::

## 演示页

quickstart 自带 `static/script-demo.html`（脚本试验场演示）与 `static/security-contract-demo.html`（安全契约演示），可作为接入联调参照。
