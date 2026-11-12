---
title: 工作流总览
sidebar_label: 总览
---

# Geelato Workflow 工作流

geelato-workflow 是**独立部署**的工作流服务，基于 Activiti 引擎（7.1.0.M6），提供流程定义、审批流转、传阅、附件、通知与流程权限等能力；业务系统通过 REST API 接入，拥有独立的工作流数据源与平台库桥接。

:::info 仓库位置
geelato-workflow 是独立仓库（`geelato-workflow`），不属于 geelato-community 聚合构建。
:::

## 模块结构

| 模块 | 说明 |
| --- | --- |
| `geelato-workflow-core` | 流程领域模型：DTO/PO/VO、WorkflowDefine ↔ BPMN 转换、枚举、`WorkflowResult` 统一返回封装 |
| `geelato-workflow-engine` | 引擎与全部 REST 控制器（define / engine / my / toread / attach / wfpolicy / notice / script / auto 等） |
| `geelato-workflow-quickstart` | 可执行宿主（默认端口 **8080**，`server.port=8080`），自带 Vue 前端（开发端口 5173） |

技术底座：Activiti `activiti-spring-boot-starter`、mybatis-plus、HikariCP、geelato-security、geelato-script、geelato-auth-client。

## REST 范式

- 控制器标注组合注解 `@WFApiRestController`（= `@ApiRestController(category="workflow")` + JSON produces）；quickstart 的 `ApiPrefixAutoConfiguration` 为其统一加 `/api` 前缀。
- 返回统一为 `WorkflowResult`（`ResultCode` / `WorkflowErrorCode`）。
- 端点全集见 [REST API 清单](api.md)。

## 流程定义与部署链路

设计器产出 **JSON 流程定义**（`WorkflowDefine` / `WorkflowNode` / `GeelatoSequenceFlow`），部署时经 `WorkflowBpmnConverter` 系列转换为 **BPMN 2.0 BpmnModel**（含 isAgree 扩展元素与 DI 图形坐标），再交 Activiti 仓储服务部署：

```
保存版本 createDraftVersion → 发布 deployDefineVersion
  → DefineService: repositoryService.createDeployment()
      .key(defineKey).category(appId).tenantId(tenantCode)
      .addBpmnModel(defineKey + ".bpmn", bpmnModel).deploy()
```

- 版本化：`queryDefineVersions` / `getDefineVersion`；调试发布 `deployDebugDefineVersion`；批量部署 `batchDeployDefine`。
- 互转：`convertToBpmn` / `convertToJson` / `getDefineBpmn`（样例 BPMN 在 engine 模块 `resources/define/*.bpmn20.xml`）。
- **编辑锁**：定义编辑期间通过 `DefineLockController`（acquire/renew/release）防止并发修改。
- 会签与跳转：`GroupVoteTaskListener` / `VoteCollectTaskListener` 计数会签；`Jump2TargetFlowNodeCmd` 自由跳转。

## 引擎能力

| 能力 | 要点 |
| --- | --- |
| 发起 | `startWorkflow`（及原子性版本、调试发起、`runWorkflow`） |
| 预测 | `queryNextStep(Prediction)`、`queryStepUser(Prediction)`：提交前预演下一步与办理人 |
| 办理 | 撤回 `withdraw`、驳回 `rejectToLastStep / rejectToStart / rejectToNode`、转办 `transferTask`、强制终止 / 删除实例 |
| 我的任务 | 待办/已办/待阅/已阅分页查询、我发起的；常用语 CRUD |
| 传阅 | `sendToRead`、`completeRead`、批量已阅 |
| 附件 | 上传（multipart）与下载（类级 `/api/attach`） |
| 通知 | `NoticeController /send`；SPI 通道：Email / PlatformNotice / Webhook / External，失败重试调度，落 `ext_notice_send_log` |
| 流程权限 | `WfPolicyController`：策略与绑定 CRUD、`getEffectivePermission` / `getMyPermission`、`refreshFacts` |
| 组织检索 | 用户/组织/角色/部门角色关键词查询（供表单与节点选人） |

## 脚本与自动节点

工作流接入 geelato-script（试验场定位）：

- `POST /api/script/execute` / `parse`，`GET /api/script/kitCatalog`、`/versions`；
- 内置 kit：`WfKit`、`WfDirectKit`（流程领域操作）；
- **自动节点**：`AutoNodeDelegate` + 执行器（`GeelatoScriptExecutor` 跑 geelato-script、`HttpNodeExecutor` 调外部 HTTP），流程走到自动节点时自动执行；
- 脚本数据源 `geelato.workflow.script.sql.connect-id=workflowDataSource`，超时 `geelato.workflow.script.timeout-ms=30000`。

## 数据表

Activiti 自建 `ACT_*` 表 + 扩展表（DDL 见仓库 `doc/sql/`）：`ext_re_procdef`（定义版本扩展）、`ext_hi_action`（操作历史）、`ext_to_read(_recipient)`（传阅）、`ext_task_attach`、`ext_opinion_phrase`（常用语）、`ext_wf_policy(_binding/_item)` 与 `ext_wf_auth_fact`（权限策略）、`ext_wf_define_lock`、`ext_notice_send_log`、`wf_user`；平台库侧桥接视图 `v_platform_wf_my_task`、`v_platform_wf_my_completed_task`。

## 当前状态

引擎与 REST 全量可用（含脚本试验场与自动节点）；前端（Vue，仓库 `frontend/`）与设计器联动开发中；文档层面[业务系统接入](integration.md)与 [REST API 清单](api.md)已备。
