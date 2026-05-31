# EmailInboxController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.email.EmailInboxController
- BasePath：/email
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailInboxController.java#L33-L282

## 接口列表

| Summary | Method | Path |
|---|---|---|
| listFolders | GET | `/email/folder/list` |
| pageQuery | POST | `/email/message/pageQuery` |
| getDetail | GET | `/email/message/{id}` |
| downloadAttachment | GET | `/email/message/{id}/attachment/{partId}/download` |
| saveAttachment | POST | `/email/message/{id}/attachment/{partId}/save` |
| getAttachments | GET | `/email/message/{id}/attachments` |

## 接口详情

### listFolders

- Method：GET
- Path：`/email/folder/list`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailInboxController.java#L43-L61

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
无

#### Query 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| emailAccountId | String | 否 |  |  |
| pattern | String | 否 |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<List<EmailFolderDto>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/email/folder/list" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### pageQuery

- Method：POST
- Path：`/email/message/pageQuery`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailInboxController.java#L63-L96

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
- Java 返回类型：ApiPagedResult<List<EmailMessageListItemDto>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/email/message/pageQuery" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### getDetail

- Method：GET
- Path：`/email/message/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailInboxController.java#L98-L116

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
- Java 返回类型：ApiResult<EmailMessageDetailDto>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/email/message/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### downloadAttachment

- Method：GET
- Path：`/email/message/{id}/attachment/{partId}/download`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailInboxController.java#L137-L166

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
| partId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：void

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/email/message/{id}/attachment/{partId}/download" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### saveAttachment

- Method：POST
- Path：`/email/message/{id}/attachment/{partId}/save`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailInboxController.java#L168-L227

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
| partId | String | 是 |  |  |

#### Query 参数
无

#### Body
- Java 类型：SaveEmailAttachmentRequest


#### Response
- Java 返回类型：ApiResult<SaveEmailAttachmentResult>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/email/message/{id}/attachment/{partId}/save" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### getAttachments

- Method：GET
- Path：`/email/message/{id}/attachments`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailInboxController.java#L118-L135

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
- Java 返回类型：ApiResult<List<EmailAttachmentDto>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/email/message/{id}/attachments" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
