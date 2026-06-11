# EmailComposeController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.email.EmailComposeController
- BasePath：/email
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| send | POST | `/email/message/send` |
| composeContext | GET | `/email/message/{id}/composeContext` |

## 接口详情

### send

- Method：POST
- Path：`/email/message/send`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailComposeController.java#L23-L37

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
- Java 类型：SendEmailRequest


#### Response
- Java 返回类型：ApiResult<SendEmailResult>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/email/message/send" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### composeContext

- Method：GET
- Path：`/email/message/{id}/composeContext`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/email/EmailComposeController.java#L39-L54

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
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| mode | String | 否 |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<EmailComposeContextDto>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/email/message/{id}/composeContext" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
