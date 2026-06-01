# PageController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.base.PageController
- BasePath：/page
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 基于页面id或页面的扩展id（树节点id）获取页面定义及页面自定义信息 | GET | `/page/getPageAndCustom/{idType}/{id}` |
| 基于页面id或页面的扩展id（树节点id）获取页面定义及页面自定义信息 | GET | `/page/getPageAndCustom/{idType}/{id}/*` |
| 用于设计时，基于页面的扩展id（树节点id），返回页面的完整配置信息 | GET | `/page/getPageByExtendId/{extendId}` |
| 用于设计时，基于页面id，返回页面的完整配置信息 | GET | `/page/getPageById/{pageId}` |
| 获取页面的多语言信息 | GET | `/page/getPageLang/{idType}/{id}` |
| 获取页面的多语言信息 | GET | `/page/getPageLang/{idType}/{id}/*` |
| 通知页面配置更新 | GET,POST | `/page/notifyUpdate/{pageId}/{extendId}` |
| 保存页面配置 | POST | `/page/savePage` |

## 接口详情

### 基于页面id或页面的扩展id（树节点id）获取页面定义及页面自定义信息

- Method：GET
- Path：`/page/getPageAndCustom/{idType}/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L42-L45

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| idType | String | 是 |  |  |
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<HashMap<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/getPageAndCustom/{idType}/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 基于页面id或页面的扩展id（树节点id）获取页面定义及页面自定义信息

- Method：GET
- Path：`/page/getPageAndCustom/{idType}/{id}/*`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L42-L45

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| idType | String | 是 |  |  |
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<HashMap<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/getPageAndCustom/{idType}/{id}/*" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 用于设计时，基于页面的扩展id（树节点id），返回页面的完整配置信息

- Method：GET
- Path：`/page/getPageByExtendId/{extendId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L64-L67

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| extendId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<HashMap<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/getPageByExtendId/{extendId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 用于设计时，基于页面id，返回页面的完整配置信息

- Method：GET
- Path：`/page/getPageById/{pageId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L53-L56

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| pageId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<HashMap<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/getPageById/{pageId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 获取页面的多语言信息

- Method：GET
- Path：`/page/getPageLang/{idType}/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L77-L140

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| idType | String | 是 |  |  |
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<HashMap<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/getPageLang/{idType}/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 获取页面的多语言信息

- Method：GET
- Path：`/page/getPageLang/{idType}/{id}/*`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L77-L140

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| idType | String | 是 |  |  |
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<HashMap<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/getPageLang/{idType}/{id}/*" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 通知页面配置更新

- Method：GET,POST
- Path：`/page/notifyUpdate/{pageId}/{extendId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L271-L280

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| pageId | String | 是 |  |  |
| extendId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/page/notifyUpdate/{pageId}/{extendId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 保存页面配置

- Method：POST
- Path：`/page/savePage`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/PageController.java#L288-L345

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
- Java 类型：AppPage
- 推导不确定：是


#### Response
- Java 返回类型：ApiResult<AppPage>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/page/savePage" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
