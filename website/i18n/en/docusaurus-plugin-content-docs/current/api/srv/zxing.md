---
title: API · Barcode
sidebar_label: Barcode
---
# Barcode (zxing)

1 controller, 9 endpoints in total. Endpoints marked with ⚙ are design-time APIs.

## BarcodeController — `/zxing/barcode`

CRUD, code uniqueness validation, barcode image generation by config, and available font listing for barcode configurations.

### pageQuery Paged query

| Item | Content |
| --- | --- |
| URL | `POST /api/zxing/barcode/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is a page of barcode configs (`Barcode`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/zxing/barcode/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<Barcode>>`: list of barcode configs. |

### get Get details

| Item | Content |
| --- | --- |
| URL | `GET /api/zxing/barcode/get/{id}` |
| Params | `id`: path, string, barcode config ID. |
| Returns | `ApiResult<Barcode>`: barcode config details. |

### createOrUpdate Create or update

| Item | Content |
| --- | --- |
| URL | `POST /api/zxing/barcode/createOrUpdate` |
| Params | `form`: body, `Barcode` entity; non-empty `id` updates, empty `id` creates (`afterSet` defaults are applied before saving). |
| Returns | `ApiResult<Barcode>`: the saved barcode config. |

### isDelete Delete barcode config

| Item | Content |
| --- | --- |
| URL | `DELETE /api/zxing/barcode/isDelete/{id}` |
| Params | `id`: path, string, barcode config ID. |
| Returns | `ApiResult<NullResult>`: `data` is empty; soft delete. Fails if the config does not exist. |

### validate Validate code uniqueness

| Item | Content |
| --- | --- |
| URL | `POST /api/zxing/barcode/validate` |
| Params | `form`: body, `Barcode` entity, providing the `code`, `id`, and `tenantCode` to validate (`tenantCode` falls back to the current session tenant when blank). |
| Returns | `ApiResult<Boolean>`: `true` means validation passes (no undeleted record with the same `code` in `platform_barcode`, excluding itself); `false` means a duplicate exists. |

### generate Generate barcode by config

| Item | Content |
| --- | --- |
| URL | `GET /api/zxing/barcode/generate/{type}/{id}/{text}` |
| Params | `type`: path, string, output mode: `attach` (save as attachment), `base64` (Base64 string), any other value returns the generated image file path (case-insensitive).<br />`id`: path, string, barcode config ID.<br />`text`: path, string, text encoded in the barcode. |
| Returns | `ApiResult<?>`: with `type=attach`, `data` is the saved attachment record (`Map<String,Object>`, genre `Barcode`); with `type=base64`, a `data:[contentType];base64,[...]` string; with any other `type`, `data` is the save path of the generated barcode image. Fails if the barcode config does not exist. |

### generate Generate barcode without config

| Item | Content |
| --- | --- |
| URL | `POST /api/zxing/barcode/generate/{type}/{text}` |
| Params | `type`: path, string, output mode, same as above.<br />`text`: path, string, text encoded in the barcode.<br />`form`: body, `Barcode` entity, the barcode config (`afterSet` defaults are applied before saving). |
| Returns | `ApiResult<?>`: same content as above, determined by `type`. |

### getFonts List available fonts

| Item | Content |
| --- | --- |
| URL | `GET /api/zxing/barcode/getFonts` |
| Params | None |
| Returns | `ApiResult<List<String>>`: available font family names on the server (cached in Redis, expiring after 7 days). |
