# EmailDraftController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.email.EmailDraftController
- BasePath：/email/draft
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| createOrUpdate | POST | `/email/draft/createOrUpdate` |
| pageQuery | POST | `/email/draft/pageQuery` |
| delete | DELETE | `/email/draft/{id}` |
| get | GET | `/email/draft/{id}` |
| send | POST | `/email/draft/{id}/send` |

## 接口详情

### createOrUpdate

- Method：POST
- Path：`/email/draft/createOrUpdate`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailDraftController.java#L71-L85

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
- Java 类型：EmailDraftUpsertRequest


#### Response
- Java 返回类型：ApiResult<String>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/email/draft/createOrUpdate" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### pageQuery

- Method：POST
- Path：`/email/draft/pageQuery`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailDraftController.java#L33-L51

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
- Java 类型：Map<String,Object>


#### Response
- Java 返回类型：ApiPagedResult<List<EmailDraftDto>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/email/draft/pageQuery" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### delete

- Method：DELETE
- Path：`/email/draft/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailDraftController.java#L87-L99

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
- Java 返回类型：ApiResult<Boolean>

#### curl 示例
```bash
curl -X DELETE \
  "{{baseUrl}}/email/draft/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### get

- Method：GET
- Path：`/email/draft/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailDraftController.java#L53-L69

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
- Java 返回类型：ApiResult<EmailDraftDto>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/email/draft/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### send

- Method：POST
- Path：`/email/draft/{id}/send`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailDraftController.java#L101-L115

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
- Java 返回类型：ApiResult<SendEmailResult>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/email/draft/{id}/send" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
