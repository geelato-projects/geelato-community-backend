# WeworkValidateController

## 基本信息
- 模块：geelato-web-quickstart
- Controller：cn.geelato.web.wework.WeworkValidateController
- BasePath：/wx/validate
- 条件装配：
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| config | GET | `/wx/validate/config` |
| verifyUrl | GET | `/wx/validate/receive` |

## 接口详情

### config

- Method：GET
- Path：`/wx/validate/config`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-quickstart/src/main/java/cn/geelato/web/wework/WeworkValidateController.java#L46-L53

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
- Java 返回类型：Map<String,String>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/wx/validate/config" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### verifyUrl

- Method：GET
- Path：`/wx/validate/receive`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-quickstart/src/main/java/cn/geelato/web/wework/WeworkValidateController.java#L55-L74

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
| msg_signature | String |  |  |  |
| timestamp | String |  |  |  |
| nonce | String |  |  |  |
| echostr | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：String

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/wx/validate/receive" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
