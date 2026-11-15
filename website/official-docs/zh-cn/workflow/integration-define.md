---
title: 流程定义集成
sidebar_label: 流程定义集成
---

# 流程定义集成

本页逐一说明流程定义的保存、查询、版本管理、发布与编辑锁接口。定义的数据模型（节点/迁移线/属性）见[流程编排](orchestration.md)，版本与发布状态语义见[流程编排 · 版本与状态](orchestration.md#版本与状态)。

## 通用约定

所有接口统一 `/api` 前缀，响应体统一为：

```json
{ "code": 200, "status": "success", "msg": "success", "data": "..." }
```

| 字段 | 说明 |
| --- | --- |
| `code` | 业务码：`200` 成功；`400` 参数/数据问题；`403` 无权限；`4090` 编辑锁冲突；`4091` 定义内容冲突（乐观锁） |
| `status` | `success` / `fail` |
| `msg` | 提示信息 |
| `data` | 业务数据，结构见各接口 |

分页响应 `data` 为：

```json
{ "page": 1, "pageSize": 10, "total": 42, "data": [ "..." ] }
```

认证接入（本地登录 / OAuth2 / auth-client 契约）见 [Geelato AuthServer](../authentication/overview.md)。

## 保存定义版本

### `POST /api/define/saveDefineVersion`

保存一份流程定义版本。新建时生成定义 key 与首个版本；更新时仅允许修改草稿（`design`）或调试（`debug`）状态版本。

**入参**（JSON body）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 更新时必填 | 定义版本 id，新建留空由系统生成 |
| `defineKey` | string | 更新时必填 | 定义唯一标识，新建留空由系统生成 |
| `name` | string | 是 | 流程名称 |
| `remark` | string | 否 | 备注 |
| `appId` | string | 是 | 所属应用 id |
| `defineGroup` | string | 否 | 流程分组 |
| `pageId` | string | 否 | 关联表单页面 id |
| `source` | object | 否 | 设计器布局原始数据（原样保存） |
| `workflowNodes` | array | 是 | 节点列表，结构见[流程节点](orchestration.md#流程节点)，必须含 `start` 与 `end` 节点 |
| `sequenceFlows` | array | 是 | 迁移线列表，结构见[迁移线](orchestration.md#迁移线)，至少一条 |
| `lockVersion` | int | 否 | 乐观锁基线：从详情接口取得后回传，与库内不一致时保存失败（`4091`）；不传则不做基线校验 |

**出参**（`data`）：

| 字段 | 说明 |
| --- | --- |
| `id` | 定义版本 id |
| `defineKey` | 定义唯一标识 |
| `version` | 版本号 |
| `status` | 保存后状态（新建/更新均为 `design` 或 `debug`） |
| `lockVersion` | 最新乐观锁版本，下次保存回传 |
| `createAt` / `updateAt` | 创建/更新时间（`yyyy-MM-dd HH:mm:ss`） |

**请求示例**：

```json
{
  "defineKey": "leave_apply",
  "name": "请假申请",
  "appId": "1001",
  "pageId": "leave_form_page",
  "lockVersion": 3,
  "workflowNodes": [
    { "id": "start_1", "name": "启动", "nodeType": "start", "properties": [] },
    { "id": "audit_1", "name": "主管审批", "nodeType": "audit", "properties": [
        { "name": "assignee", "value": [{ "assignType": "role", "assignObject": "dept_manager" }] }
      ] },
    { "id": "end_1", "name": "结束", "nodeType": "end", "properties": [] }
  ],
  "sequenceFlows": [
    { "id": "flow_1", "sourceRef": "start_1", "targetRef": "audit_1", "isAgree": 1 },
    { "id": "flow_2", "sourceRef": "audit_1", "targetRef": "end_1", "isAgree": 1 }
  ]
}
```

**业务规则**：

- 已发布（`activated`）或历史（`history`）版本不可直接修改，需通过[创建草稿版本](#创建草稿版本)生成新草稿后再保存。
- 更新时以定义 key 加行级锁串行化，避免与发布/生成草稿并发交错。
- 需要当前用户具备该定义的 `edit` 动作权限（动作位掩码见[分页查询定义](#分页查询定义)说明）。

**错误**：`400` 数据不存在 / 已发布版本不可直接修改；`4091` 定义已被他人修改，请刷新后重新加载。

## 分页查询定义

### `GET /api/define/pageQueryDefine`

分页查询流程定义列表，每行返回该定义最新版本的概要信息。

**入参**（query 参数）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 统一关键字，按名称与 defineKey 模糊匹配 |
| `name` | string | 否 | 名称模糊匹配（历史参数，保留兼容） |
| `appId` | string | 否 | 按所属应用过滤 |
| `status` | string | 否 | 发布状态：`published` 已发布 / `unpublished` 未发布 |
| `defineGroup` | string | 否 | 分组单选（历史参数） |
| `defineGroups` | string | 否 | 分组多选，逗号分隔，非空时优先于 `defineGroup` |
| `statusList` | string | 否 | 版本状态多选，逗号分隔：`design` / `debug` / `activated` / `history` |
| `updateTimeFrom` / `updateTimeTo` | string | 否 | 更新时间区间（`yyyy-MM-dd HH:mm:ss`，含边界） |
| `lockState` | string | 否 | 编辑锁状态：`locked` 被占用 / `mine` 我编辑中 / `others` 他人编辑中 / `unlocked` 未锁定 |
| `sortBy` | string | 否 | 排序字段（服务端白名单校验） |
| `sortOrder` | string | 否 | `asc` / `desc` |
| `page` / `pageSize` | int | 否 | 分页，默认 `1` / `10` |

**出参**（`data.data[]`，每项为定义列表行）：

| 字段 | 说明 |
| --- | --- |
| `id` | 当前（最新）版本 id |
| `defineKey` | 定义唯一标识 |
| `name` | 流程名称 |
| `version` | 当前版本号 |
| `status` | 当前版本状态：`design` / `debug` / `activated` / `history` |
| `publishStatus` | 发布状态：`published`（存在已发布版本）/ `unpublished` |
| `latestPublishedVersion` | 最新已发布版本号，未发布时为空 |
| `defineGroup` | 流程分组 |
| `updateTime` | 更新时间（`yyyy-MM-dd HH:mm:ss`） |
| `actions` | 当前用户对本定义的权限动作位掩码：`view=1` / `edit=2` / `publish=4` / `grant=8`，按位或叠加（如 7 = 查看+编辑+发布） |
| `lockOwnerName` | 当前编辑人姓名，未被占用时为空 |
| `lockRemainMinutes` | 编辑锁最长剩余占用分钟数，未被占用时为空 |

**响应示例**：

```json
{
  "code": 200, "status": "success", "msg": "success",
  "data": {
    "page": 1, "pageSize": 10, "total": 2,
    "data": [
      {
        "id": "1932048000000000001", "defineKey": "leave_apply", "name": "请假申请",
        "version": "3", "status": "design", "publishStatus": "published",
        "latestPublishedVersion": "2", "defineGroup": "人事流程",
        "updateTime": "2026-11-10 15:30:00", "actions": 7,
        "lockOwnerName": "张三", "lockRemainMinutes": 28
      }
    ]
  }
}
```

## 查询定义版本列表

### `GET /api/define/queryDefineVersions`

查询某定义 key 下的全部版本。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defineKey` | string | 是 | 定义唯一标识 |

**出参**（`data[]`）：同[分页查询定义](#分页查询定义)的列表行结构。

## 定义版本详情

### `GET /api/define/getDefineVersion`

获取定义版本的完整内容（含节点、迁移线与布局数据）。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 定义版本 id |

**出参**（`data`）：与[保存定义版本](#保存定义版本)入参同构，另含系统字段：`version`、`status`、`deploymentId`、`deployAt`、`deployer`、`deployerName`、`lockVersion`。设计器编辑时先取本接口，保存时回传 `id`、`defineKey`、`lockVersion`。

## 查询定义分组

### `GET /api/define/listDefineGroups`

获取当前用户可见的流程定义分组清单（列表页分组筛选用）。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `appId` | string | 否 | 按应用过滤，为空不过滤 |

**出参**（`data[]`）：分组名字符串数组，如 `["人事流程", "财务流程"]`。

## 查询定义选项

### `GET /api/define/listDefineOptions`

获取列表筛选用定义选项，每个 defineKey 一行（取当前版本信息）；与列表同一鉴权口径，非管理员仅返回可查看的定义。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `appId` | string | 否 | 按应用过滤 |
| `keyword` | string | 否 | 按名称/defineKey 模糊匹配 |

**出参**（`data[]`）：

| 字段 | 说明 |
| --- | --- |
| `defineKey` | 定义唯一标识 |
| `name` | 流程名称 |
| `defineGroup` | 流程分组 |

## 创建草稿版本

### `GET|POST /api/define/createDraftVersion`

生成或复用可编辑的草稿版本（流程定义列表点击"编辑"时调用）。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 来源版本 id（列表当前展示的版本） |

**出参**（`data`）：

| 字段 | 说明 |
| --- | --- |
| `id` | 草稿版本 id |
| `defineKey` | 定义唯一标识 |
| `version` | 版本号（来源为已发布版本时 +1；来源已是草稿/调试时不变） |
| `status` | `design` |

**版本策略**：

- 来源版本已是草稿（`design`）或调试（`debug`）状态：直接返回该版本，不产生新版本。
- 来源版本为已发布状态：复制生成新草稿版本（版本号 +1）。
- 同时把遗留的多余草稿/调试版本收敛为历史版本，保证同一定义最多只有一个可编辑版本。

## 发布定义

### `GET|POST /api/define/deployDefineVersion`

将草稿版本正式发布为当前版本（`activated`），部署到流程引擎后即可被正式发起接口运行。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 定义版本 id（须为 `design` 或 `debug` 状态） |

**出参**（`data`）：部署 id 字符串。

**业务规则**：

- 原先的已激活版本自动转为 `history`，同一 defineKey 只保留一个已激活版本。
- 其余草稿/调试版本收敛为历史版本。
- 他人正在编辑该定义时发布被拒绝（`4090`）；正式发布成功后自动释放本人持有的编辑锁。
- 需要当前用户具备 `publish` 动作权限。

**错误**：`400` 数据不存在 / 非设计/调试状态不能发布；`403` 无发布权限；`4090` 流程定义正在被他人编辑。

## 调试发布

### `GET|POST /api/define/deployDebugDefineVersion`

将草稿版本发布为调试版本（`debug`），仅[调试发起](integration-runtime.md#启动调试流程)接口可按该版本运行，用于发布前验证。

**入参/出参**：与[发布定义](#发布定义)一致。

**业务规则**：仅 `design` 状态可调试发布；调试发布需要 `edit` 动作权限（与保存同级）；调试版本不影响已激活版本的正常运行。

## 批量部署

### `GET|POST /api/define/batchDeployDefine`

批量部署全部待部署定义，用于系统环境迁移。无入参。

**出参**（`data`）：部署结果描述字符串。

**业务规则**：批量迁移场景忽略状态校验与编辑占用校验，逐个定义执行部署。

## 删除定义

### `DELETE /api/define/deleteDefine`

删除整个流程定义（该 defineKey 下全部版本）。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defineKey` | string | 是 | 定义唯一标识 |

**出参**（`data`）：删除数量。

## 删除定义版本

### `DELETE /api/define/deleteDefineVersion`

删除单个定义版本。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 定义版本 id |

**出参**（`data`）：删除数量。

## JSON 与 BPMN 互转

### `POST /api/define/convertToBpmn`

将定义 JSON 转换为 BPMN 2.0 XML（不入库，供预览或导出）。

**入参**（JSON body）：[流程定义](#保存定义版本)结构，转换时使用其中 `workflowNodes`、`sequenceFlows`、`defineKey`、`name`、`remark` 字段。

**出参**（`data`）：BPMN XML 字符串。

### `POST /api/define/convertToJson`

将 BPMN XML 转换回定义 JSON（不入库）。请求体为 BPMN XML 原始字符串。

**出参**（`data`）：定义 JSON，仅含 `workflowNodes` 与 `sequenceFlows`。

### `GET /api/define/getDefineBpmn`

获取某定义版本已部署的 BPMN XML。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 定义版本 id |

**出参**（`data`）：BPMN XML 字符串。

## 按钮元数据

### `GET /api/define/getButtonMeta`

获取系统支持的按钮元数据清单，设计器据此渲染可配置按钮。无入参。

**出参**（`data[]`）：

| 字段 | 说明 |
| --- | --- |
| `name` | 按钮标识（如 `wf_btn_agree`） |
| `text` | 默认文案 |
| `defaultSave` | 默认表单保存策略：`0` 不保存 / `1` 保存 / `2` 提示确认 |
| `nodeDefaultEnabled` | 各节点类型下默认是否展示：`{"audit": 1, "handle": 0, ...}` |
| `level` | 按钮层级：`primary` 一级 / `special` 特殊 / `secondary` 二级 |

按钮全集与各节点默认值见[流程编排 · 按钮清单](orchestration.md#按钮清单)。

## 编辑锁

流程定义编辑期间通过编辑锁防止并发修改。锁与保存、发布通过同一套并发控制协同：他人持锁时保存/发布被拒绝（`4090`），响应 `data` 中返回持有人信息。

### `POST /api/define/lock/acquire`

获取编辑锁。

**入参**（query 或 form）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `defineKey` | string | 是 | 定义唯一标识 |

**出参**（`data`）：

| 字段 | 说明 |
| --- | --- |
| `lockToken` | 锁令牌，续期/释放时回传 |
| `defineKey` | 定义唯一标识 |
| `ownerName` | 持有人姓名（锁被他人占用时用于提示） |
| `expiresAt` | 过期时间（`yyyy-MM-dd HH:mm:ss`） |
| `remainingMinutes` | 剩余有效时长（分钟） |

**错误**：`4090` 流程定义正在被他人编辑（响应含持有人与剩余时间）。

### `POST /api/define/lock/renew`

续期编辑锁，前端建议每 5 分钟心跳一次。

**入参**（query 或 form）：`defineKey`（string，必填）、`lockToken`（string，必填）。

**出参**（`data`）：续期后的过期时间信息，结构同 [acquire](#post-apidefinelockacquire)。

### `POST /api/define/lock/release`

释放编辑锁（离开编辑页/发布成功时调用）。

**入参**（query 或 form）：`defineKey`（string，必填）、`lockToken`（string，必填）。

**出参**：无业务数据。

## 相关页面

- [流程运行集成](integration-runtime.md)：发起、提交、办理等运行期接口
- [个人流程集成](integration-my.md)：待办、已办、待阅、传阅接口
- [流程编排](orchestration.md)：节点/迁移线/属性模型与取值
