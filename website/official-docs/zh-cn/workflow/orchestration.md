---
title: 流程编排
sidebar_label: 流程编排
---

# 流程编排

本页说明 geelato-workflow 流程设计的核心模型：**流程节点**、**迁移线**、**参与者**、**流程属性**与**流程节点属性**。设计器产出 JSON 格式的流程定义，保存时由工作流服务转换为标准 BPMN 2.0 并部署到引擎；集成方只需关注本页描述的业务模型与字段，无需接触 BPMN 细节。

定义的保存、发布、版本管理等接口见[流程定义集成](integration-define.md)；流程运转行为见[流程运转](runtime.md)。

## 定义整体结构

一个流程定义由三部分组成：

| 组成 | 说明 |
| --- | --- |
| 流程节点（`workflowNodes`） | 节点列表，必须包含启动节点与结束节点 |
| 迁移线（`sequenceFlows`） | 节点之间的连线，至少一条，决定流程走向 |
| 流程属性 | 定义标识、名称、分组、表单页、版本状态等定义级信息 |

```json
{
  "defineKey": "leave_apply",
  "name": "请假申请",
  "appId": "1001",
  "defineGroup": "人事流程",
  "pageId": "leave_form_page",
  "workflowNodes": [
    { "id": "start_1", "name": "启动", "nodeType": "start", "properties": [] },
    { "id": "initiate_1", "name": "申请", "nodeType": "initiate", "properties": [] },
    { "id": "audit_1", "name": "主管审批", "nodeType": "audit", "properties": [
        { "name": "assignee", "value": [{ "assignType": "role", "assignObject": "dept_manager" }] },
        { "name": "auditType", "value": "any" }
      ] },
    { "id": "end_1", "name": "结束", "nodeType": "end", "properties": [] }
  ],
  "sequenceFlows": [
    { "id": "flow_1", "sourceRef": "start_1", "targetRef": "initiate_1", "isAgree": 1 },
    { "id": "flow_2", "name": "同意", "sourceRef": "initiate_1", "targetRef": "audit_1", "isAgree": 1 },
    { "id": "flow_3", "name": "通过", "sourceRef": "audit_1", "targetRef": "end_1", "isAgree": 1 }
  ]
}
```

## 流程节点

节点是流程中的处理环节，每个节点包含以下字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 节点标识，定义内全局唯一，迁移线通过它引用节点 |
| `name` | string | 节点名称（2-50 字符） |
| `nodeType` | string | 节点类型，见下表 |
| `properties` | array | 节点属性列表（`{name, value}` 对），见[流程节点属性](#流程节点属性) |
| `x` / `y` / `width` / `height` | number | 画布布局信息（设计器持久化用，可为空） |

### 节点类型

| 类型标识 | 名称 | 行为说明 |
| --- | --- | --- |
| `start` | 启动节点 | 虚拟节点，流程入口，每个定义必须有且仅有一个 |
| `end` | 结束节点 | 虚拟节点，流程出口，走到该节点流程正常结束 |
| `initiate` | 发起节点 | 流程发起后首先到达的节点，自动绑定发起人为处理人，产生拟稿待办 |
| `audit` | 审批节点 | 审批人给出同意/不同意结论的节点，按并行多实例执行，多个审批人按完成规则判定 |
| `handle` | 办理节点 | 承办类用户任务，配置了处理人时按运行时求解结果分配；未配置时由有权限的用户自行办理 |
| `notice` | 通知节点 | 自动执行：按节点配置的接收人发送通知后自动向下流转，不产生待办 |
| `service` | 自动执行节点 | 自动执行脚本或 HTTP 调用后向下流转，不产生待办 |
| `exclusiveGateway` | 排他网关 | 按迁移线条件评估结果选择一条出线 |
| `parallelGateway` | 并行网关 | 分叉时多条出线同时到达，汇聚时等所有分支到达后放行 |
| `hq` | 会签节点 | 多人并行投票的审批节点，按完成规则与拒绝策略判定节点结论 |
| `countersignStart` / `countersignEnd` | 会签开始/结束（存量兼容） | 旧版会签模型，仅存量定义兼容保留，新建流程使用 `hq` |

:::note 自动类节点的失败兜底
通知节点与自动执行节点执行失败时，引擎自动生成一条人工兜底待办（处理人为发起人），处理人可选择**重新执行**（回边重跑失败节点）或**跳过继续**（跳过失败节点流向原下游）。
:::

## 迁移线

迁移线连接两个节点，决定流程的流转路径：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | string | 迁移线标识 |
| `name` | string | 迁移线名称（如"同意"/"不同意"，展示于下一步选择与流转路径文案） |
| `sourceRef` | string | 来源节点 id |
| `targetRef` | string | 目标节点 id |
| `conditionExpression` | string | 条件表达式，基于表单数据/流程变量评估；排他网关出线必须配置条件 |
| `isAgree` | int | 是否为同意类型迁移线：`1` 同意线、`0` 不同意线 |

迁移线的路由规则：

- **同意线/不同意线**：审批、会签节点提交时按本次结论（`isAgree`）筛选出线，同意走 `isAgree=1` 的线，不同意走 `isAgree=0` 的线。
- **条件线**：`conditionExpression` 基于流程变量（含表单数据）评估，典型用法是排他网关的多条出线按业务数据择一。
- **默认流转**：无条件或条件为真的迁移线参与路由；排他网关所有条件都不满足时流程报错，编排时应保证条件完备。
- **并行流转**：并行网关分叉后的多条出线同时生效；汇聚时等待全部分支到达。

## 参与者

审批、办理、会签等用户任务节点通过 `assignee` 属性配置参与者，支持多条配置叠加。

### 处理人配置

| 字段 | 说明 |
| --- | --- |
| `assignType` | 处理人类型：`user` 指定用户、`role` 指定角色、`dept` 指定部门、`dept_role` 部门角色（格式 `orgId:roleId`）、`initiator` 发起人 |
| `assignObject` | 处理人标识，多个值用 `,` 分隔（如多个用户 id 或多个角色 id） |
| `withChild` | 是否包含子部门，仅 `assignType=dept` 时生效 |

各类型的运行期语义：

| assignType | assignObject 含义 | 解析结果 |
| --- | --- | --- |
| `user` | 用户 id 列表 | 直接取指定用户 |
| `role` | 角色 id 列表 | 该角色下全部用户 |
| `dept` | 部门 id 列表 | 部门下全部用户；`withChild=true` 时含子部门用户 |
| `dept_role` | `部门id:角色id` 列表 | 该部门下拥有该角色的用户 |
| `initiator` | 无需配置 | 流程发起人（自动类节点失败兜底处理人、通知接收人使用） |

### 参与者求解时机

- **提交时选人**：提交任务前通过[下一步与参与者求解接口](integration-runtime.md#下一步与参与者求解)获取候选处理人（`StepUser`），由提交人确认后随提交参数传入；目标节点是否必须选人由 `needAssignee` 标记决定。
- **兜底解析**：提交时对未显式选人的审批/会签节点，引擎按节点 `assignee` 配置自动解析参与者；解析结果为空时按节点 `emptyAssigneeStrategy` 处理（见下文[空参与者策略](#空参与者策略)）。

### 人数限制

`assigneeLimit` 限制节点参与人数：`0` 表示无限制，正整数 `N` 表示最多选择 `N` 人。求解出的候选人数超过上限时，提交前须由提交人裁剪。

### 空参与者策略

`emptyAssigneeStrategy` 决定目标节点解析不到任何参与者时的行为：

| 取值 | 行为 |
| --- | --- |
| `initiator` | **默认值**。以流程发起人作为该节点处理人，避免流程卡死 |
| `skip` | 以空参与者集合到达该节点，节点直接完成并向下流转 |
| `error` | 提交失败并报错，提示节点未配置审批人 |

## 流程属性

流程属性是定义级信息，随定义 JSON 一并保存：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `defineKey` | 更新时必填 | 定义唯一标识（首次创建留空由系统生成），同一 key 下按版本演进 |
| `name` | 是 | 流程名称 |
| `remark` | 否 | 备注 |
| `appId` | 是 | 所属应用 id，用于按应用过滤定义与任务 |
| `defineGroup` | 否 | 流程分组（列表页分组筛选） |
| `pageId` | 否 | 关联表单页面 id，发起/办理页据此加载业务表单（iframe） |
| `source` | 否 | 设计器布局原始数据（前端维护） |
| `lockVersion` | 否 | 乐观锁基线，保存时校验定义是否已被他人修改 |

### 版本与状态

同一 `defineKey` 下的定义按版本演进，版本状态：

| 状态 | 说明 |
| --- | --- |
| `design` | 草稿：可编辑，尚未部署到引擎 |
| `debug` | 调试：调试发布产生，仅调试发起可运行，供发布前验证 |
| `activated` | 当前版本：已发布，`startWorkflow` 等正式发起接口按此版本运行 |
| `history` | 历史版本：被新发布版本替代，仅运行中的存量实例继续按原版本执行 |

发布动作（草稿发布、调试发布、批量部署）与编辑锁的接口说明见[流程定义集成](integration-define.md)。

## 流程节点属性

节点属性以 `{name, value}` 对挂载在节点的 `properties` 数组中，按 `name` 区分。全集如下：

### 基础属性

| 属性 name | 适用节点 | value 结构与取值 | 默认 |
| --- | --- | --- | --- |
| `auditType` | audit / hq | `any` 任意一人通过即通过；`all` 全部通过才通过。`deptStep` 部门逐级、`roleStep` 角色逐级为预留值，当前版本部署校验直接拒绝 | `all` |
| `assignee` | audit / handle / hq / notice | 处理人配置数组，结构见[参与者](#参与者) | 未配置时 handle 节点由有权限用户自行办理 |
| `assigneeLimit` | audit / handle / hq | `0` 无限制；正整数 N 限制 N 人 | `0` |
| `page` | 全部用户任务节点 | 节点级表单页面 id，覆盖定义级 `pageId` | 取定义级 `pageId` |
| `batchSubmit` | audit / handle | 批量提交配置（多任务合并处理） | — |

### 表单交互属性

| 属性 name | 适用节点 | value 结构与取值 | 默认 |
| --- | --- | --- | --- |
| `button` | 全部用户任务节点 | `{按钮标识: {enabled, text, save}}`。`enabled` 0 隐藏/1 展示；`text` 按钮文案；`save` 表单保存策略 0 不保存/1 保存/2 提示确认 | 见[按钮清单](#按钮清单) |
| `opinion` | audit / handle / hq | `{enabled, required, signature}`：意见框显隐、必填、手写签名开关，均 0/1 | enabled=1，其余 0 |
| `attachment` | 全部用户任务节点 | `{upload, type, download, delete}`：上传权限 0/1、允许类型（逗号分隔）、下载权限 0/1、删除权限 0 禁止/1 删本人/2 删所有 | — |
| `ccEnabled` | audit / handle / hq | 抄送开关 0/1 | `0` |
| `ccAssignee` | ccEnabled=1 时 | 抄送人配置数组，结构与处理人配置相同 | — |

### 会签规则属性（audit / hq）

会签节点按并行多实例执行，多人同时持有待办，节点结论由完成规则与拒绝策略判定：

| 属性 name | value 取值 | 默认 |
| --- | --- | --- |
| `completionRule` | `all` 全部同意；`any` 任一同意；`ratio` 按比例（配合 `completionValue` 为百分比分子，正整数）；`count` 按票数（配合 `completionValue` 为票数阈值，正整数） | `all` |
| `completionValue` | `ratio` 的分子（如 60 表示 60%）或 `count` 的票数阈值 | — |
| `rejectStrategy` | `veto_any` 一票否决即回退；`vote_all` 全部投完再按结论判定 | `veto_any` |
| `emptyAssigneeStrategy` | `initiator` / `skip` / `error`，见[空参与者策略](#空参与者策略) | `initiator` |

:::note 审批方式与会签完成规则的关系
`auditType=any/all` 是审批方式的简化表达，内部映射为完成规则 `any/all`；`completionRule` 显式配置时优先于 `auditType`。需要比例、票数等精细控制时使用 `completionRule + completionValue`。
:::

### 分组会签属性（hq）

分组会签将参与者划分为多个小组，每个小组作为一个意见主体参与会签：

| 属性 name | value 取值 | 默认 |
| --- | --- | --- |
| `groupSource` | `dept` 按部门分组（含子部门）；`role` 按角色分组；`custom` 自定义分组（配合 `groups`）；**空 = 不分组**，按普通会签处理 | 空 |
| `groupInnerRule` | `all` 组内全部同意该组才算同意；`any` 组内任一人同意即该组同意 | `all` |
| `countingUnit` | `member` 按人计数；`group` 按组计数（完成规则以组结论为计数单位） | `member` |
| `groupKeyName` | 组名展示模板（如 `{dept}会签`） | — |
| `groups` | `groupSource=custom` 时的显式分组：`[{key, name, members: [用户id]}]` | — |

组内无人的分组自动跳过（标记 `skipped`）并从组间计数中剔除；全部分组为空时回落到 `emptyAssigneeStrategy` 处理。会签进度（含分组进度）通过实例详情与会签进度接口查询，结构见[流程运行集成](integration-runtime.md#公共出参结构)。

### 自动类节点属性（notice / service）

| 属性 name | 说明 |
| --- | --- |
| `noticeConfig` | 节点通知配置：通知模板、接收人等；引擎在节点到达/完成时发送通知 |
| `async` | 自动节点是否异步执行：`true` 异步（需启用异步执行器）；默认同步执行 |

## 按钮清单

`GET /api/define/getButtonMeta` 返回系统支持的按钮元数据，设计器据此渲染可配置按钮，运行时按节点类型过滤。`提交`按钮不参与节点配置（由运行态按节点是否需要审批结论自动注入）。

| 按钮标识 | 文案 | 默认保存表单 | 默认展示的节点类型 | 层级 |
| --- | --- | --- | --- | --- |
| `wf_btn_agree` | 同意 | 保存 | audit / hq | primary |
| `wf_btn_deny` | 不同意 | 不保存 | audit / hq | primary |
| `wf_btn_back_previous` | 退回上一步 | 不保存 | audit / handle | secondary |
| `wf_btn_back_origin` | 退回发起人 | 不保存 | audit / handle | secondary |
| `wf_btn_back_any` | 退回任意节点 | 不保存 | audit / handle | secondary |
| `wf_btn_stage` | 暂存 | 保存 | initiate / audit / handle / hq | special |
| `wf_btn_addsign` | 加签 | 不保存 | audit / hq | secondary |
| `wf_btn_end` | 结束流程 | 不保存 | initiate | secondary |
| `wf_btn_delete` | 删除流程 | 不保存 | initiate | secondary |
| `wf_btn_print` | 打印 | 不保存 | 全部用户任务节点 | special |
| `wf_btn_circulate` | 传阅 | 不保存 | audit / handle / hq / notice / end | special |
| `wf_btn_attach` | 附件上传 | 不保存 | 全部用户任务节点 | secondary |

层级说明：`primary` 一级按钮（操作区主操作）、`special` 特殊按钮（表单/流程信息区右上角）、`secondary` 二级按钮（更多操作）。

:::note 撤回与催办
撤回、催办是**页面级**按钮，不纳入节点按钮配置：由详情页按当前用户与实例的关系（是否发起人、是否有待办）自动显隐。
:::

退回类按钮（退回上一步/退回发起人/退回任意节点）不开放给会签节点——会签节点只能向下流转，不支持退回。

## 相关页面

- [流程运转](runtime.md)：发起、求解、办理、结束的全生命周期行为
- [流程定义集成](integration-define.md)：定义保存、版本、发布、编辑锁接口
- [流程运行集成](integration-runtime.md)：发起、提交、办理、撤回驳回等接口
