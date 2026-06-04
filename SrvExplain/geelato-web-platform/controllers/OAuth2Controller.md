# OAuth2Controller

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.auth.OAuth2Controller
- BasePath：/oauth2
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| login | POST | `/oauth2/login` |
| 刷新token | POST | `/oauth2/refreshToken` |

## 接口详情

### login

- Method：POST
- Path：`/oauth2/login`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/auth/OAuth2Controller.java#L35-L56

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
- Java 类型：String
- 推导不确定：是


#### Response
- Java 返回类型：ApiResult<LoginResult>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/oauth2/login" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 刷新token

- Method：POST
- Path：`/oauth2/refreshToken`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/auth/OAuth2Controller.java#L65-L108

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
- Java 类型：String
- 推导不确定：是


#### Response
- Java 返回类型：ApiResult<HashMap<String,String>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/oauth2/refreshToken" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
