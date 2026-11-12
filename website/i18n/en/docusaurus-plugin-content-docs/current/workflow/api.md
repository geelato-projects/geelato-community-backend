---
id: api
title: Workflow REST API Reference
sidebar_label: REST API Reference
---

# Workflow REST API Reference

All endpoints are declared via `@WFApiRestController` with the unified `/api` prefix (except the auth controllers, at the end) and return a unified `WorkflowResult`. This is a navigation-level list; field-level inputs follow the controller sources.

## Process Definition `/api/define`

| Endpoint | Description |
| --- | --- |
| `POST /saveDefineVersion` | Save a JSON definition version |
| `GET /pageQueryDefine` | Definition page query |
| `GET /queryDefineVersions`, `GET /getDefineVersion` | Version list / detail |
| `GET /listDefineGroups`, `GET /listDefineOptions` | Groups and options (start-page pickers) |
| `GET/POST /createDraftVersion` | Create a draft version |
| `POST /deployDefineVersion`, `/deployDebugDefineVersion`, `/batchDeployDefine` | Deploy / debug deploy / batch deploy |
| `POST /convertToBpmn`, `POST /convertToJson`, `GET /getDefineBpmn` | JSON↔BPMN conversion, fetch BPMN |
| `GET /getButtonMeta` | Button metadata (node actions) |

**Edit lock `/api/define/lock`**: `POST /acquire`, `/renew`, `/release` (defineKey, lockToken)

## Engine `/api/engine`

| Endpoint | Description |
| --- | --- |
| `POST /startWorkflow`, `/startWorkflowAtomicity` | Start (business+process atomic variant) |
| `POST /startDebugWorkflow`, `/startDebugWorkflowAtomicity`, `/runWorkflow` | Debug start / rerun |
| `GET/POST /queryNextStep`, `/queryNextStepPrediction` | Next-step prediction |
| `GET/POST /queryStepUser`, `/queryStepUserPrediction` | Assignee prediction |
| `GET /getDefineFormPageUrl` | Node form page URL template |
| `GET /getCurrentTask`, `/getProcInstance`, `/pageQueryProcInst`, `/getProcFormData`, `/getProcTask`, `/getTaskInfo`, `/getProcessTrace`, `/getProcBpmn` | Instance / task / trace / form / diagram queries |
| `POST /withdraw` | Withdraw |
| `POST /rejectToLastStep`, `/rejectToStart`, `/rejectToNode` | Reject (last / start / any node) |
| `POST /transferTask` | Transfer |
| `POST /forceEndProcInstance`, `/deleteProcInstance` | Force end / delete instance |
| `GET /remindTargets`, `POST /remind` | Remind targets / remind |

## My Tasks `/api/my`

| Endpoint | Description |
| --- | --- |
| `GET /getMyPendingTask`, `/pageQueryMyPendingTask` | Pending |
| `GET /getMyCompleteTask`, `/pageQueryMyCompleteTask` | Completed |
| `GET /getMyToRead`, `/pageQueryMyToRead` | To-read |
| `GET /getMyCompleteRead`, `/pageQueryMyCompleteRead` | Read |
| `GET /getMyStart` | Started by me |
| `GET /getOpinionPhraseList`, `POST /saveOpinionPhrase`, `/updateOpinionPhrase`, `/deleteOpinionPhrase` | Opinion phrases CRUD |

## To-read `/api/toread`

`POST /sendToRead`, `POST /completeRead`, `POST /batchCompleteRead`

## Attachments

`POST /api/attach/upload` (multipart); `GET /api/attach/download?id=`

## Process Permissions `/api/wfpolicy`

`GET /listPolicy`, `POST /savePolicy`, `POST /deletePolicy`; `GET /listBinding`, `POST /saveBinding`, `POST /deleteBinding`; `POST /refreshFacts`; `GET /getEffectivePermission`, `/getMyPermission`

## Notifications `/api/notice`

`POST /send`, `POST /sendLogs`; channel SPI: Email / PlatformNotice / Webhook / External.

## Script `/api/script`

`POST /execute`, `POST /parse`; `GET /kitCatalog`, `GET /versions`

## Org Search `/api/org`

`GET /queryUserByKeyword`, `/queryOrgByKeyword`, `/queryRoleByKeyword`, `/queryDeptRoleByKeyword`

## Auth (not WFApiRestController)

`POST /api/auth/login` (local `wf_user` login, issues a JWT); `GET /oauth2/callback` (OAuth2 callback from the auth center).

Health: `WorkflowHealthEndPoint`.
