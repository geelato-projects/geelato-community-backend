# ApiParamController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.script.ApiParamController
- BasePath：/script/api/param
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| createOrUpdate | POST | `/script/api/param/createOrUpdate` |
| get | GET | `/script/api/param/get/{id}` |
| isDelete | DELETE | `/script/api/param/isDelete/{id}` |
| pageQuery | POST | `/script/api/param/pageQuery` |
| query | GET | `/script/api/param/query` |

## 接口详情

### createOrUpdate

- Method：POST
- Path：`/script/api/param/createOrUpdate`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ApiParamController.java#L73-L85

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
- Java 类型：ApiParam


#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/script/api/param/createOrUpdate" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### get

- Method：GET
- Path：`/script/api/param/get/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ApiParamController.java#L63-L71

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
  "{{baseUrl}}/script/api/param/get/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### isDelete

- Method：DELETE
- Path：`/script/api/param/isDelete/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ApiParamController.java#L87-L98

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
  "{{baseUrl}}/script/api/param/isDelete/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### pageQuery

- Method：POST
- Path：`/script/api/param/pageQuery`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ApiParamController.java#L38-L49

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
  "{{baseUrl}}/script/api/param/pageQuery" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### query

- Method：GET
- Path：`/script/api/param/query`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ApiParamController.java#L51-L61

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
  "{{baseUrl}}/script/api/param/query" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
