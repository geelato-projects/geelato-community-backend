---
id: overview
title: Workflow Overview
sidebar_label: Overview
---

# Geelato Workflow

geelato-workflow is an **independently deployed** workflow service built on the Activiti engine (7.1.0.M6), providing process definition, approval flow, circulation (to-read), attachments, notifications and process permissions. Business systems integrate via REST APIs; the service owns a dedicated workflow datasource plus a platform-database bridge.

:::info Repository
geelato-workflow is a standalone repository (`geelato-workflow`), not part of the geelato-community aggregate build.
:::

## Module Structure

| Module | Description |
| --- | --- |
| `geelato-workflow-core` | Domain model: DTO/PO/VO, WorkflowDefine ↔ BPMN conversion, enums, unified `WorkflowResult` |
| `geelato-workflow-engine` | Engine and all REST controllers (define / engine / my / toread / attach / wfpolicy / notice / script / auto…) |
| `geelato-workflow-quickstart` | Executable host (default port **8080**, `server.port=8080`), bundles a Vue front-end (dev port 5173) |

Foundation: Activiti `activiti-spring-boot-starter`, mybatis-plus, HikariCP, geelato-security, geelato-script, geelato-auth-client.

## REST Conventions

- Controllers use the composed `@WFApiRestController` annotation (= `@ApiRestController(category="workflow")` + JSON produces); the quickstart `ApiPrefixAutoConfiguration` adds the unified `/api` prefix.
- Responses are unified as `WorkflowResult` (`ResultCode` / `WorkflowErrorCode`).
- Full endpoint list: [REST API Reference](api.md).

## Process Definition & Deployment

The designer produces a **JSON process definition** (`WorkflowDefine` / `WorkflowNode` / `GeelatoSequenceFlow`); deployment converts it into a **BPMN 2.0 BpmnModel** (with isAgree extension elements and DI coordinates) via the `WorkflowBpmnConverter` family, then deploys through the Activiti repository service:

```
createDraftVersion → deployDefineVersion
  → DefineService: repositoryService.createDeployment()
      .key(defineKey).category(appId).tenantId(tenantCode)
      .addBpmnModel(defineKey + ".bpmn", bpmnModel).deploy()
```

- Versioning: `queryDefineVersions` / `getDefineVersion`; debug deploy `deployDebugDefineVersion`; batch `batchDeployDefine`.
- Conversion: `convertToBpmn` / `convertToJson` / `getDefineBpmn` (sample BPMN under engine `resources/define/`).
- **Edit locks**: `DefineLockController` (acquire/renew/release) prevents concurrent editing.
- Countersign & jump: `GroupVoteTaskListener` / `VoteCollectTaskListener`; free jump via `Jump2TargetFlowNodeCmd`.

## Engine Capabilities

| Capability | Highlights |
| --- | --- |
| Start | `startWorkflow` (+ atomicity variant, debug start, `runWorkflow`) |
| Prediction | `queryNextStep(Prediction)`, `queryStepUser(Prediction)` — preview next step and assignees before submit |
| Handling | withdraw, reject (last/first/any node), transfer, force end / delete instance |
| My tasks | pending/done/to-read/read queries, started-by-me; opinion-phrase CRUD |
| To-read | send, complete, batch complete |
| Attachments | upload (multipart) and download (class-level `/api/attach`) |
| Notifications | `/send`; SPI channels: Email / PlatformNotice / Webhook / External, retry scheduler, `ext_notice_send_log` |
| Process permissions | policy & binding CRUD, effective/my permission, facts refresh |
| Org search | user/org/role/dept-role keyword queries for pickers |

## Scripting & Auto Nodes

Workflow embeds geelato-script (playground role):

- `POST /api/script/execute` / `parse`, `GET /api/script/kitCatalog` / `versions`;
- Built-in kits: `WfKit`, `WfDirectKit`;
- **Auto nodes**: `AutoNodeDelegate` + executors (`GeelatoScriptExecutor`, `HttpNodeExecutor`) run automatically when the flow reaches them;
- Script datasource `geelato.workflow.script.sql.connect-id=workflowDataSource`, timeout `geelato.workflow.script.timeout-ms=30000`.

## Tables

Activiti `ACT_*` tables + extensions (DDL under repo `doc/sql/`): `ext_re_procdef`, `ext_hi_action`, `ext_to_read(_recipient)`, `ext_task_attach`, `ext_opinion_phrase`, `ext_wf_policy(_binding/_item)` + `ext_wf_auth_fact`, `ext_wf_define_lock`, `ext_notice_send_log`, `wf_user`; platform-side bridging views `v_platform_wf_my_task`, `v_platform_wf_my_completed_task`.

## Current Status

The engine and REST surface are fully usable (including the script playground and auto nodes); the Vue front-end and designer integration are in development. See [Business Integration](integration.md) and the [API reference](api.md).
