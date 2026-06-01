# DevViewController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.model.DevViewController
- BasePath：/model/view
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| createOrUpdate | POST | `/model/view/createOrUpdate` |
| get | GET | `/model/view/get/{id}` |
| isDelete | DELETE | `/model/view/isDelete/{id}` |
| pageQuery | POST | `/model/view/pageQuery` |
| query | GET | `/model/view/query` |
| validate | POST | `/model/view/validate` |

## 接口详情

### createOrUpdate

- Method：POST
- Path：`/model/view/createOrUpdate`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevViewController.java#L83-L104

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
- Java 类型：TableView


#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/model/view/createOrUpdate" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### get

- Method：GET
- Path：`/model/view/get/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevViewController.java#L71-L81

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
- Java 返回类型：ApiResult<TableView>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/model/view/get/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### isDelete

- Method：DELETE
- Path：`/model/view/isDelete/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevViewController.java#L106-L122

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
  "{{baseUrl}}/model/view/isDelete/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### pageQuery

- Method：POST
- Path：`/model/view/pageQuery`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevViewController.java#L46-L57

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
- Java 返回类型：ApiPagedResult<?>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/model/view/pageQuery" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### query

- Method：GET
- Path：`/model/view/query`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevViewController.java#L59-L69

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
  "{{baseUrl}}/model/view/query" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### validate

- Method：POST
- Path：`/model/view/validate`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevViewController.java#L124-L138

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
- Java 类型：TableView


#### Response
- Java 返回类型：ApiResult<Boolean>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/model/view/validate" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
