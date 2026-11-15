---
id: integration-define
title: Define Integration
sidebar_label: Define Integration
---

# Define Integration

This page documents, endpoint by endpoint, the APIs for saving, querying, versioning, publishing and locking process definitions. The data model (nodes / flows / properties) is described in [Workflow Orchestration](orchestration.md); version and publish status semantics in [Workflow Orchestration · Versions and Status](orchestration.md#versions-and-status).

## Common Conventions

All APIs use the unified `/api` prefix and respond with:

```json
{ "code": 200, "status": "success", "msg": "success", "data": "..." }
```

| Field | Description |
| --- | --- |
| `code` | Business code: `200` success; `400` parameter/data problem; `403` no permission; `4090` edit-lock conflict; `4091` definition content conflict (optimistic lock) |
| `status` | `success` / `fail` |
| `msg` | Message |
| `data` | Business data, see each API |

Paginated responses carry `data` as:

```json
{ "page": 1, "pageSize": 10, "total": 42, "data": [ "..." ] }
```

Authentication and headers: see [Business Integration](integration.md).

## Save Definition Version

### `POST /api/define/saveDefineVersion`

Saves a process definition version. On create the definition key and the first version are generated; updates are only allowed on versions in `design` or `debug` status.

**Input** (JSON body):

| Field | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | Required on update | Definition version id; empty on create |
| `defineKey` | string | Required on update | Unique definition identifier; empty on create |
| `name` | string | Yes | Process name |
| `remark` | string | No | Remark |
| `appId` | string | Yes | Owning application id |
| `defineGroup` | string | No | Process group |
| `pageId` | string | No | Bound form page id |
| `source` | object | No | Designer layout raw data (stored as-is) |
| `workflowNodes` | array | Yes | Node list, see [Process Nodes](orchestration.md#process-nodes); must contain `start` and `end` nodes |
| `sequenceFlows` | array | Yes | Flow list, see [Sequence Flows](orchestration.md#sequence-flows); at least one |
| `lockVersion` | int | No | Optimistic-lock baseline: pass back the value from the detail API; a mismatch fails the save (`4091`); omit to skip baseline validation |

**Output** (`data`):

| Field | Description |
| --- | --- |
| `id` | Definition version id |
| `defineKey` | Unique definition identifier |
| `version` | Version number |
| `status` | Status after save (`design` or `debug`) |
| `lockVersion` | Latest optimistic-lock version; pass back on the next save |
| `createAt` / `updateAt` | Creation / update time (`yyyy-MM-dd HH:mm:ss`) |

**Request example**:

```json
{
  "defineKey": "leave_apply",
  "name": "Leave Application",
  "appId": "1001",
  "pageId": "leave_form_page",
  "lockVersion": 3,
  "workflowNodes": [
    { "id": "start_1", "name": "Start", "nodeType": "start", "properties": [] },
    { "id": "audit_1", "name": "Manager Approval", "nodeType": "audit", "properties": [
        { "name": "assignee", "value": [{ "assignType": "role", "assignObject": "dept_manager" }] }
      ] },
    { "id": "end_1", "name": "End", "nodeType": "end", "properties": [] }
  ],
  "sequenceFlows": [
    { "id": "flow_1", "sourceRef": "start_1", "targetRef": "audit_1", "isAgree": 1 },
    { "id": "flow_2", "sourceRef": "audit_1", "targetRef": "end_1", "isAgree": 1 }
  ]
}
```

**Rules**:

- Published (`activated`) or history (`history`) versions cannot be modified directly; create a new draft via [Create Draft Version](#create-draft-version) first.
- Updates take a row-level lock on the definition key to serialize against publish/draft creation.
- The current user needs the `edit` action on the definition (action bitmask, see [Page-Query Definitions](#page-query-definitions)).

**Errors**: `400` data not found / published version cannot be modified directly; `4091` definition modified by someone else, reload and retry.

## Page-Query Definitions

### `GET /api/define/pageQueryDefine`

Page-queries process definitions; each row summarizes the definition's latest version.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `keyword` | string | No | Unified keyword, fuzzy-matches name and defineKey |
| `name` | string | No | Name fuzzy match (legacy parameter) |
| `appId` | string | No | Filter by owning application |
| `status` | string | No | Publish status: `published` / `unpublished` |
| `defineGroup` | string | No | Single group (legacy parameter) |
| `defineGroups` | string | No | Multiple groups comma-separated; takes precedence over `defineGroup` |
| `statusList` | string | No | Version statuses comma-separated: `design` / `debug` / `activated` / `history` |
| `updateTimeFrom` / `updateTimeTo` | string | No | Update time range (`yyyy-MM-dd HH:mm:ss`, inclusive) |
| `lockState` | string | No | Edit-lock state: `locked` / `mine` / `others` / `unlocked` |
| `sortBy` | string | No | Sort field (server-side whitelist) |
| `sortOrder` | string | No | `asc` / `desc` |
| `page` / `pageSize` | int | No | Paging, default `1` / `10` |

**Output** (`data.data[]`, one row per definition):

| Field | Description |
| --- | --- |
| `id` | Current (latest) version id |
| `defineKey` | Unique definition identifier |
| `name` | Process name |
| `version` | Current version number |
| `status` | Current version status: `design` / `debug` / `activated` / `history` |
| `publishStatus` | Publish status: `published` (a published version exists) / `unpublished` |
| `latestPublishedVersion` | Latest published version number, empty when unpublished |
| `defineGroup` | Process group |
| `updateTime` | Update time (`yyyy-MM-dd HH:mm:ss`) |
| `actions` | Current user's action bitmask on this definition: `view=1` / `edit=2` / `publish=4` / `grant=8`, OR-ed (e.g. 7 = view+edit+publish) |
| `lockOwnerName` | Current editor name, empty when unlocked |
| `lockRemainMinutes` | Longest remaining edit-lock minutes, empty when unlocked |

**Response example**:

```json
{
  "code": 200, "status": "success", "msg": "success",
  "data": {
    "page": 1, "pageSize": 10, "total": 2,
    "data": [
      {
        "id": "1932048000000000001", "defineKey": "leave_apply", "name": "Leave Application",
        "version": "3", "status": "design", "publishStatus": "published",
        "latestPublishedVersion": "2", "defineGroup": "HR",
        "updateTime": "2026-11-10 15:30:00", "actions": 7,
        "lockOwnerName": "Alice", "lockRemainMinutes": 28
      }
    ]
  }
}
```

## Query Definition Versions

### `GET /api/define/queryDefineVersions`

Lists all versions under one definition key.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `defineKey` | string | Yes | Unique definition identifier |

**Output** (`data[]`): rows in the same shape as [Page-Query Definitions](#page-query-definitions).

## Definition Version Detail

### `GET /api/define/getDefineVersion`

Returns the full content of a definition version (nodes, flows and layout).

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | Yes | Definition version id |

**Output** (`data`): same shape as the [Save Definition Version](#save-definition-version) input, plus system fields: `version`, `status`, `deploymentId`, `deployAt`, `deployer`, `deployerName`, `lockVersion`. Designers fetch this first and pass back `id`, `defineKey`, `lockVersion` when saving.

## List Definition Groups

### `GET /api/define/listDefineGroups`

Lists the definition groups visible to the current user (for list-page group filtering).

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `appId` | string | No | Filter by application; empty for all |

**Output** (`data[]`): group name strings, e.g. `["HR", "Finance"]`.

## List Definition Options

### `GET /api/define/listDefineOptions`

Lightweight definition options for list filtering, one row per defineKey (current version info); same authorization scope as the list — non-admins only see definitions they can view.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `appId` | string | No | Filter by application |
| `keyword` | string | No | Fuzzy match on name/defineKey |

**Output** (`data[]`):

| Field | Description |
| --- | --- |
| `defineKey` | Unique definition identifier |
| `name` | Process name |
| `defineGroup` | Process group |

## Create Draft Version

### `GET|POST /api/define/createDraftVersion`

Creates or reuses an editable draft version (called when clicking "Edit" on the definition list).

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | Yes | Source version id (the version currently shown in the list) |

**Output** (`data`):

| Field | Description |
| --- | --- |
| `id` | Draft version id |
| `defineKey` | Unique definition identifier |
| `version` | Version number (+1 when the source is published; unchanged when it is already a draft/debug) |
| `status` | `design` |

**Version strategy**:

- Source version is already `design` or `debug`: returns that version directly, no new version.
- Source version is published: copies it into a new draft (version +1).
- Leftover draft/debug versions are retired to history, so a definition has at most one editable version.

## Publish Definition

### `GET|POST /api/define/deployDefineVersion`

Publishes a draft version as the activated version; once deployed to the engine it can be run by the formal start APIs.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | Yes | Definition version id (must be in `design` or `debug` status) |

**Output** (`data`): deployment id string.

**Rules**:

- The previously activated version automatically becomes `history`; one definition keeps exactly one activated version.
- Other draft/debug versions are retired to history.
- Publishing is rejected while someone else is editing (`4090`); a successful publish releases the current user's own edit lock.
- The current user needs the `publish` action.

**Errors**: `400` data not found / non-design-or-debug status cannot publish; `403` no publish permission; `4090` definition is being edited by someone else.

## Debug Publish

### `GET|POST /api/define/deployDebugDefineVersion`

Publishes a draft version as a debug version (`debug`), runnable only by the [debug start](integration-runtime.md#start-debug-process) APIs for pre-release validation.

**Input/Output**: same as [Publish Definition](#publish-definition).

**Rules**: only `design` versions can be debug-published; debug publish requires the `edit` action (same level as save); debug versions do not affect the running activated version.

## Batch Deploy

### `GET|POST /api/define/batchDeployDefine`

Batch-deploys all pending definitions, for environment migration. No input.

**Output** (`data`): deployment result description string.

**Rules**: the migration scenario skips status and edit-lock checks and deploys definitions one by one.

## Delete Definition

### `DELETE /api/define/deleteDefine`

Deletes an entire definition (all versions under the defineKey).

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `defineKey` | string | Yes | Unique definition identifier |

**Output** (`data`): number of rows deleted.

## Delete Definition Version

### `DELETE /api/define/deleteDefineVersion`

Deletes a single definition version.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | Yes | Definition version id |

**Output** (`data`): number of rows deleted.

## JSON-BPMN Conversion

### `POST /api/define/convertToBpmn`

Converts definition JSON into BPMN 2.0 XML (not persisted; for preview or export).

**Input** (JSON body): the [definition](#save-definition-version) structure; conversion uses its `workflowNodes`, `sequenceFlows`, `defineKey`, `name`, `remark` fields.

**Output** (`data`): BPMN XML string.

### `POST /api/define/convertToJson`

Converts BPMN XML back into definition JSON (not persisted). The request body is the raw BPMN XML string.

**Output** (`data`): definition JSON, containing only `workflowNodes` and `sequenceFlows`.

### `GET /api/define/getDefineBpmn`

Returns the deployed BPMN XML of a definition version.

**Input** (query):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `id` | string | Yes | Definition version id |

**Output** (`data`): BPMN XML string.

## Button Metadata

### `GET /api/define/getButtonMeta`

Returns the button metadata supported by the system; designers render configurable buttons from it. No input.

**Output** (`data[]`):

| Field | Description |
| --- | --- |
| `name` | Button identifier (e.g. `wf_btn_agree`) |
| `text` | Default text |
| `defaultSave` | Default form save policy: `0` no / `1` save / `2` confirm |
| `nodeDefaultEnabled` | Default visibility per node type: `{"audit": 1, "handle": 0, ...}` |
| `level` | Button level: `primary` / `special` / `secondary` |

The full button set and defaults: [Workflow Orchestration · Button Reference](orchestration.md#button-reference).

## Edit Lock

While a definition is being edited, an edit lock prevents concurrent modification. The lock cooperates with save and publish concurrency control: when someone else holds the lock, save/publish is rejected (`4090`) and the response returns the holder info.

### `POST /api/define/lock/acquire`

Acquires the edit lock.

**Input** (query or form):

| Parameter | Type | Required | Description |
| --- | --- | --- | --- |
| `defineKey` | string | Yes | Unique definition identifier |

**Output** (`data`):

| Field | Description |
| --- | --- |
| `lockToken` | Lock token; pass back on renew/release |
| `defineKey` | Unique definition identifier |
| `ownerName` | Holder name (used for prompting when locked by someone else) |
| `expiresAt` | Expiry time (`yyyy-MM-dd HH:mm:ss`) |
| `remainingMinutes` | Remaining validity (minutes) |

**Error**: `4090` definition is being edited by someone else (response includes holder and remaining time).

### `POST /api/define/lock/renew`

Renews the edit lock; the front end should heartbeat every 5 minutes.

**Input** (query or form): `defineKey` (string, required), `lockToken` (string, required).

**Output** (`data`): renewed expiry info, same shape as [acquire](#post-apidefinelockacquire).

### `POST /api/define/lock/release`

Releases the edit lock (on leaving the editor or after a successful publish).

**Input** (query or form): `defineKey` (string, required), `lockToken` (string, required).

**Output**: no business data.

## Related Pages

- [Runtime Integration](integration-runtime.md): start, submit, handling and instance management APIs
- [My Process Integration](integration-my.md): pending/done/to-read APIs
- [Workflow Orchestration](orchestration.md): node/flow/property models and values
