# PageCheckController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.base.PageCheckController
- BasePath：/page/check
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 页面签出/签入操作 | POST | `/page/check/` |
| 获取已签出页面列表 | GET | `/page/check/list` |
| 申请接管页面 | POST | `/page/check/request` |
| 取消接管请求 | POST | `/page/check/request/cancel` |
| 获取接管请求列表 | GET | `/page/check/request/list` |
| 处理接管请求 | POST | `/page/check/request/process` |
| 同步页面状态 | GET | `/page/check/sync` |

## 接口详情

### 页面签出/签入操作

- Method：POST
- Path：`/page/check/`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageCheckController.java#L32-L153

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
- Java 返回类型：ApiResult<Map<String,Object>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/page/check/" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 获取已签出页面列表

- Method：GET
- Path：`/page/check/list`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageCheckController.java#L200-L233

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
| appId | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<List<Map<String,Object>>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/check/list" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 申请接管页面

- Method：POST
- Path：`/page/check/request`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageCheckController.java#L274-L338

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
- Java 返回类型：ApiResult<Map<String,Object>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/page/check/request" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 取消接管请求

- Method：POST
- Path：`/page/check/request/cancel`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageCheckController.java#L463-L508

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
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/page/check/request/cancel" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 获取接管请求列表

- Method：GET
- Path：`/page/check/request/list`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageCheckController.java#L346-L367

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
| userId | String |  |  |  |
| status | String | 否 |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<List<AppPageCheckReq>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/check/request/list" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 处理接管请求

- Method：POST
- Path：`/page/check/request/process`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageCheckController.java#L374-L456

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
- Java 返回类型：ApiResult<Map<String,Object>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/page/check/request/process" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 同步页面状态

- Method：GET
- Path：`/page/check/sync`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageCheckController.java#L240-L267

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
| pageId | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<Map<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/check/sync" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
