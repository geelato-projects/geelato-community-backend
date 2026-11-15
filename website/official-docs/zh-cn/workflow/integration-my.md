---
title: 个人流程集成
sidebar_label: 个人流程集成
---

# 个人流程集成

本页逐一说明以当前登录用户为维度的流程查询与操作接口：我的待办、已办、待阅、已阅、我发起的流程、审批常用语与传阅。数据范围自动限定为当前用户，无需（也无法）传用户参数。

## 通用约定

- 接口统一 `/api` 前缀，响应体与分页结构见[流程定义集成 · 通用约定](integration-define.md#通用约定)。
- 分页查询均支持统一关键字（`keyword`）、服务端排序（`sortBy` 服务端白名单校验 / `sortOrder` 仅 `asc` / `desc`）与分页（`page` 默认 `1`、`pageSize` 默认 `10`）。
- 时间参数格式统一 `yyyy-MM-dd HH:mm:ss`，区间含边界。

## 公共出参结构

### MyTask

待办/已办条目：

| 字段 | 说明 |
| --- | --- |
| `id` | 任务 id（办理接口的 `taskId` 取此值） |
| `procInstId` | 流程实例 id |
| `procInstName` | 实例名称（`【定义名】业务名`） |
| `procDefName` | 流程定义名称 |
| `taskDefId` | 任务定义 id（节点 id） |
| `taskName` | 任务名称 |
| `formRecordId` | 业务记录 id（businessKey） |
| `description` | 任务描述 |
| `assigneeId` / `assigneeName` | 办理人 id / 姓名（即当前用户） |
| `ownerId` / `ownerName` | 发起人 id / 姓名 |
| `createTime` | 实例发起时间 |
| `arriveTime` | 任务到达当前节点时间 |
| `finishTime` | 任务完成时间（仅已办） |

### ToReadVo

待阅/已阅条目：

| 字段 | 说明 |
| --- | --- |
| `id` | 待阅记录 id（办结待阅接口的 `id` 取此值） |
| `procInstId` / `procInstName` | 流程实例 id / 名称 |
| `procDefName` | 流程定义名称 |
| `taskId` | 关联流程任务 id |
| `formRecordId` | 业务记录 id |
| `recipientId` / `recipientName` | 接收人 id / 姓名（即当前用户） |
| `senderId` / `senderName` | 传阅人 id / 姓名 |
| `comment` | 传阅意见 |
| `receiveTime` | 接收时间（`yyyy-MM-dd HH:mm:ss`） |
| `readAt` | 阅读时间（仅已阅） |

## 我的待办

### `GET /api/my/getMyPendingTask`

查询当前用户全部待办任务（不分页，全量列表）。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `appId` | string | 否 | 按流程定义所属应用过滤，为空不过滤 |

**出参**（`data[]`）：[MyTask](#mytask) 列表。

### `GET /api/my/pageQueryMyPendingTask`

分页查询当前用户待办任务。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 统一关键字（实例名称/任务名称等模糊匹配） |
| `appId` | string | 否 | 按所属应用过滤 |
| `procDefKeys` | string | 否 | 流程类型（定义 key），多个逗号分隔 |
| `arriveTimeFrom` / `arriveTimeTo` | string | 否 | 到达时间区间（含边界） |
| `stayLevel` | string | 否 | 停留时长档位：`lt8h` 8 小时内 / `8to24h` 8 至 24 小时 / `gt24h` 超 24 小时 |
| `startUserId` | string | 否 | 按发起人过滤 |
| `sortBy` / `sortOrder` | string | 否 | 排序字段（如 `arriveTime`）/ 方向 |
| `page` / `pageSize` | int | 否 | 分页，默认 `1` / `10` |

**出参**（`data`）：[PageResult](#通用约定)，`data[]` 为 [MyTask](#mytask) 列表。`total` 由同口径统计得出，翻页结果连续。

**响应示例**：

```json
{
  "code": 200, "status": "success", "msg": "success",
  "data": {
    "page": 1, "pageSize": 10, "total": 1,
    "data": [
      {
        "id": "6001", "procInstId": "5001",
        "procInstName": "【请假申请】张三的请假申请",
        "procDefName": "请假申请", "taskDefId": "audit_1", "taskName": "主管审批",
        "formRecordId": "LV202611150001",
        "assigneeId": "u_2001", "assigneeName": "李四",
        "ownerId": "u_1001", "ownerName": "张三",
        "createTime": "2026-11-15 10:00:00",
        "arriveTime": "2026-11-15 10:05:00", "finishTime": null
      }
    ]
  }
}
```

## 我的已办

### `GET /api/my/getMyCompleteTask`

查询当前用户全部已办任务（不分页）。

**入参**（query）：`appId`（string，可选，按所属应用过滤）。

**出参**（`data[]`）：[MyTask](#mytask) 列表（含 `finishTime`）。

### `GET /api/my/pageQueryMyCompleteTask`

分页查询当前用户已办任务。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 统一关键字 |
| `appId` | string | 否 | 按所属应用过滤 |
| `procDefKeys` | string | 否 | 流程类型，多个逗号分隔 |
| `finishTimeFrom` / `finishTimeTo` | string | 否 | 完成时间区间（含边界） |
| `durationLevel` | string | 否 | 办理耗时档位：`lt1h` 1 小时内 / `1to8h` 1 至 8 小时 / `8to24h` 8 至 24 小时 / `gt24h` 超 24 小时 |
| `startUserId` | string | 否 | 按发起人过滤 |
| `sortBy` / `sortOrder` | string | 否 | 排序字段（如 `finishTime`）/ 方向 |
| `page` / `pageSize` | int | 否 | 分页，默认 `1` / `10` |

**出参**（`data`）：[PageResult](#通用约定)，`data[]` 为 [MyTask](#mytask) 列表。

## 我的待阅

### `GET /api/my/getMyToRead`

查询当前用户全部待阅（不分页）。

**入参**（query）：`appId`（string，可选）。

**出参**（`data[]`）：[ToReadVo](#toreadvo) 列表。

### `GET /api/my/pageQueryMyToRead`

分页查询当前用户待阅。

**入参**（query）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `keyword` | string | 否 | 统一关键字 |
| `appId` | string | 否 | 按所属应用过滤 |
| `procDefKeys` | string | 否 | 流程类型，多个逗号分隔 |
| `receiveTimeFrom` / `receiveTimeTo` | string | 否 | 接收时间区间（含边界） |
| `senderId` | string | 否 | 按传阅人过滤 |
| `stayLevel` | string | 否 | 待阅停留档位：`lt8h` / `8to24h` / `gt24h` |
| `sortBy` / `sortOrder` | string | 否 | 排序字段 / 方向 |
| `page` / `pageSize` | int | 否 | 分页，默认 `1` / `10` |

**出参**（`data`）：[PageResult](#通用约定)，`data[]` 为 [ToReadVo](#toreadvo) 列表。

## 我的已阅

### `GET /api/my/getMyCompleteRead`

查询当前用户全部已阅（不分页）。

**入参**（query）：`appId`（string，可选）。

**出参**（`data[]`）：[ToReadVo](#toreadvo) 列表（含 `readAt`）。

### `GET /api/my/pageQueryMyCompleteRead`

分页查询当前用户已阅。

**入参**（query）：与[我的待阅分页查询](#get-apimypagequerymytoread)一致，另增加：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `readAtFrom` / `readAtTo` | string | 否 | 已阅时间区间（含边界，仅已阅列表生效） |

**出参**（`data`）：[PageResult](#通用约定)，`data[]` 为 [ToReadVo](#toreadvo) 列表。

## 我发起的

### `GET /api/my/getMyStart`

分页查询当前用户发起的流程实例（发起人固定为当前用户）。

**入参**（query）：与[实例分页查询](integration-runtime.md#分页查询流程实例)一致（`status` / `procDefKeys` / 时间区间 / 排序 / 分页），`startUserId` 参数无需传入。

**出参**（`data`）：[PageResult](#通用约定)，`data[]` 为 [ProcInstListVo](integration-runtime.md#procinstlistvo) 列表。

## 审批常用语

当前用户个人的审批常用语，用于意见框快捷输入；数据归属当前用户，仅本人可见可改。

### `GET /api/my/getOpinionPhraseList`

查询当前用户常用语列表。无入参。

**出参**（`data[]`）：

| 字段 | 说明 |
| --- | --- |
| `id` | 常用语 id |
| `text` | 常用语文本 |
| `enabled` | 是否启用：`0` 停用（不展示于意见框下方）/ `1` 启用 |
| `sortOrder` | 排序序号，升序展示（越小越靠前） |

### `POST /api/my/saveOpinionPhrase`

新增常用语（id 为空时新增，否则更新；归属当前用户）。

**入参**（JSON body）：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `text` | string | 是 | 常用语文本 |
| `enabled` | int | 否 | `0` 停用 / `1` 启用 |
| `sortOrder` | int | 否 | 排序序号 |

**出参**（`data`）：保存后的常用语（含 `id`）。

### `PUT /api/my/updateOpinionPhrase`

更新常用语（文本/启停/排序的部分更新；id 必填且须为本人所有）。

**入参**（JSON body）：在[新增](#post-apimysaveopinionphrase)基础上，`id`（string）必填。

**出参**（`data`）：更新后的常用语。

### `DELETE /api/my/deleteOpinionPhrase`

删除常用语（逻辑删除，须为本人所有）。

**入参**（query）：`id`（string，必填）。

**出参**（`data`）：`"success"`。

## 传阅

### 发送待阅

#### `POST|GET /api/toRead/sendToRead`

把流程实例发给指定人员查阅，为每个接收人生成一条待阅。

**入参**（query 或 form）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `procInstId` | string | 是 | 流程实例 id |
| `taskId` | string | 否 | 关联任务 id |
| `recipientIds` | string | 是 | 接收人用户 id，多个逗号分隔 |
| `comment` | string | 否 | 传阅意见 |

**出参**（`data`）：处理结果描述字符串。

**业务规则**：仅实例相关人（发起人/处理人/已处理人）可发送传阅；传阅动作写入操作记录（`CIRCULATE`）。

### 完成待阅

#### `POST|GET /api/toRead/completeRead`

办结本人一条待阅（标记已阅）。

**入参**（query 或 form）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `id` | string | 是 | 待阅记录 id（待阅列表 `ToReadVo.id`） |

**出参**（`data`）：处理结果描述字符串。

### 批量完成待阅

#### `POST|GET /api/toRead/batchCompleteRead`

批量办结本人待阅。

**入参**（query 或 form）：

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `ids` | string | 是 | 待阅记录 id，多个逗号分隔 |

**出参**（`data`）：成功办结条数。

## 相关页面

- [流程运转](runtime.md)：待办办理与传阅在流程生命周期中的位置
- [流程运行集成](integration-runtime.md)：任务提交与实例查询接口
- [流程定义集成](integration-define.md)：定义保存与发布接口
