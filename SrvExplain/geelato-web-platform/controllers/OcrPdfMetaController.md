# OcrPdfMetaController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.ocr.OcrPdfMetaController
- BasePath：/ocr/pdf/meta
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/OcrPdfMetaController.java#L24-L108

## 接口列表

| Summary | Method | Path |
|---|---|---|
| createOrUpdate | POST | `/ocr/pdf/meta/createOrUpdate` |
| get | GET | `/ocr/pdf/meta/get/{id}` |
| isDelete | DELETE | `/ocr/pdf/meta/isDelete/{id}` |
| pageQuery | POST | `/ocr/pdf/meta/pageQuery` |
| query | GET | `/ocr/pdf/meta/query` |
| updateMetaRules | POST | `/ocr/pdf/meta/rule/{pdfId}` |

## 接口详情

### createOrUpdate

- Method：POST
- Path：`/ocr/pdf/meta/createOrUpdate`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/OcrPdfMetaController.java#L70-L83

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
无

#### Query 参数
无

#### Body
- Java 类型：OcrPdfMeta


#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/ocr/pdf/meta/createOrUpdate" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### get

- Method：GET
- Path：`/ocr/pdf/meta/get/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/OcrPdfMetaController.java#L60-L68

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/ocr/pdf/meta/get/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### isDelete

- Method：DELETE
- Path：`/ocr/pdf/meta/isDelete/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/OcrPdfMetaController.java#L85-L96

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<NullResult>

#### curl 示例
```bash
curl -X DELETE \
  "{{baseUrl}}/ocr/pdf/meta/isDelete/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### pageQuery

- Method：POST
- Path：`/ocr/pdf/meta/pageQuery`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/OcrPdfMetaController.java#L35-L46

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
无

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiPagedResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/ocr/pdf/meta/pageQuery" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### query

- Method：GET
- Path：`/ocr/pdf/meta/query`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/OcrPdfMetaController.java#L48-L58

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
无

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/ocr/pdf/meta/query" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### updateMetaRules

- Method：POST
- Path：`/ocr/pdf/meta/rule/{pdfId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/OcrPdfMetaController.java#L98-L107

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| pdfId | String | 是 |  |  |

#### Query 参数
无

#### Body
- Java 类型：Map<String,OcrPdfMeta>


#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/ocr/pdf/meta/rule/{pdfId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
