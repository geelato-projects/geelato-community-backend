---
id: integration
title: Workflow Business Integration
sidebar_label: Business Integration
---

# Workflow Business Integration Guide

How to deploy the workflow service and how business systems integrate (auth, datasources, configuration). Endpoints: [REST API Reference](api.md); configuration details: [Workflow Module Configuration](../system-config/workflow.md).

## Deployment

- Artifact: `geelato-workflow-quickstart` executable jar (build.bat / run.bat / publish.bat under repo `bin/`).
- Port: `server.port=8080` (front-end dev port 5173).
- **Dual datasources** (both HikariCP, defined by `WorkflowDataSourceConfiguration`):

| Datasource | Prefix | Purpose |
| --- | --- | --- |
| workflow | `spring.datasource.workflow.*` | Process DB: Activiti `ACT_*` and `ext_*` tables |
| platform | `spring.datasource.platform.*` | Platform bridge: org/user/role snapshots, cross-DB views (alias `primaryDataSource` for framework conventions) |

- Activiti: `spring.activiti.dbHistoryUsed=true`, `historyLevel=audit`, `databaseSchemaUpdate=false` (schema managed by scripts under repo `doc/sql/`).
- Platform-DB init scripts: `ext_hi_action.sql`, `ext_re_procdef.sql`, `ext_to_read*.sql`, views `v_platform_wf_my_task.sql`, etc.

## Authentication (three channels)

1. **Local login**: `POST /api/auth/login` validates against the workflow DB's `wf_user` table (SHA-1 + salt, 1024 iterations) and issues a `JWTBearer` token — good for standalone trials.
2. **Unified auth (OAuth2)**: `GET /oauth2/callback` receives the auth-center callback and exchanges the token; configure `geelato.oauth2.url/clientId/clientSecret/redirectUri/frontendRedirectUri`. See [Geelato AuthServer](../authentication/overview.md).
3. **auth-client security contract**: both business and workflow sides read org/user/role data from the auth center via `geelato-auth-client` (`geelato.auth.client.base-url/client-id/client-secret`).

**Org snapshots**: the engine syncs org/user/role snapshots from the platform DB via `WorkflowDBSecurityProvider` + `DBUserSnapshotLoader` / `DBOrgSnapshotLoader` / `DBRoleSnapshotLoader`; node assignee resolution and permission decisions use these snapshots.

## Configuration Essentials

| Key | Description |
| --- | --- |
| `geelato.workflow` | Module switch (quickstart default true) |
| `geelato.workflow.admin-roles` | Admin roles |
| `geelato.workflow.form.default-page-url` | Node form page template (`http://localhost:5173/{pageId}?bizId={bizId}`) |
| `geelato.workflow.attach.store-dir` / `download-url-prefix` | Attachment storage and download prefix |
| `geelato.workflow.script.sql.connect-id` / `timeout-ms` | Script datasource and timeout |
| `geelato.oauth2.*`, `geelato.auth.client.*` | Auth center and security contract |

## Integration Patterns

1. **HTTP API**: call `/api/engine/startWorkflow` at approval points, list `/api/my/pageQueryMyPendingTask`, load `/getCurrentTask`, complete tasks from the form page; `queryNextStepPrediction` previews the path before submit.
2. **Notification callbacks**: `NoticeController /send` with Webhook/PlatformNotice channels push todo events to business systems; failures retry automatically (`ext_notice_send_log`).
3. **Forms & attachments**: form page URLs are parameterized with `{pageId}/{bizId}`; attachments via `/api/attach/*`.

:::warning MetaConfiguration
The quickstart `MetaConfiguration` runs `MetaManager.singleInstance().scanAndParse("cn.geelato.web", false)` + `parseDBMeta(null)` **in its constructor**: the scan package is hard-coded to `cn.geelato.web` and all metadata is parsed from the DB during construction. Startup fails if the datasource is not ready, platform tables are missing, or the package name mismatches. MetaManager is a global singleton — beware state pollution when customizing.
:::

## Demo Pages

The quickstart ships `static/script-demo.html` (script playground demo) and `static/security-contract-demo.html` (security contract demo) as integration references.
