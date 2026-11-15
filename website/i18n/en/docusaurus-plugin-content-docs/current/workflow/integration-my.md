---
id: integration-my
title: My Process Integration
sidebar_label: My Process Integration
---

# My Process Integration

This page documents, endpoint by endpoint, the APIs scoped to the current logged-in user: my pending tasks, done tasks, to-read, read, processes started by me, opinion phrases and circulation. The data scope is automatically the current user — no user parameter is needed (or accepted).

## Common Conventions

- APIs use the unified `/api` prefix; response and pagination shapes are documented in [Define Integration · Common Conventions](integration-define.md#common-conventions).
- All page queries support a unified keyword (`keyword`), server-side sorting (`sortBy` whitelist / `sortOrder` only `asc` / `desc`) and paging (`page` default `1`, `pageSize` default `10`).
- Time parameters use `yyyy-MM-dd HH:mm:ss`; ranges are inclusive.

## Common Response Structures

### MyTask

Pending / done task entry:

| Field | Description |
| --- | --- |
| `id` | Task id (use as `taskId` in the handling APIs) |
| `procInstId` | Process instance id |
| `procInstName` | Instance name (`【Definition Name】Business Name`) |
| `procDefName` | Process definition name |
| `taskDefId` | Task definition id (node id) |
| `taskName` | Task name |
| `formRecordId` | Business record id (businessKey) |
| `description` | Task description |
| `assigneeId` / `assigneeName` | Handler id / name (the current user) |
| `ownerId` / `ownerName` | Initiator id / name |
| `createTime` | Instance start time |
| `arriveTime` | Time the task arrived at the current node |
| `finishTime` | Task completion time (done list only) |

### ToReadVo

To-read / read entry:

| Field | Description |
| --- | --- |
| `id` | To-read record id (use as `id` in the complete APIs) |
| `procInstId` / `procInstName` | Process instance id / name |
| `procDefName` | Process definition name |
| `taskId` | Related process task id |
| `formRecordId` | Business record id |
| `recipientId` / `recipientName` | Recipient id / name (the current user) |
| `senderId` / `senderName` | Sender id / name |
| `comment` | Circulation comment |
| `receiveTime` | Receive time (`yyyy-MM-dd HH:mm:ss`) |
| `readAt` | Read time (read list only) |

## My Pending Tasks

### `GET /api/my/getMyPendingTask`

Lists all pending tasks of the current user (no paging).

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `appId` | string | No | Filter by definition's application; empty for all |

**Output** (`data[]`): [MyTask](#mytask) list.

### `GET /api/my/pageQueryMyPendingTask`

Page-queries the current user's pending tasks.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `keyword` | string | No | Unified keyword (instance name / task name fuzzy match) |
| `appId` | string | No | Filter by application |
| `procDefKeys` | string | No | Process types (definition keys), comma-separated |
| `arriveTimeFrom` / `arriveTimeTo` | string | No | Arrival time range (inclusive) |
| `stayLevel` | string | No | Stay-duration bucket: `lt8h` under 8h / `8to24h` 8-24h / `gt24h` over 24h |
| `startUserId` | string | No | Filter by initiator |
| `sortBy` / `sortOrder` | string | No | Sort field (e.g. `arriveTime`) / direction |
| `page` / `pageSize` | int | No | Paging, default `1` / `10` |

**Output** (`data`): [PageResult](#common-conventions); `data[]` holds [MyTask](#mytask) rows. `total` is computed with the same criteria, so paging is consistent.

**Response example**:

```json
{
  "code": 200, "status": "success", "msg": "success",
  "data": {
    "page": 1, "pageSize": 10, "total": 1,
    "data": [
      {
        "id": "6001", "procInstId": "5001",
        "procInstName": "【Leave Application】Alice's leave request",
        "procDefName": "Leave Application", "taskDefId": "audit_1", "taskName": "Manager Approval",
        "formRecordId": "LV202611150001",
        "assigneeId": "u_2001", "assigneeName": "Bob",
        "ownerId": "u_1001", "ownerName": "Alice",
        "createTime": "2026-11-15 10:00:00",
        "arriveTime": "2026-11-15 10:05:00", "finishTime": null
      }
    ]
  }
}
```

## My Done Tasks

### `GET /api/my/getMyCompleteTask`

Lists all done tasks of the current user (no paging).

**Input** (query): `appId` (string, optional, filter by application).

**Output** (`data[]`): [MyTask](#mytask) list (with `finishTime`).

### `GET /api/my/pageQueryMyCompleteTask`

Page-queries the current user's done tasks.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `keyword` | string | No | Unified keyword |
| `appId` | string | No | Filter by application |
| `procDefKeys` | string | No | Process types, comma-separated |
| `finishTimeFrom` / `finishTimeTo` | string | No | Completion time range (inclusive) |
| `durationLevel` | string | No | Handling-duration bucket: `lt1h` under 1h / `1to8h` 1-8h / `8to24h` 8-24h / `gt24h` over 24h |
| `startUserId` | string | No | Filter by initiator |
| `sortBy` / `sortOrder` | string | No | Sort field (e.g. `finishTime`) / direction |
| `page` / `pageSize` | int | No | Paging, default `1` / `10` |

**Output** (`data`): [PageResult](#common-conventions); `data[]` holds [MyTask](#mytask) rows.

## My To-Read

### `GET /api/my/getMyToRead`

Lists all to-read items of the current user (no paging).

**Input** (query): `appId` (string, optional).

**Output** (`data[]`): [ToReadVo](#toreadvo) list.

### `GET /api/my/pageQueryMyToRead`

Page-queries the current user's to-read items.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `keyword` | string | No | Unified keyword |
| `appId` | string | No | Filter by application |
| `procDefKeys` | string | No | Process types, comma-separated |
| `receiveTimeFrom` / `receiveTimeTo` | string | No | Receive time range (inclusive) |
| `senderId` | string | No | Filter by sender |
| `stayLevel` | string | No | To-read stay bucket: `lt8h` / `8to24h` / `gt24h` |
| `sortBy` / `sortOrder` | string | No | Sort field / direction |
| `page` / `pageSize` | int | No | Paging, default `1` / `10` |

**Output** (`data`): [PageResult](#common-conventions); `data[]` holds [ToReadVo](#toreadvo) rows.

## My Read

### `GET /api/my/getMyCompleteRead`

Lists all read items of the current user (no paging).

**Input** (query): `appId` (string, optional).

**Output** (`data[]`): [ToReadVo](#toreadvo) list (with `readAt`).

### `GET /api/my/pageQueryMyCompleteRead`

Page-queries the current user's read items.

**Input** (query): same as [My To-Read page query](#get-apimypagequerymytoread), plus:

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `readAtFrom` / `readAtTo` | string | No | Read time range (inclusive; read list only) |

**Output** (`data`): [PageResult](#common-conventions); `data[]` holds [ToReadVo](#toreadvo) rows.

## Started by Me

### `GET /api/my/getMyStart`

Page-queries process instances started by the current user (initiator fixed to the current user).

**Input** (query): same as the [instance page query](integration-runtime.md#page-query-process-instances) (`status` / `procDefKeys` / time ranges / sorting / paging); `startUserId` need not be passed.

**Output** (`data`): [PageResult](#common-conventions); `data[]` holds [ProcInstListVo](integration-runtime.md#procinstlistvo) rows.

## Opinion Phrases

The current user's personal opinion phrases for quick input in the opinion box; data belongs to the current user and is visible/editable only by them.

### `GET /api/my/getOpinionPhraseList`

Lists the current user's opinion phrases. No input.

**Output** (`data[]`):

| Field | Description |
| --- | --- |
| `id` | Phrase id |
| `text` | Phrase text |
| `enabled` | Whether enabled: `0` disabled (hidden from the opinion box) / `1` enabled |
| `sortOrder` | Sort order, ascending (smaller first) |

### `POST /api/my/saveOpinionPhrase`

Creates an opinion phrase (empty id creates, otherwise updates; owned by the current user).

**Input** (JSON body):

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `text` | string | Yes | Phrase text |
| `enabled` | int | No | `0` disabled / `1` enabled |
| `sortOrder` | int | No | Sort order |

**Output** (`data`): the saved phrase (with `id`).

### `PUT /api/my/updateOpinionPhrase`

Updates an opinion phrase (partial update of text/enabled/sortOrder; `id` required and must be owned by the current user).

**Input** (JSON body): as in [create](#post-apimysaveopinionphrase), plus `id` (string) required.

**Output** (`data`): the updated phrase.

### `DELETE /api/my/deleteOpinionPhrase`

Deletes an opinion phrase (logical delete; must be owned by the current user).

**Input** (query): `id` (string, required).

**Output** (`data`): `"success"`.

## Circulation

### Send To-Read

#### `POST|GET /api/toRead/sendToRead`

Sends the process instance to selected people for reading; one to-read item is created per recipient.

**Input** (query or form):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `procInstId` | string | Yes | Process instance id |
| `taskId` | string | No | Related task id |
| `recipientIds` | string | Yes | Recipient user ids, comma-separated |
| `comment` | string | No | Circulation comment |

**Output** (`data`): result description string.

**Rules**: only people related to the instance (initiator / handler / past handler) may circulate; the action is recorded in the trace (`CIRCULATE`).

### Complete To-Read

#### `POST|GET /api/toRead/completeRead`

Marks one of the own to-read items as read.

**Input** (query or form):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | Yes | To-read record id (`ToReadVo.id` from the to-read list) |

**Output** (`data`): result description string.

### Batch Complete To-Read

#### `POST|GET /api/toRead/batchCompleteRead`

Marks several of the own to-read items as read.

**Input** (query or form):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `ids` | string | Yes | To-read record ids, comma-separated |

**Output** (`data`): number of items completed.

## Related Pages

- [Workflow Runtime](runtime.md): where handling and circulation sit in the lifecycle
- [Runtime Integration](integration-runtime.md): task submission and instance query APIs
- [Define Integration](integration-define.md): definition save and publish APIs
