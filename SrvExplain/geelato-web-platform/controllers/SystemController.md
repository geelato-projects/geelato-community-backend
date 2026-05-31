# SystemController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.security.SystemController
- BasePath：/sys
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/SystemController.java#L31-L134

## 接口列表

| Summary | Method | Path |
|---|---|---|
| getAccountList | GET | `/sys/getRoleListByPage` |
| getUserInfo | GET | `/sys/getUserInfo` |
| logout | GET | `/sys/logout` |
| 用于管理员重置密码 | POST,GET | `/sys/resetPassword` |

## 接口详情

### getAccountList

- Method：GET
- Path：`/sys/getRoleListByPage`
- Produces：
- Consumes：
- 鉴权：无需 Authorization（@IgnoreVerify）
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/SystemController.java#L42-L47

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |

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
curl -X GET \
  "{{baseUrl}}/sys/getRoleListByPage" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
```

### getUserInfo

- Method：GET
- Path：`/sys/getUserInfo`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/SystemController.java#L49-L63

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
  "{{baseUrl}}/sys/getUserInfo" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### logout

- Method：GET
- Path：`/sys/logout`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/SystemController.java#L66-L76

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
- Java 返回类型：ApiResult<NullResult>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/sys/logout" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 用于管理员重置密码

- Method：POST,GET
- Path：`/sys/resetPassword`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/SystemController.java#L88-L96

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
| passwordLength | int | 否 | 8 |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/sys/resetPassword" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
