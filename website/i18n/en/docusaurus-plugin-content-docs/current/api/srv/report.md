---
title: API · Periodic Reports
sidebar_label: Periodic Reports
---
# Periodic Reports (report)

1 controller, 5 endpoints in total. This domain is a design-time API (@ApiRuntimeRestController, category=platform-design).

Responses use the domain-specific `ReportApiResponse<T>` wrapper (fields `msg`, `code`, `status`, `data`; success `code=20000`/`status=success`, error `code=50000`/`status=error`). It follows the same style as `ApiResult` but has no `cache` field.

## ReportConfigController ⚙ — `/periodic-report/report-config`

Backend of the periodic report config editor: report type list, customer config detail, config saving and template adjustment.

### reportTypes Report type list

| Item | Content |
| --- | --- |
| URL | `GET /api/periodic-report/report-config/report-types` |
| Params | None |
| Returns | `ReportApiResponse<List<ReportTypeConfig>>`: `data` is the list of report type configs (`ReportTypeConfig`: `id`, `reportCode`, `reportName`, `scopeType`, `dataProviderCode`, `rendererType`, `enabled`, `tenantCode`). |

### detail Editor detail

| Item | Content |
| --- | --- |
| URL | `GET /api/periodic-report/report-config/detail` |
| Params | `templateId`: query, string, template ID, optional.<br />`reportTypeId`: query, string, report type ID, optional.<br />`customerId`: query, string, customer ID, required. |
| Returns | `ReportApiResponse<ReportConfigDetailData>`: `data` is the editor detail (`reportTypes` report type list, `currentTemplate` current template, `customerConfig` customer config). |

### save Save config

| Item | Content |
| --- | --- |
| URL | `POST /api/periodic-report/report-config/save` |
| Params | `request`: body, `ReportConfigSaveRequest`: `template` (`TemplateMaterial`, template material), `customerConfig` (`CustomerReportConfig`, customer config). |
| Returns | `ReportApiResponse<ReportConfigDetailData>`: `data` is the editor detail after saving. |

### adjustTemplate Adjust template

| Item | Content |
| --- | --- |
| URL | `POST /api/periodic-report/report-config/adjust-template` |
| Params | `request`: body, `TemplateAdjustRequest`: `templateContent` (template content), `templateSchema` (template schema), `feedback` (adjustment feedback). |
| Returns | `ReportApiResponse<TemplateAdjustResult>`: `data.templateContent` is the adjusted template content. |

### reflowTemplate Reflow template

| Item | Content |
| --- | --- |
| URL | `POST /api/periodic-report/report-config/reflow-template` |
| Params | `request`: body, `TemplateReflowRequest`: `templateContent` (template content), `templateSchema` (template schema). |
| Returns | `ReportApiResponse<TemplateAdjustResult>`: `data.templateContent` is the reflowed template content. |
