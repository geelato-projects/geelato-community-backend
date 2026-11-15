---
id: integration-runtime
title: Runtime Integration
sidebar_label: Runtime Integration
---

# Runtime Integration

This page documents, endpoint by endpoint, all runtime APIs: process start, next-step and participant resolution, task submission and handling, instance/task queries, instance management and reminding. The behavioral semantics (when to call what, how resolution works) are described in [Workflow Runtime](runtime.md).

## Common Conventions

- All APIs use the unified `/api` prefix and the `{code, status, msg, data}` response (see [Define Integration · Common Conventions](integration-define.md#common-conventions)).
- Paginated responses carry `data` as `{page, pageSize, total, data[]}`.
- Key business codes: `400` task not found / instance not exist / parameter error; `403` no permission; `409` the task has been processed, refresh and retry (concurrent submission); `4031` no permission to view the process instance (insufficient data permission; front ends show a unified block).

### Instance Status and Action Types

Instance status (`status`):

| Value | Description |
| --- | --- |
| `running` | In progress |
| `completed` | Completed (reached the end node normally) |
| `terminated` | Terminated (force-ended) |

Trace action types (`actionType`):

| Value | Description |
| --- | --- |
| `INITIATE` | Start |
| `SUBMIT` | Submit |
| `WITHDRAW` | Withdraw |
| `TRANSFER` | Transfer |
| `TERMINATE` / `FORCE_END` | Terminate / force end |
| `DELETE` | Delete |
| `CIRCULATE` | Circulate |
| `REMIND` | Remind |
| `CANCEL` | Cancelled sibling tasks after a countersign denial |

## Common Response Structures

### NextStep

The core structure of next-step / submit routing; it appears in the `queryNextStep*` responses and the `runWorkflow` input:

| Field | Description |
| --- | --- |
| `stepName` | Step name (e.g. "Submit") |
| `stepTargetName` | Target node name |
| `targetRef` | Target node id, passed back as-is at submit |
| `stepUserList` | Candidate handlers ([StepUser](#stepuser)); passed back at submit, may be trimmed to `assigneeLimit` |
| `isAgree` | Whether the step's flow is an agree line: `1` agree, `0` deny |
| `assigneeLimit` | Handler count limit of the target node (`0` unlimited) |
| `subStepList` | Parallel sub-steps (one entry per branch on parallel fork; same structure) |
| `routeTargetRefs` | Route target id chain (includes traversed gateway/automatic nodes); passed back as-is at submit |
| `pathText` | Path text (flow names, or `current->next1->next2...` when names are missing) |
| `needAssignee` | Whether handlers must be picked: `true` for user tasks; `false` for end nodes / parallel joins (submit directly) |

### StepUser

Handler entry:

| Field | Description |
| --- | --- |
| `userId` | User id |
| `userName` | User name |
| `enName` | English name |
| `orgId` / `orgName` | Department id / name |

### WorkflowInstance

Instance detail (returned by the start APIs and the instance detail API):

| Field | Description |
| --- | --- |
| `procId` | Process instance id |
| `procName` | Instance name (`【Definition Name】Business Name`) |
| `defineKey` | Unique definition identifier |
| `bizId` / `bizName` | Business id / name |
| `status` | Instance status: `running` / `completed` / `terminated` |
| `startUserId` / `startUserName` | Initiator id / name |
| `createTime` / `finishTime` | Start / finish time (`yyyy-MM-dd HH:mm:ss`) |
| `currentWorkflowTasks` | Current active tasks ([WorkflowTask](#workflowtask)) |
| `formPageUrl` | Form page url (for read-only viewing) |
| `withdrawable` | Whether the current user can withdraw |
| `withdrawTaskId` | Withdrawable task id; use it for the withdraw action |
| `remindable` | Whether the current user can remind (initiator + running + active tasks + past the remind interval) |
| `hasMyPendingTask` | Whether the current user has a pending task on this instance |
| `relations` | Current user's relations to the instance: `initiator` / `pending` / `completed` / `toRead` / `read` |
| `canHandle` | Whether the current user can handle it (own active pending task exists) |
| `readonly` | Read-only for the current user (complement of `canHandle`) |
| `currentTaskId` | The current user's own pending task id |
| `currentNode` | Current node name |
| `currentHandlers` | Handlers of current active tasks ([WorkflowTask](#workflowtask)) |
| `countersignProgress` | Countersign progress ([CountersignProgressVO](#countersignprogressvo)); empty when no countersign nodes |

### WorkflowTask

Task / handler entry:

| Field | Description |
| --- | --- |
| `procId` | Process instance id |
| `taskId` | Task id |
| `taskName` | Task name |
| `userId` / `userName` | Handler id / name |
| `orgId` / `orgName` | Handler department id / name |
| `fromTask` | Source task (nested, for tracing) |

### TaskInfoVo

Task detail (returned by `getTaskInfo` / `getCurrentTask`):

| Field | Description |
| --- | --- |
| `id` | Task id |
| `name` | Task name |
| `procId` | Process instance id |
| `procDefId` | Process definition id |
| `assignee` | Handler id |
| `nodeInfo` | Node definition ([Process Node](orchestration.md#process-nodes) structure incl. properties; drives buttons / opinion box / attachment area) |
| `status` | Task status |
| `readOnly` | Read-only flag (to-read scenarios) |
| `bizId` | Business data id (businessKey) |
| `formPageUrl` | Form page url (node `page` property first, then definition `pageId`, then the default form url config) |
| `needNextStep` | Whether submitting this task requires next-step/handler picking: `true` only when the multi-instance/countersign submitter is the last one and the submission completes the node |
| `countersignProgress` | Countersign progress (empty for non-countersign tasks) |

### CountersignProgressVO

Countersign node progress (`countersignProgress[]` of the instance detail and the task detail):

| Field | Description |
| --- | --- |
| `nodeId` / `nodeName` | Countersign node id / name |
| `total` / `completed` / `approved` / `rejected` / `pending` | Expected votes / completed / approved / rejected / pending |
| `nodeStatus` | Node status: `running` / `completed` |
| `arriveTime` / `finishTime` | Node arrival / completion time |
| `items[]` | Per-person detail: `{userId, userName, dept, status(pending/approved/rejected/not_arrived), arriveTime, comment, endTime}` |
| `groupSource` / `groupInnerRule` / `countingUnit` | Grouped-countersign config echo (empty when ungrouped) |
| `groups[]` | Group progress: `{key, name, source, result(pending/agreed/rejected/skipped), total, agree, reject, skipped, members[]}`; `members` share the `items[]` structure |

### ActivityTraceVO

Trace entry (returned by `getProcessTrace`):

| Field | Description |
| --- | --- |
| `nodeId` / `nodeName` | Node id / name |
| `activityType` | Node type (see [Node Types](orchestration.md#node-types)) |
| `startTime` / `endTime` / `duration` | Start / end time / duration |
| `status` | Node processing status |
| `userId` / `userName` | Handler id / name |
| `comment` | Comment |
| `actionType` | Action type, see [Action Types](#instance-status-and-action-types) |
| `attachments[]` | Attachments bound to this action: `{id, name, size, kind(file/image), contentType, fileUrl, uploader, uploaderName, uploadTime, taskId, procInstId, nodeId, actionId}` |
| `countersignProgress[]` | Countersign progress of the node (empty for non-countersign nodes) |

### ProcInstListVo

Instance list row (shared by the instance page query and "started by me"):

| Field | Description |
| --- | --- |
| `procInstId` | Process instance id |
| `procInstName` | Instance name (`【Definition Name】Business Name`) |
| `procDefName` | Process definition name |
| `formRecordId` | Business record id (businessKey set at start) |
| `startUserId` / `startUserName` | Initiator id / name |
| `currentNode` | Current node name (empty for finished instances) |
| `currentHandlers[]` | Current handler names (empty for finished instances) |
| `status` | Instance status: `running` / `completed` / `terminated` |
| `startTime` / `finishTime` | Start / finish time (`finishTime` empty while running) |

## Process Start

### Start Process

#### `POST /api/engine/startWorkflow`

Starts an instance from the activated version; the instance stays on the initiate node and a draft pending task is created for the initiator.

**Input** (JSON body):

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `defineKey` | string | Yes | Process definition key |
| `bizId` | string | Yes | Business id (stored as the instance businessKey for lookup) |
| `bizName` | string | Yes | Business name (instance name is `【Definition Name】Business Name`) |
| `formData` | object | No | Business form data, written into process variables for condition evaluation and form echo |
| `attachIds` | string[] | No | Attachment ids uploaded at start, bound to the start action record |

**Output** (`data`): [WorkflowInstance](#workflowinstance); `currentWorkflowTasks` holds the initiate-node task.

**Request example**:

```json
{
  "defineKey": "leave_apply",
  "bizId": "LV202611150001",
  "bizName": "Alice's leave request",
  "formData": { "days": 3, "reason": "annual leave" }
}
```

**Response example**:

```json
{
  "code": 200, "status": "success", "msg": "success",
  "data": {
    "procId": "5001", "procName": "【Leave Application】Alice's leave request",
    "defineKey": "leave_apply", "bizId": "LV202611150001", "bizName": "Alice's leave request",
    "status": "running", "startUserId": "u_1001", "startUserName": "Alice",
    "createTime": "2026-11-15 10:00:00", "finishTime": null,
    "currentWorkflowTasks": [
      { "procId": "5001", "taskId": "6001", "taskName": "Apply",
        "userId": "u_1001", "userName": "Alice", "fromTask": null }
    ]
  }
}
```

**Errors**: `400` definition not found or not published (no activated version).

### Start and Submit

#### `POST /api/engine/startWorkflowAtomicity`

Starts the instance and submits the initiate node in one call, flowing straight to the next node.

**Input** (JSON body): the [Start Process](#start-process) input plus:

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `selectStepList` | array | Yes | Next-step selection ([NextStep](#nextstep) array), from `queryNextStepPrediction` / `queryNextStep` |
| `comment` | string | No | Initiate-node comment, written into the operation record |

**Output** (`data`): [WorkflowInstance](#workflowinstance); `currentWorkflowTasks` holds the tasks after the flow.

### Start Debug Process

#### `POST /api/engine/startDebugWorkflow`

Starts an instance from the debug (`debug`) version; behavior, input and output match [Start Process](#start-process). Returns `400` when the definition has no debug version.

### Debug Start and Submit

#### `POST /api/engine/startDebugWorkflowAtomicity`

Starts and submits from the debug version; matches [Start and Submit](#start-and-submit).

## Next-Step and Participant Resolution

### Query Next Step

#### `GET|POST /api/engine/queryNextStep`

Resolves the reachable next nodes and candidate handlers for a running instance's task.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `procId` | string | Yes | Process instance id |
| `taskId` | string | Yes | Process task id |
| `isAgree` | int | No | Filter outgoing flows by conclusion: `1` agree lines only, `0` deny lines only; omit for all |

**Output** (`data[]`): [NextStep](#nextstep) list. On a parallel fork each branch is returned as `targetRef + subStepList`.

**Errors**: `400` process instance not found / task not found.

### Predict Next Step Before Start

#### `GET|POST /api/engine/queryNextStepPrediction`

Before the process is started, predicts the first stop after the initiate submission, by definition.

**Input** (query + body):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `defineKey` | string | Yes | Process definition key |
| `debug` | boolean | No | Predict by the debug version, default `false` |
| `formData` | object | No | Draft form data (JSON body) for condition-line evaluation; may be empty |

**Output** (`data[]`): [NextStep](#nextstep) list.

### Query Next-Step Users

#### `GET|POST /api/engine/queryStepUser`

Resolves candidate handlers of a target node on a running instance.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `procId` | string | Yes | Process instance id |
| `targetRef` | string | Yes | Target node id |

**Output** (`data[]`): [StepUser](#stepuser) list.

### Predict Handlers Before Start

#### `GET|POST /api/engine/queryStepUserPrediction`

Before the process is started, predicts candidate handlers of a node, by definition.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `defineKey` | string | Yes | Process definition key |
| `targetRef` | string | Yes | Target node id |
| `debug` | boolean | No | Resolve by the debug version, default `false` |

**Output** (`data[]`): [StepUser](#stepuser) list.

### Get Draft Form Page Url

#### `GET|POST /api/engine/getDefineFormPageUrl`

Returns the form page url bound to the definition, for loading the business form in an iframe before starting.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `defineKey` | string | Yes | Process definition key |
| `debug` | boolean | No | Resolve by the debug version, default `false` |

**Output** (`data`): form page url string (computed from the definition `pageId` and the default form url config, template like `http://host/{pageId}?bizId={bizId}`).

## Task Submission and Handling

### Run Task

#### `POST /api/engine/runWorkflow`

Submits the current task and drives the flow — the unified entry for approval / handling / initiate submission.

**Input** (JSON body):

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `procId` | string | Yes | Process instance id |
| `taskId` | string | Yes | Current task id (must be the current user's pending task) |
| `selectStepList` | array | Yes | Next-step selection ([NextStep](#nextstep) array); pass back `targetRef`, `stepUserList`, `routeTargetRefs`, `subStepList` from the resolution APIs; `stepUserList` may be empty when the target needs no picking |
| `comment` | string | No | Approval comment, written into the operation record |
| `attachIds` | string[] | No | Attachment ids bound to this submission |

**Output** (`data`): `"The Workflow Operate Success!"`.

**Request example**:

```json
{
  "procId": "5001",
  "taskId": "6001",
  "comment": "Agreed, please follow the policy",
  "selectStepList": [
    {
      "stepName": "Submit",
      "stepTargetName": "Manager Approval",
      "targetRef": "audit_1",
      "isAgree": 1,
      "assigneeLimit": 1,
      "needAssignee": true,
      "stepUserList": [
        { "userId": "u_2001", "userName": "Bob", "orgId": "o_10", "orgName": "Engineering" }
      ]
    }
  ]
}
```

**Rules**:

- The task must belong to the current user and not be suspended, otherwise `400`/`403`.
- Countersign: when one person votes deny (`selectStepList` contains `isAgree=0`), sibling pending tasks of the same node are cancelled and the deny line is followed.
- Concurrent submissions of the same task are retried inside the transaction; after exhausting retries the engine returns `409 The task has been processed, please refresh and retry`.
- When the target node has no explicitly picked users, the engine resolves participants from the node `assignee` config; on an empty result the node's `emptyAssigneeStrategy` applies (see [Workflow Orchestration · Empty Participant Strategy](orchestration.md#empty-participant-strategy)).

### Withdraw

#### `POST /api/engine/withdraw`

The initiator withdraws a sent-out process; the process returns to the initiator's pending list.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `procId` | string | Yes | Process instance id |
| `taskId` | string | Yes | Task id to withdraw (take `withdrawTaskId` from the instance detail) |
| `comment` | string | Yes | Withdrawal reason, written into the operation record |

**Output** (`data`): result description string.

**Rules**: only completed tasks with no later node processing record can be withdrawn; the instance detail's `withdrawable` is the visibility flag.

### Reject to Previous Step

#### `POST /api/engine/rejectToLastStep`

Returns the process to the previous processing node.

**Input** (query): `procId` (string, required), `taskId` (string, required, current task id), `comment` (string, required).

**Output** (`data`): result description string.

### Reject to Start

#### `POST /api/engine/rejectToStart`

Returns the process to the initiate node; the initiator edits and resubmits.

**Input** (query): same as [Reject to Previous Step](#reject-to-previous-step) (`procId`, `taskId`, `comment`).

**Output** (`data`): result description string.

### Reject to Node

#### `POST /api/engine/rejectToNode`

Returns the process to any specified node.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `procId` | string | Yes | Process instance id |
| `taskId` | string | Yes | Current task id |
| `targetNodeId` | string | Yes | Target node id to return to |
| `comment` | string | Yes | Reject comment |

**Output** (`data`): result description string.

### Transfer Task

#### `POST /api/engine/transferTask`

Hands the own pending task to another user.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `taskId` | string | Yes | Task id |
| `userId` | string | Yes | Target user id |
| `comment` | string | No | Transfer comment |

**Output** (`data`): result description string.

## Instance and Task Queries

### Get Current Tasks

#### `GET /api/engine/getCurrentTask`

Lists all active tasks of an instance.

**Input** (query): `procId` (string, required).

**Output** (`data[]`): [TaskInfoVo](#taskinfovo) list.

### Get Instance Detail

#### `GET /api/engine/getProcInstance`

Returns the instance detail. Running instances return runtime data; finished instances (no runtime state) are reconstructed from history so the detail page still renders.

**Input** (query): `procId` (string, required).

**Output** (`data`): [WorkflowInstance](#workflowinstance).

**Error**: `4031` no permission to view the process instance (insufficient data permission).

### Get Instance Tasks

#### `GET /api/engine/getProcTask`

Lists all tasks of an instance (with handler info).

**Input** (query): `procId` (string, required).

**Output** (`data[]`): [WorkflowTask](#workflowtask) list.

### Get Task Detail

#### `GET /api/engine/getTaskInfo`

Returns the task detail by task id (node definition, form page url and submit hints included).

**Input** (query): `taskId` (string, required).

**Output** (`data`): [TaskInfoVo](#taskinfovo).

### Page-Query Process Instances

#### `GET /api/engine/pageQueryProcInst`

Page-queries process instances. Data permission: admins see everything; non-admins only see instances related to them (started by me / my pending / my done / my to-read and read).

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `keyword` | string | No | Unified keyword (instance name / definition name fuzzy match) |
| `name` | string | No | Name fuzzy match (legacy parameter) |
| `status` | string | No | Instance status: `running` / `completed` / `terminated` |
| `statusList` | string | No | Statuses comma-separated (e.g. `completed,terminated`); takes precedence over `status` |
| `appId` | string | No | Filter by definition's application |
| `procDefKeys` | string | No | Process types (definition keys), comma-separated |
| `startUserId` | string | No | Filter by initiator |
| `startTimeFrom` / `startTimeTo` | string | No | Start time range (`yyyy-MM-dd HH:mm:ss`, inclusive) |
| `finishTimeFrom` / `finishTimeTo` | string | No | Finish time range |
| `currentAssigneeId` | string | No | Current handler id (running instances) |
| `currentTaskDefKey` | string | No | Current node id (running instances) |
| `sortBy` / `sortOrder` | string | No | Sort field (server-side whitelist) / direction `asc` / `desc` |
| `page` / `pageSize` | int | No | Paging, default `1` / `10` |

**Output** (`data`): [PageResult](#common-conventions); `data[]` holds [ProcInstListVo](#procinstlistvo) rows.

### Get Process Trace

#### `GET /api/engine/getProcessTrace`

Returns node-level operation records of an instance (data source of the process timeline).

**Input** (query): `procId` (string, required).

**Output** (`data[]`): [ActivityTraceVO](#activitytracevo) list.

**Error**: `400` history not exist (instance has no history).

### Get Instance BPMN

#### `GET /api/engine/getProcBpmn`

Returns the BPMN XML of the instance's definition, for read-only process diagram rendering; finished instances are served from history.

**Input** (query): `procId` (string, required).

**Output** (`data`): BPMN XML string.

### Get Instance Form Data

#### `GET /api/engine/getProcFormData`

Looks up process variables (form data) by business id.

:::warning For testing only
Form data should normally be provided by the business system per `bizId`; this API is kept for integration demos.
:::

**Input** (query): `bizId` (string, required).

**Output** (`data`): process variable key-value object.

## Instance Management

### Force End

#### `POST /api/engine/forceEndProcInstance`

Force-terminates a running instance; all unfinished tasks are terminated and the instance becomes `terminated`.

**Input** (query): `procId` (string, required), `comment` (string, optional).

**Output** (`data`): result description string.

### Delete Instance

#### `POST /api/engine/deleteProcInstance`

Deletes the instance with its tasks and operation records; business data is unaffected.

**Input** (query): `procId` (string, required), `comment` (string, optional).

**Output** (`data`): result description string.

## Reminding

### Get Remind Targets

#### `GET /api/engine/remindTargets`

After verifying the current user is the initiator, returns the deduplicated handlers of the instance's active tasks and the default remind text.

**Input** (query): `procId` (string, required).

**Output** (`data[]`):

| Field | Description |
| --- | --- |
| `userId` / `userName` | Handler id / name |
| `defaultContent` | Default remind text |

**Error**: `400` the current user is not the initiator / instance not running.

### Send Remind

#### `POST /api/engine/remind`

The initiator sends a remind to the specified handlers.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `procId` | string | Yes | Process instance id |
| `userIds` | string | Yes | Target user ids, comma-separated |
| `content` | string | No | Remind text; the default text is used when empty |

**Output** (`data`): result description string.

**Rules**: only the initiator can remind, and the remind interval must have elapsed (the instance detail's `remindable` is the visibility flag).

## Related Pages

- [Workflow Runtime](runtime.md): behaviors and the end-to-end sequence behind these APIs
- [My Process Integration](integration-my.md): pending/done/to-read/read queries
- [Define Integration](integration-define.md): definition save and publish APIs
