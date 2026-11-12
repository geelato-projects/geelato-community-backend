---
title: 工作流 REST API 清单
sidebar_label: REST API 清单
---

# 工作流 REST API 清单

所有端点由 `@WFApiRestController` 声明并统一带 `/api` 前缀（认证控制器除外，见文末），返回统一 `WorkflowResult`。字段级入参以控制器源码为准，本页是导航级清单。

## 流程定义 `/api/define`（DefineController）

| 端点 | 说明 |
| --- | --- |
| `POST /saveDefineVersion` | 保存 JSON 流程定义版本 |
| `GET /pageQueryDefine` | 定义分页查询 |
| `GET /queryDefineVersions`、`GET /getDefineVersion` | 版本列表 / 版本详情 |
| `GET /listDefineGroups`、`GET /listDefineOptions` | 分组与选项（发起页选择器） |
| `GET/POST /createDraftVersion` | 创建草稿版本 |
| `POST /deployDefineVersion`、`POST /deployDebugDefineVersion`、`POST /batchDeployDefine` | 发布 / 调试发布 / 批量部署 |
| `POST /convertToBpmn`、`POST /convertToJson`、`GET /getDefineBpmn` | JSON↔BPMN 互转、取 BPMN |
| `GET /getButtonMeta` | 按钮元数据（节点可用操作） |

**编辑锁 `/api/define/lock`**（DefineLockController）：`POST /acquire`、`POST /renew`、`POST /release`（参数 defineKey、lockToken）

## 引擎运行 `/api/engine`（EngineController）

| 端点 | 说明 |
| --- | --- |
| `POST /startWorkflow`、`/startWorkflowAtomicity` | 发起（及业务+流程原子性版本） |
| `POST /startDebugWorkflow`、`/startDebugWorkflowAtomicity`、`/runWorkflow` | 调试发起 / 重跑 |
| `GET/POST /queryNextStep`、`/queryNextStepPrediction` | 下一步预测 |
| `GET/POST /queryStepUser`、`/queryStepUserPrediction` | 办理人预测 |
| `GET /getDefineFormPageUrl` | 节点表单页地址模板 |
| `GET /getCurrentTask`、`/getProcInstance`、`/pageQueryProcInst`、`/getProcFormData`、`/getProcTask`、`/getTaskInfo`、`/getProcessTrace`、`/getProcBpmn` | 实例/任务/轨迹/表单/图查询 |
| `POST /withdraw` | 撤回 |
| `POST /rejectToLastStep`、`/rejectToStart`、`/rejectToNode` | 驳回（上一步/发起节点/指定节点） |
| `POST /transferTask` | 转办 |
| `POST /forceEndProcInstance`、`/deleteProcInstance` | 强制终止 / 删除实例 |
| `GET /remindTargets`、`POST /remind` | 催办目标与催办 |

## 我的任务 `/api/my`（MyController）

| 端点 | 说明 |
| --- | --- |
| `GET /getMyPendingTask`、`/pageQueryMyPendingTask` | 待办（详情/分页） |
| `GET /getMyCompleteTask`、`/pageQueryMyCompleteTask` | 已办 |
| `GET /getMyToRead`、`/pageQueryMyToRead` | 待阅 |
| `GET /getMyCompleteRead`、`/pageQueryMyCompleteRead` | 已阅 |
| `GET /getMyStart` | 我发起的 |
| `GET /getOpinionPhraseList`、`POST /saveOpinionPhrase`、`/updateOpinionPhrase`、`/deleteOpinionPhrase` | 常用语 CRUD |

## 传阅 `/api/toread`（ToReadController）

`POST /sendToRead`（发传阅）、`POST /completeRead`、`POST /batchCompleteRead`（办结/批量办结传阅）

## 附件

| 端点 | 说明 |
| --- | --- |
| `POST /api/attach/upload` | 上传（multipart） |
| `GET /api/attach/download?id=` | 下载（类级 `/api/attach`） |

## 流程权限 `/api/wfpolicy`（WfPolicyController）

| 端点 | 说明 |
| --- | --- |
| `GET /listPolicy`、`POST /savePolicy`、`POST /deletePolicy` | 策略 CRUD |
| `GET /listBinding`、`POST /saveBinding`、`POST /deleteBinding` | 策略绑定 |
| `POST /refreshFacts` | 刷新权限事实 |
| `GET /getEffectivePermission`、`/getMyPermission` | 生效权限 / 我的权限 |

## 通知 `/api/notice`（NoticeController）

`POST /send`（发送通知）、`POST /sendLogs`（发送日志查询）；通道 SPI：Email / PlatformNotice / Webhook / External。

## 脚本 `/api/script`（WorkflowScriptController）

| 端点 | 说明 |
| --- | --- |
| `POST /execute`、`POST /parse` | 执行 / 解析脚本 |
| `GET /kitCatalog`、`GET /versions` | 可用 kit 目录 / 引擎版本 |

## 组织检索 `/api/org`（OrgController）

`GET /queryUserByKeyword`、`/queryOrgByKeyword`、`/queryRoleByKeyword`、`/queryDeptRoleByKeyword`

## 认证（非 WFApiRestController）

| 端点 | 说明 |
| --- | --- |
| `POST /api/auth/login` | 本地登录（工作流库 `wf_user` 校验，签发 JWT） |
| `GET /oauth2/callback` | 统一认证中心 OAuth2 回调 |

健康检查：`WorkflowHealthEndPoint`（"工作流模块 HEALTH"）。
