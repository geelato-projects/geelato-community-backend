---
id: orchestration
title: Workflow Orchestration
sidebar_label: Orchestration
---

# Workflow Orchestration

This page describes the core model of geelato-workflow process design: **process nodes**, **sequence flows**, **participants**, **process properties** and **node properties**. The designer produces a JSON process definition; on save the workflow service converts it into standard BPMN 2.0 and deploys it to the engine. Integrators only need the business model and fields described here — no BPMN knowledge required.

For definition save/publish/version APIs see [Define Integration](integration-define.md); for runtime behavior see [Workflow Runtime](runtime.md).

## Definition Structure

A process definition consists of three parts:

| Part | Description |
| --- | --- |
| Process nodes (`workflowNodes`) | Node list; must contain a start node and an end node |
| Sequence flows (`sequenceFlows`) | Connections between nodes, at least one; they decide the routing |
| Process properties | Definition-level info: identifiers, name, group, form page, version status |

```json
{
  "defineKey": "leave_apply",
  "name": "Leave Application",
  "appId": "1001",
  "defineGroup": "HR",
  "pageId": "leave_form_page",
  "workflowNodes": [
    { "id": "start_1", "name": "Start", "nodeType": "start", "properties": [] },
    { "id": "initiate_1", "name": "Apply", "nodeType": "initiate", "properties": [] },
    { "id": "audit_1", "name": "Manager Approval", "nodeType": "audit", "properties": [
        { "name": "assignee", "value": [{ "assignType": "role", "assignObject": "dept_manager" }] },
        { "name": "auditType", "value": "any" }
      ] },
    { "id": "end_1", "name": "End", "nodeType": "end", "properties": [] }
  ],
  "sequenceFlows": [
    { "id": "flow_1", "sourceRef": "start_1", "targetRef": "initiate_1", "isAgree": 1 },
    { "id": "flow_2", "name": "Agree", "sourceRef": "initiate_1", "targetRef": "audit_1", "isAgree": 1 },
    { "id": "flow_3", "name": "Pass", "sourceRef": "audit_1", "targetRef": "end_1", "isAgree": 1 }
  ]
}
```

## Process Nodes

A node is a processing step in the process. Each node has these fields:

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | Node identifier, unique within the definition; sequence flows reference nodes by it |
| `name` | string | Node name (2-50 characters) |
| `nodeType` | string | Node type, see table below |
| `properties` | array | Node property list (`{name, value}` pairs), see [Process Node Properties](#process-node-properties) |
| `x` / `y` / `width` / `height` | number | Canvas layout (persisted by the designer, may be empty) |

### Node Types

| Type | Name | Behavior |
| --- | --- | --- |
| `start` | Start | Virtual node; the process entry. Exactly one per definition |
| `end` | End | Virtual node; the process exit. Reaching it completes the process normally |
| `initiate` | Initiate | First node reached after the process starts; the initiator is bound as handler and a draft pending task is created |
| `audit` | Audit | Approver gives an agree/deny conclusion; executed as parallel multi-instance, multiple approvers judged by the completion rule |
| `handle` | Handle | Handling user task; with a configured participant the handler is resolved at runtime, otherwise any user with permission may handle it |
| `notice` | Notice | Automatic: sends notifications per node config and flows on; produces no pending task |
| `service` | Service | Automatic: runs a script or HTTP call and flows on; produces no pending task |
| `exclusiveGateway` | Exclusive gateway | Picks one outgoing flow by condition evaluation |
| `parallelGateway` | Parallel gateway | Forks multiple branches; at join, waits for all branches |
| `hq` | Countersign | Multi-person parallel voting approval node; node conclusion judged by completion rule and reject strategy |
| `countersignStart` / `countersignEnd` | Countersign start/end (legacy) | Legacy countersign model, kept for backward compatibility only; use `hq` for new definitions |

:::note Fallback for automatic nodes
When a notice or service node fails, the engine automatically creates a manual fallback task (handler = initiator). The handler can choose **re-run** (loop back and re-run the failed node) or **skip** (skip the failed node and flow to its original downstream).
:::

## Sequence Flows

A sequence flow connects two nodes and decides the routing:

| Field | Type | Description |
| --- | --- | --- |
| `id` | string | Flow identifier |
| `name` | string | Flow name (e.g. "Agree" / "Deny"; shown in next-step selection and path text) |
| `sourceRef` | string | Source node id |
| `targetRef` | string | Target node id |
| `conditionExpression` | string | Condition expression evaluated against form data / process variables; exclusive gateway outgoing flows must have one |
| `isAgree` | int | Whether this is an agree-type flow: `1` agree line, `0` deny line |

Routing rules:

- **Agree / deny lines**: when submitting from an audit or countersign node, outgoing flows are filtered by the conclusion (`isAgree`) — agree follows `isAgree=1` lines, deny follows `isAgree=0` lines.
- **Condition lines**: `conditionExpression` is evaluated against process variables (including form data); typical use is an exclusive gateway with multiple outgoing flows selected by business data.
- **Default routing**: flows without a condition, or with a true condition, participate in routing. If no exclusive gateway condition matches, the process errors — conditions should be complete at design time.
- **Parallel routing**: after a parallel gateway fork, all outgoing flows take effect; at join, all branches must arrive.

## Participants

User task nodes (audit / handle / hq) configure participants via the `assignee` property; multiple entries can be combined.

### Assignee Configuration

| Field | Description |
| --- | --- |
| `assignType` | Participant type: `user` specified users, `role` roles, `dept` departments, `dept_role` dept-role (`orgId:roleId`), `initiator` the initiator |
| `assignObject` | Participant identifiers, multiple values comma-separated (user ids or role ids) |
| `withChild` | Include sub-departments; only effective for `assignType=dept` |

Runtime semantics per type:

| assignType | assignObject meaning | Resolution |
| --- | --- | --- |
| `user` | User id list | The specified users |
| `role` | Role id list | All users under the roles |
| `dept` | Department id list | All users in the departments; `withChild=true` includes sub-departments |
| `dept_role` | `deptId:roleId` list | Users in the department holding the role |
| `initiator` | Not needed | The process initiator (used as fallback handler for automatic nodes and notice recipients) |

### Resolution Timing

- **Pick at submit**: before submitting, resolve candidate handlers ([StepUser](integration-runtime.md#stepuser)) via the [next-step and participant resolution APIs](integration-runtime.md#next-step-and-participant-resolution) and let the submitter confirm; whether the target node requires picking is marked by `needAssignee`.
- **Fallback resolution**: at submit time, for audit/countersign target nodes without explicitly picked users, the engine resolves participants from the node `assignee` config; when the result is empty the node's `emptyAssigneeStrategy` applies (see [Empty Participant Strategy](#empty-participant-strategy)).

### Assignee Limit

`assigneeLimit` caps the number of participants: `0` unlimited, positive integer `N` at most `N` people. When resolved candidates exceed the limit, the submitter must trim before submitting.

### Empty Participant Strategy

`emptyAssigneeStrategy` decides the behavior when no participant can be resolved for the target node:

| Value | Behavior |
| --- | --- |
| `initiator` | **Default**. The process initiator acts as handler, avoiding a stuck process |
| `skip` | The node is reached with an empty participant set, completes immediately and flows on |
| `error` | Submission fails with an error saying the node has no configured approver |

## Process Properties

Process properties are definition-level info saved with the definition JSON:

| Field | Required | Description |
| --- | --- | --- |
| `defineKey` | Required on update | Unique definition identifier (generated on first create); versions evolve under the same key |
| `name` | Yes | Process name |
| `remark` | No | Remark |
| `appId` | Yes | Owning application id, used to filter definitions and tasks by application |
| `defineGroup` | No | Process group (for list filtering) |
| `pageId` | No | Bound form page id; start/handle pages load the business form (iframe) from it |
| `source` | No | Designer layout raw data (maintained by the front end) |
| `lockVersion` | No | Optimistic-lock baseline; validated on save to detect concurrent edits |

### Versions and Status

Definitions evolve by version under the same `defineKey`. Version status:

| Status | Description |
| --- | --- |
| `design` | Draft: editable, not deployed to the engine |
| `debug` | Debug: produced by debug deploy; runnable only by debug-start APIs, for pre-release validation |
| `activated` | Activated: published; formal start APIs run this version |
| `history` | History: superseded by a newer publication; running instances keep executing the original version |

Deploy actions (publish, debug deploy, batch deploy) and edit locks are documented in [Define Integration](integration-define.md).

## Process Node Properties

Node properties are `{name, value}` pairs in the node `properties` array, distinguished by `name`. The full set:

### Basic Properties

| name | Applies to | value values / structure | Default |
| --- | --- | --- | --- |
| `auditType` | audit / hq | `any` any one approval passes the node; `all` all must approve. `deptStep` (department cascade) and `roleStep` (role cascade) are reserved values, rejected at deploy time in the current version | `all` |
| `assignee` | audit / handle / hq / notice | Assignee config array, see [Participants](#participants) | Unset: handle node is handled by any user with permission |
| `assigneeLimit` | audit / handle / hq | `0` unlimited; positive integer N caps at N people | `0` |
| `page` | All user task nodes | Node-level form page id, overrides definition-level `pageId` | Falls back to definition `pageId` |
| `batchSubmit` | audit / handle | Batch submit configuration | — |

### Form Interaction Properties

| name | Applies to | value values / structure | Default |
| --- | --- | --- | --- |
| `button` | All user task nodes | `{buttonId: {enabled, text, save}}`. `enabled` 0 hide/1 show; `text` button text; `save` form save policy 0 no/1 save/2 confirm | See [Button Reference](#button-reference) |
| `opinion` | audit / handle / hq | `{enabled, required, signature}`: opinion box visibility, required, handwritten signature, all 0/1 | enabled=1, others 0 |
| `attachment` | All user task nodes | `{upload, type, download, delete}`: upload permission 0/1, allowed types (comma-separated), download permission 0/1, delete permission 0 forbidden/1 own items/2 all | — |
| `ccEnabled` | audit / handle / hq | CC switch 0/1 | `0` |
| `ccAssignee` | when ccEnabled=1 | CC recipient config array, same structure as assignee config | — |

### Countersign Rule Properties (audit / hq)

Countersign nodes run as parallel multi-instance: multiple people hold pending tasks simultaneously, and the node conclusion is judged by completion rule and reject strategy:

| name | value values | Default |
| --- | --- | --- |
| `completionRule` | `all` all must agree; `any` any one agree; `ratio` by percentage (with `completionValue` as the numerator, positive integer); `count` by votes (with `completionValue` as vote threshold, positive integer) | `all` |
| `completionValue` | Numerator for `ratio` (e.g. 60 means 60%) or vote threshold for `count` | — |
| `rejectStrategy` | `veto_any` any single denial rolls back; `vote_all` collect all votes then judge | `veto_any` |
| `emptyAssigneeStrategy` | `initiator` / `skip` / `error`, see [Empty Participant Strategy](#empty-participant-strategy) | `initiator` |

:::note auditType vs completionRule
`auditType=any/all` is the simplified form of the approval mode, internally mapped to completion rules `any/all`; an explicit `completionRule` takes precedence over `auditType`. Use `completionRule + completionValue` for ratio- or vote-based control.
:::

### Grouped Countersign Properties (hq)

Grouped countersign splits participants into groups; each group participates in the countersign as one opinion body:

| name | value values | Default |
| --- | --- | --- |
| `groupSource` | `dept` group by department (incl. sub-departments); `role` group by role; `custom` explicit groups (with `groups`); **empty = no grouping**, plain countersign | empty |
| `groupInnerRule` | `all` group agrees only when all members agree; `any` one agreeing member makes the group agree | `all` |
| `countingUnit` | `member` count by person; `group` count by group (completion rule counts group conclusions) | `member` |
| `groupKeyName` | Group name display template (e.g. `{dept} Countersign`) | — |
| `groups` | Explicit groups when `groupSource=custom`: `[{key, name, members: [userId]}]` | — |

Empty groups are skipped automatically (marked `skipped`) and excluded from group-level counting; when all groups are empty, `emptyAssigneeStrategy` applies. Countersign progress (incl. group progress) is queried via the instance detail / countersign progress APIs, see [Runtime Integration · Common Response Structures](integration-runtime.md#common-response-structures).

### Automatic Node Properties (notice / service)

| name | Description |
| --- | --- |
| `noticeConfig` | Node notification config: template, recipients, etc.; the engine sends notifications on node arrival/completion |
| `async` | Whether the automatic node runs asynchronously: `true` async (requires the async executor); synchronous by default |

## Button Reference

`GET /api/define/getButtonMeta` returns the button metadata supported by the system; the designer renders configurable buttons from it and the runtime filters by node type. The submit button is not part of node configuration (injected automatically by the runtime depending on whether the node needs an approval conclusion).

| Button | Text | Default save | Default shown on node types | Level |
| --- | --- | --- | --- | --- |
| `wf_btn_agree` | Agree | save | audit / hq | primary |
| `wf_btn_deny` | Deny | no | audit / hq | primary |
| `wf_btn_back_previous` | Return to previous | no | audit / handle | secondary |
| `wf_btn_back_origin` | Return to initiator | no | audit / handle | secondary |
| `wf_btn_back_any` | Return to any node | no | audit / handle | secondary |
| `wf_btn_stage` | Save draft | save | initiate / audit / handle / hq | special |
| `wf_btn_addsign` | Add signer | no | audit / hq | secondary |
| `wf_btn_end` | End process | no | initiate | secondary |
| `wf_btn_delete` | Delete process | no | initiate | secondary |
| `wf_btn_print` | Print | no | all user task nodes | special |
| `wf_btn_circulate` | Circulate | no | audit / handle / hq / notice / end | special |
| `wf_btn_attach` | Upload attachment | no | all user task nodes | secondary |

Levels: `primary` main action in the action area; `special` special buttons (top-right of form/process info); `secondary` secondary buttons (more actions).

:::note Withdraw and remind
Withdraw and remind are **page-level** buttons, not part of node button config: their visibility is decided by the current user's relation to the instance (initiator, pending task, etc.).
:::

Return-type buttons (return to previous / initiator / any node) are not available on countersign nodes — countersign nodes can only flow forward, return is not supported.

## Related Pages

- [Workflow Runtime](runtime.md): full lifecycle from start to end
- [Define Integration](integration-define.md): definition save, version, publish, edit lock APIs
- [Runtime Integration](integration-runtime.md): start, submit, handling, withdraw/reject APIs
