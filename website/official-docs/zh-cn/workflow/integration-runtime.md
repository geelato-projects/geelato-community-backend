---
title: 流程运行集成
sidebar_label: 流程运行集成
---

# 流程运行集成

本页逐一说明流程运行期的全部接口：流程发起、下一步与参与者求解、任务提交与办理、实例与任务查询、实例管理与催办。接口对应的行为语义（何时调用、求解过程）见[流程运转](runtime.md)。

## 通用约定

- 所有接口统一 `/api` 前缀，响应体 `{code, status, msg, data}`（见[流程定义集成 · 通用约定](integration-define.md#通用约定)）。
- 分页响应 `data` 统一为 `{page, pageSize, total, data[]}`。
- 关键业务码：`400` 任务不存在/实例不存在/参数错误；`403` 无权限；`409` 任务已被处理，请刷新后重试（并发提交）；`4031` 无权查看该流程实例（数据权限不足，前端统一拦截提示）。

### 实例状态与操作类型

实例状态（`status`）：

| 取值 | 说明 |
| --- | --- |
| `running` | 进行中 |
| `completed` | 已完成（正常走到结束节点） |
| `terminated` | 已终止（强制结束） |

操作记录类型（轨迹 `actionType`）：

| 取值 | 说明 |
| --- | --- |
| `INITIATE` | 启动 |
| `SUBMIT` | 提交 |
| `WITHDRAW` | 撤回 |
| `TRANSFER` | 转办 |
| `TERMINATE` / `FORCE_END` | 终止 / 强制终止 |
| `DELETE` | 删除 |
| `CIRCULATE` | 传阅 |
| `REMIND` | 催办 |
| `CANCEL` | 会签否决后取消同节点其余待办 |

## 公共出参结构

### NextStep

下一步/提交选路的核心结构，出现在 `queryNextStep*` 的返回与 `runWorkflow` 的入参中：

| 字段 | 说明 |
| --- | --- |
| `stepName` | 步骤名称（如"提交"） |
| `stepTargetName` | 目标节点名称 |
| `targetRef` | 目标节点 id，提交时原样回传 |
| `stepUserList` | 候选处理人列表（[StepUser](#stepuser)），提交时原样回传（可裁剪到 `assigneeLimit` 内） |
| `isAgree` | 该步骤对应迁移线是否为同意线：`1` 同意、`0` 不同意 |
| `assigneeLimit` | 目标节点选人数量限制（`0` 无限制） |
| `subStepList` | 并行子步骤列表（并行分叉时每个分支一项，结构同本结构） |
| `routeTargetRefs` | 路由目标节点 id 链（出线直达网关/自动节点透传时含途经节点），提交时原样回传 |
| `pathText` | 后续路径文案（取连线名，缺省时按 `当前节点->后续节点...` 拼接） |
| `needAssignee` | 该步骤是否需要选择处理人：目标为用户任务时 `true`；目标为结束节点/并行汇聚时 `false`（可直接提交） |

### StepUser

处理人条目：

| 字段 | 说明 |
| --- | --- |
| `userId` | 用户 id |
| `userName` | 用户姓名 |
| `enName` | 英文名 |
| `orgId` / `orgName` | 所属部门 id / 名称 |

### WorkflowInstance

流程实例详情（发起接口与实例详情接口的返回）：

| 字段 | 说明 |
| --- | --- |
| `procId` | 流程实例 id |
| `procName` | 实例名称（`【定义名】业务名`） |
| `defineKey` | 定义唯一标识 |
| `bizId` / `bizName` | 业务 id / 业务名称 |
| `status` | 实例状态：`running` / `completed` / `terminated` |
| `startUserId` / `startUserName` | 发起人 id / 姓名 |
| `createTime` / `finishTime` | 发起 / 结束时间（`yyyy-MM-dd HH:mm:ss`） |
| `currentWorkflowTasks` | 当前活跃任务列表（[WorkflowTask](#workflowtask)） |
| `formPageUrl` | 表单页地址（供非待办查看场景） |
| `withdrawable` | 当前用户是否可撤回 |
| `withdrawTaskId` | 可撤回任务 id，撤回动作以此为准 |
| `remindable` | 当前用户是否可催办（发起人 + 运行中 + 有活跃任务 + 超过催办间隔） |
| `hasMyPendingTask` | 当前用户在本实例是否存在待办 |
| `relations` | 当前用户与实例的关系：`initiator` / `pending` / `completed` / `toRead` / `read` |
| `canHandle` | 当前用户是否可办理（存在本人活跃待办） |
| `readonly` | 对当前用户是否只读（与 `canHandle` 互补） |
| `currentTaskId` | 当前用户本人的待办任务 id |
| `currentNode` | 当前节点名称 |
| `currentHandlers` | 当前活跃任务处理人列表（[WorkflowTask](#workflowtask)） |
| `countersignProgress` | 会签节点进度统计（[CountersignProgressVO](#countersignprogressvo)），无会签时为空 |

### WorkflowTask

任务/处理人条目：

| 字段 | 说明 |
| --- | --- |
| `procId` | 流程实例 id |
| `taskId` | 任务 id |
| `taskName` | 任务名称 |
| `userId` / `userName` | 处理人 id / 姓名 |
| `orgId` / `orgName` | 处理人部门 id / 名称 |
| `fromTask` | 来源任务（嵌套，供追溯） |

### TaskInfoVo

任务详情（`getTaskInfo` / `getCurrentTask` 的返回）：

| 字段 | 说明 |
| --- | --- |
| `id` | 任务 id |
| `name` | 任务名称 |
| `procId` | 流程实例 id |
| `procDefId` | 流程定义 id |
| `assignee` | 处理人 id |
| `nodeInfo` | 所在节点定义（[流程节点](orchestration.md#流程节点)结构，含节点属性，供渲染按钮/意见框/附件区） |
| `status` | 任务状态 |
| `readOnly` | 是否只读（待阅场景） |
| `bizId` | 业务数据 id（businessKey） |
| `formPageUrl` | 表单页地址（节点 `page` 属性优先，其次定义 `pageId`，最后默认表单地址配置） |
| `needNextStep` | 提交本任务是否需要选择下一步与处理人：会签节点仅最后一个处理人且会触发节点完成时为 `true` |
| `countersignProgress` | 会签进度（非会签任务为空） |

### CountersignProgressVO

会签节点进度（实例详情的 `countersignProgress[]` 与任务详情的 `countersignProgress`）：

| 字段 | 说明 |
| --- | --- |
| `nodeId` / `nodeName` | 会签节点 id / 名称 |
| `total` / `completed` / `approved` / `rejected` / `pending` | 应投总数 / 已完成 / 同意 / 否决 / 待投票人数 |
| `nodeStatus` | 节点状态：`running` 进行中 / `completed` 已完成 |
| `arriveTime` / `finishTime` | 节点到达 / 完成时间 |
| `items[]` | 每人处理明细：`{userId, userName, dept, status(pending/approved/rejected/not_arrived), arriveTime, comment, endTime}` |
| `groupSource` / `groupInnerRule` / `countingUnit` | 分组会签配置回显（无分组时为空） |
| `groups[]` | 分组进度：`{key, name, source, result(pending/agreed/rejected/skipped), total, agree, reject, skipped, members[]}`，`members` 结构同 `items[]` |

### ActivityTraceVO

流程轨迹条目（`getProcessTrace` 的返回）：

| 字段 | 说明 |
| --- | --- |
| `nodeId` / `nodeName` | 节点 id / 名称 |
| `activityType` | 节点类型（见[节点类型](orchestration.md#节点类型)） |
| `startTime` / `endTime` / `duration` | 开始 / 结束时间 / 持续时长 |
| `status` | 节点处理状态 |
| `userId` / `userName` | 处理人 id / 姓名 |
| `comment` | 处理意见 |
| `actionType` | 操作类型，见[操作类型](#实例状态与操作类型) |
| `attachments[]` | 本次操作关联的附件：`{id, name, size, kind(file/image), contentType, fileUrl, uploader, uploaderName, uploadTime, taskId, procInstId, nodeId, actionId}` |
| `countersignProgress[]` | 会签节点的进度信息（非会签节点为空） |

### ProcInstListVo

实例列表行（实例分页查询与"我发起的"共用）：

| 字段 | 说明 |
| --- | --- |
| `procInstId` | 流程实例 id |
| `procInstName` | 实例名称（`【定义名】业务名`） |
| `procDefName` | 流程定义名称 |
| `formRecordId` | 业务记录 id（发起时的 businessKey） |
| `startUserId` / `startUserName` | 发起人 id / 姓名 |
| `currentNode` | 当前节点名称（已结束实例为空） |
| `currentHandlers[]` | 当前处理人姓名列表（已结束实例为空） |
| `status` | 实例状态：`running` / `completed` / `terminated` |
| `startTime` / `finishTime` | 发起 / 结束时间（已结束实例 `finishTime` 为空） |

## 流程发起

### 启动流程

#### `POST /api/engine/startWorkflow`

按已激活版本启动流程实例，流程停留在发起节点并为发起人生成拟稿待办。

**入参**（JSON body）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defineKey` | string | 是 | 流程定义 key |
| `bizId` | string | 是 | 业务 id（写入实例 businessKey，供业务系统反查） |
| `bizName` | string | 是 | 业务名称（实例名称为 `【定义名】业务名`） |
| `formData` | object | 否 | 业务表单数据，写入流程变量，供迁移线条件评估与表单回显 |
| `attachIds` | string[] | 否 | 发起时上传的附件 id 清单，与发起操作记录建立关联 |

**出参**（`data`）：[WorkflowInstance](#workflowinstance)，`currentWorkflowTasks` 为发起节点待办。

**请求示例**：

```json
{
  "defineKey": "leave_apply",
  "bizId": "LV202611150001",
  "bizName": "张三的请假申请",
  "formData": { "days": 3, "reason": "年假" }
}
```

**响应示例**：

```json
{
  "code": 200, "status": "success", "msg": "success",
  "data": {
    "procId": "5001", "procName": "【请假申请】张三的请假申请",
    "defineKey": "leave_apply", "bizId": "LV202611150001", "bizName": "张三的请假申请",
    "status": "running", "startUserId": "u_1001", "startUserName": "张三",
    "createTime": "2026-11-15 10:00:00", "finishTime": null,
    "currentWorkflowTasks": [
      { "procId": "5001", "taskId": "6001", "taskName": "申请",
        "userId": "u_1001", "userName": "张三", "fromTask": null }
    ]
  }
}
```

**错误**：`400` 定义不存在或未发布（无激活版本）。

### 发起即提交

#### `POST /api/engine/startWorkflowAtomicity`

启动流程并在同一调用中提交发起节点，直接流转到下一节点。

**入参**（JSON body）：在[启动流程](#启动流程)入参基础上追加：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `selectStepList` | array | 是 | 下一步选择（[NextStep](#nextstep) 数组），来自 `queryNextStepPrediction` / `queryNextStep` |
| `comment` | string | 否 | 发起节点审批意见，随发起提交写入操作记录 |

**出参**（`data`）：[WorkflowInstance](#workflowinstance)，`currentWorkflowTasks` 为流转后的当前任务。

### 启动调试流程

#### `POST /api/engine/startDebugWorkflow`

按调试（`debug`）版本启动流程，行为与[启动流程](#启动流程)一致，入参、出参相同。定义无调试版本时返回 `400`。

### 调试发起即提交

#### `POST /api/engine/startDebugWorkflowAtomicity`

按调试版本启动并提交，行为与[发起即提交](#发起即提交)一致。

## 下一步与参与者求解

### 查询下一步

#### `GET|POST /api/engine/queryNextStep`

对运行中实例的当前任务求解下一步可到达的节点与候选处理人。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `procId` | string | 是 | 流程实例 id |
| `taskId` | string | 是 | 流程任务 id |
| `isAgree` | int | 否 | 按结论筛选出线：`1` 仅同意线、`0` 仅不同意线；不传返回全部 |

**出参**（`data[]`）：[NextStep](#nextstep) 列表。目标为并行分叉时，每条分支以 `targetRef + subStepList` 形式返回。

**错误**：`400` process instance not found / task not found。

### 发起前预测下一步

#### `GET|POST /api/engine/queryNextStepPrediction`

流程尚未发起时，按定义预测发起提交后的首站节点。

**入参**（query + body）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defineKey` | string | 是 | 流程定义 key |
| `debug` | boolean | 否 | 是否按调试版本预测，默认 `false` |
| `formData` | object | 否 | 拟稿表单数据（JSON body），用于条件迁移线评估，可空 |

**出参**（`data[]`）：[NextStep](#nextstep) 列表。

### 查询下一步用户

#### `GET|POST /api/engine/queryStepUser`

对运行中实例求解指定目标节点的候选处理人。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `procId` | string | 是 | 流程实例 id |
| `targetRef` | string | 是 | 目标节点 id |

**出参**（`data[]`）：[StepUser](#stepuser) 列表。

### 发起前预测处理人

#### `GET|POST /api/engine/queryStepUserPrediction`

流程尚未发起时，按定义预测指定节点的候选处理人。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defineKey` | string | 是 | 流程定义 key |
| `targetRef` | string | 是 | 目标节点 id |
| `debug` | boolean | 否 | 是否按调试版本解析，默认 `false` |

**出参**（`data[]`）：[StepUser](#stepuser) 列表。

### 获取拟稿表单页地址

#### `GET|POST /api/engine/getDefineFormPageUrl`

获取定义绑定的表单页地址模板，供发起前以 iframe 加载业务表单。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defineKey` | string | 是 | 流程定义 key |
| `debug` | boolean | 否 | 是否按调试版本解析，默认 `false` |

**出参**（`data`）：表单页地址字符串（按定义 `pageId` 与默认表单地址配置计算，模板形如 `http://host/{pageId}?bizId={bizId}`）。

## 任务提交与办理

### 提交任务

#### `POST /api/engine/runWorkflow`

提交当前任务并驱动流程流转，是审批/办理/发起提交的统一入口。

**入参**（JSON body）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `procId` | string | 是 | 流程实例 id |
| `taskId` | string | 是 | 当前任务 id（须为当前用户的待办） |
| `selectStepList` | array | 是 | 下一步选择（[NextStep](#nextstep) 数组），`targetRef`、`stepUserList`、`routeTargetRefs`、`subStepList` 由求解接口取得后原样回传；目标无需选人时 `stepUserList` 可空 |
| `comment` | string | 否 | 审批意见，写入操作记录 |
| `attachIds` | string[] | 否 | 本次提交关联的附件 id 清单 |

**出参**（`data`）：`"The Workflow Operate Success!"`。

**请求示例**：

```json
{
  "procId": "5001",
  "taskId": "6001",
  "comment": "同意，请按规定执行",
  "selectStepList": [
    {
      "stepName": "提交",
      "stepTargetName": "主管审批",
      "targetRef": "audit_1",
      "isAgree": 1,
      "assigneeLimit": 1,
      "needAssignee": true,
      "stepUserList": [
        { "userId": "u_2001", "userName": "李四", "orgId": "o_10", "orgName": "技术部" }
      ]
    }
  ]
}
```

**业务规则**：

- 任务必须属于当前用户且未挂起，否则 `400`/`403`。
- 会签节点：任一人投不同意（`selectStepList` 含 `isAgree=0`）时，同节点其余待办自动取消，按不同意线流转。
- 并发提交同一任务时引擎在事务内重试，超过次数返回 `409 任务已被处理，请刷新后重试`。
- 目标节点未显式选人时，引擎按节点 `assignee` 配置解析参与者；解析为空时按节点 `emptyAssigneeStrategy` 兜底（见[流程编排 · 空参与者策略](orchestration.md#空参与者策略)）。

### 撤回

#### `POST /api/engine/withdraw`

发起人撤回已送出的流程，流程回到发起人待办。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `procId` | string | 是 | 流程实例 id |
| `taskId` | string | 是 | 要撤回的任务 id（取实例详情的 `withdrawTaskId`） |
| `comment` | string | 是 | 撤回原因，写入操作记录 |

**出参**（`data`）：处理结果描述字符串。

**业务规则**：仅可撤回"其后无其他节点处理记录"的已办任务；实例详情的 `withdrawable` 为可撤回判定依据。

### 驳回上一步

#### `POST /api/engine/rejectToLastStep`

将流程退回上一处理节点。

**入参**（query）：`procId`（string，必填）、`taskId`（string，必填，当前任务 id）、`comment`（string，必填，驳回意见）。

**出参**（`data`）：处理结果描述字符串。

### 驳回发起人

#### `POST /api/engine/rejectToStart`

将流程退回到发起节点，发起人重新编辑后再次提交。

**入参**（query）：同[驳回上一步](#驳回上一步)（`procId`、`taskId`、`comment`）。

**出参**（`data`）：处理结果描述字符串。

### 驳回指定节点

#### `POST /api/engine/rejectToNode`

将流程退回到流程中任意指定节点。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `procId` | string | 是 | 流程实例 id |
| `taskId` | string | 是 | 当前任务 id |
| `targetNodeId` | string | 是 | 退回目标节点 id |
| `comment` | string | 是 | 驳回意见 |

**出参**（`data`）：处理结果描述字符串。

### 转办

#### `POST /api/engine/transferTask`

将本人待办转给他人办理。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `taskId` | string | 是 | 任务 id |
| `userId` | string | 是 | 目标用户 id |
| `comment` | string | 否 | 转办意见 |

**出参**（`data`）：处理结果描述字符串。

## 实例与任务查询

### 查询实例当前任务

#### `GET /api/engine/getCurrentTask`

查询实例当前全部活跃任务。

**入参**（query）：`procId`（string，必填）。

**出参**（`data[]`）：[TaskInfoVo](#taskinfovo) 列表。

### 查询实例详情

#### `GET /api/engine/getProcInstance`

查询实例详情。实例仍在运行时按运行数据返回；实例已结束（运行态不存在）时以历史实例构造返回，详情页仍可展示。

**入参**（query）：`procId`（string，必填）。

**出参**（`data`）：[WorkflowInstance](#workflowinstance)。

**错误**：`4031` 无权查看该流程实例（数据权限不足）。

### 查询实例任务列表

#### `GET /api/engine/getProcTask`

查询实例的全部任务（含处理人信息）。

**入参**（query）：`procId`（string，必填）。

**出参**（`data[]`）：[WorkflowTask](#workflowtask) 列表。

### 查询任务详情

#### `GET /api/engine/getTaskInfo`

按任务 id 查询任务详情（含节点定义、表单页地址与提交提示）。

**入参**（query）：`taskId`（string，必填）。

**出参**（`data`）：[TaskInfoVo](#taskinfovo)。

### 分页查询流程实例

#### `GET /api/engine/pageQueryProcInst`

分页查询流程实例列表。数据权限：管理员全量可见；非管理员仅返回与我相关的实例（我发起 / 我待办 / 我已办 / 我待阅已阅）。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 统一关键字（实例名称/定义名称模糊匹配） |
| `name` | string | 否 | 名称模糊匹配（历史参数，保留兼容） |
| `status` | string | 否 | 实例状态：`running` / `completed` / `terminated` |
| `statusList` | string | 否 | 状态多值（逗号分隔，如 `completed,terminated`），非空时优先于 `status` |
| `appId` | string | 否 | 按定义所属应用过滤 |
| `procDefKeys` | string | 否 | 流程类型（定义 key），多个逗号分隔 |
| `startUserId` | string | 否 | 按发起人过滤 |
| `startTimeFrom` / `startTimeTo` | string | 否 | 发起时间区间（`yyyy-MM-dd HH:mm:ss`，含边界） |
| `finishTimeFrom` / `finishTimeTo` | string | 否 | 结束时间区间 |
| `currentAssigneeId` | string | 否 | 当前处理人 id（进行中实例） |
| `currentTaskDefKey` | string | 否 | 当前节点 id（进行中实例） |
| `sortBy` / `sortOrder` | string | 否 | 排序字段（服务端白名单）/ 方向 `asc` / `desc` |
| `page` / `pageSize` | int | 否 | 分页，默认 `1` / `10` |

**出参**（`data`）：[PageResult](#通用约定)，`data[]` 为 [ProcInstListVo](#procinstlistvo) 列表。

### 查询流程轨迹

#### `GET /api/engine/getProcessTrace`

查询实例的节点级操作记录（流程图时间线数据源）。

**入参**（query）：`procId`（string，必填）。

**出参**（`data[]`）：[ActivityTraceVO](#activitytracevo) 列表。

**错误**：`400` history not exist（实例无历史记录）。

### 获取实例 BPMN

#### `GET /api/engine/getProcBpmn`

获取实例对应定义的 BPMN XML，供只读流程图渲染；流程已结束仍可从历史实例导出。

**入参**（query）：`procId`（string，必填）。

**出参**（`data`）：BPMN XML 字符串。

### 查询实例表单数据

#### `GET /api/engine/getProcFormData`

按业务 id 反查流程变量（表单数据）。

:::warning 测试用途
表单数据的正式来源是业务系统按 `bizId` 自行提供，本接口仅为联调演示保留。
:::

**入参**（query）：`bizId`（string，必填）。

**出参**（`data`）：流程变量键值对象。

## 实例管理

### 强制结束流程

#### `POST /api/engine/forceEndProcInstance`

管理员强制终止运行中的实例，未完成的待办全部终止，实例状态转为 `terminated`。

**入参**（query）：`procId`（string，必填）、`comment`（string，可选，处理意见）。

**出参**（`data`）：处理结果描述字符串。

### 删除流程实例

#### `POST /api/engine/deleteProcInstance`

删除实例及其任务、操作记录，业务数据不受影响。

**入参**（query）：`procId`（string，必填）、`comment`（string，可选，处理意见）。

**出参**（`data`）：处理结果描述字符串。

## 催办

### 获取可催办对象

#### `GET /api/engine/remindTargets`

校验当前用户为发起人后，返回实例当前活跃任务的去重处理人与默认催办文案。

**入参**（query）：`procId`（string，必填）。

**出参**（`data[]`）：

| 字段 | 说明 |
| --- | --- |
| `userId` / `userName` | 处理人 id / 姓名 |
| `defaultContent` | 默认催办文案 |

**错误**：`400` 当前用户不是发起人 / 实例不在运行中。

### 发送催办

#### `POST /api/engine/remind`

发起人对指定处理人发送催办提醒。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `procId` | string | 是 | 流程实例 id |
| `userIds` | string | 是 | 催办对象用户 id，多个逗号分隔 |
| `content` | string | 否 | 催办内容，为空时使用默认文案 |

**出参**（`data`）：处理结果描述字符串。

**业务规则**：仅发起人可催办，且距上次催办需超过间隔（实例详情 `remindable` 为显隐依据）。

## 相关页面

- [流程运转](runtime.md)：本页接口对应的行为与全链路时序
- [个人流程集成](integration-my.md)：待办/已办/待阅/已办查询
- [流程定义集成](integration-define.md)：定义保存与发布接口
