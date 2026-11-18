---
title: API · 周期报表
sidebar_label: 周期报表
---
# 周期报表（report）

1 个 Controller，共 5 个端点。本域为设计态接口（@ApiRuntimeRestController，category=platform-design）。

返回结构为本域自定义的 `ReportApiResponse<T>`（字段 `msg`、`code`、`status`、`data`；成功 `code=20000`、`status=success`，失败 `code=50000`、`status=error`），与 `ApiResult` 同风格但不含 `cache`。

## ReportConfigController ⚙ — `/periodic-report/report-config`

周期报表配置编辑器后端：报表类型清单、客户配置详情、配置保存与模板调整。

### reportTypes 报表类型列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/periodic-report/report-config/report-types` |
| 参数 | 无 |
| 返回 | `ReportApiResponse<List<ReportTypeConfig>>`：`data` 为报表类型配置列表（`ReportTypeConfig`：`id`、`reportCode`、`reportName`、`scopeType`、`dataProviderCode`、`rendererType`、`enabled`、`tenantCode`）。 |

### detail 编辑器详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/periodic-report/report-config/detail` |
| 参数 | `templateId`：query，string，模板 ID，选填。<br />`reportTypeId`：query，string，报表类型 ID，选填。<br />`customerId`：query，string，客户 ID，必填。 |
| 返回 | `ReportApiResponse<ReportConfigDetailData>`：`data` 为编辑器详情（`reportTypes` 报表类型列表、`currentTemplate` 当前模板、`customerConfig` 客户配置）。 |

### save 保存配置

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/periodic-report/report-config/save` |
| 参数 | `request`：body，`ReportConfigSaveRequest`：`template`（`TemplateMaterial`，模板素材）、`customerConfig`（`CustomerReportConfig`，客户配置）。 |
| 返回 | `ReportApiResponse<ReportConfigDetailData>`：`data` 为保存后的编辑器详情。 |

### adjustTemplate 调整模板

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/periodic-report/report-config/adjust-template` |
| 参数 | `request`：body，`TemplateAdjustRequest`：`templateContent`（模板内容）、`templateSchema`（模板结构）、`feedback`（调整反馈）。 |
| 返回 | `ReportApiResponse<TemplateAdjustResult>`：`data.templateContent` 为调整后的模板内容。 |

### reflowTemplate 重排模板

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/periodic-report/report-config/reflow-template` |
| 参数 | `request`：body，`TemplateReflowRequest`：`templateContent`（模板内容）、`templateSchema`（模板结构）。 |
| 返回 | `ReportApiResponse<TemplateAdjustResult>`：`data.templateContent` 为重排后的模板内容。 |
