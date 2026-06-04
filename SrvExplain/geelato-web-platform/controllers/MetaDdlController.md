# MetaDdlController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.meta.MetaDdlController
- BasePath：/meta/ddl
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 刷新Redis缓存 | POST | `/meta/ddl/redis/refresh` |
| 根据实体名称重建或创建数据库表，需要切换数据库 | POST | `/meta/ddl/table/{entity}` |
| 根据实体名称重建或创建数据库表，需要切换数据库 | POST | `/meta/ddl/tables/{appId}` |
| 验证视图语句 | POST | `/meta/ddl/view/valid/{connectId}` |
| validateView | POST | `/meta/ddl/view/valid/{connectId}/{entityName}` |
| 新建或更新视图，需要切换数据库 | POST | `/meta/ddl/view/{view}` |
| createOrUpdateViewById | POST | `/meta/ddl/viewOne/{id}` |
| createOrUpdateViewByAppId | POST | `/meta/ddl/views/{appId}` |

## 接口详情

### 刷新Redis缓存

- Method：POST
- Path：`/meta/ddl/redis/refresh`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L121-L130

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
- Java 类型：Map<String,String>


#### Response
- Java 返回类型：ApiMetaResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/redis/refresh" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 根据实体名称重建或创建数据库表，需要切换数据库

- Method：POST
- Path：`/meta/ddl/table/{entity}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L38-L47

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| entity | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiMetaResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/table/{entity}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 根据实体名称重建或创建数据库表，需要切换数据库

- Method：POST
- Path：`/meta/ddl/tables/{appId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L55-L59

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| appId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiMetaResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/tables/{appId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 验证视图语句

- Method：POST
- Path：`/meta/ddl/view/valid/{connectId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L92-L101

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| connectId | String | 是 |  |  |

#### Query 参数
无

#### Body
- Java 类型：Map<String,String>


#### Response
- Java 返回类型：ApiMetaResult<Boolean>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/view/valid/{connectId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### validateView

- Method：POST
- Path：`/meta/ddl/view/valid/{connectId}/{entityName}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L103-L112

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| connectId | String | 是 |  |  |
| entityName | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiMetaResult<Map<String,String>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/view/valid/{connectId}/{entityName}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 新建或更新视图，需要切换数据库

- Method：POST
- Path：`/meta/ddl/view/{view}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L79-L82

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| view | String | 是 |  |  |

#### Query 参数
无

#### Body
- Java 类型：Map<String,String>


#### Response
- Java 返回类型：ApiMetaResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/view/{view}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### createOrUpdateViewById

- Method：POST
- Path：`/meta/ddl/viewOne/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L67-L70

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
- Java 返回类型：ApiMetaResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/viewOne/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### createOrUpdateViewByAppId

- Method：POST
- Path：`/meta/ddl/views/{appId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/meta/MetaDdlController.java#L61-L65

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| appId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiMetaResult

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/meta/ddl/views/{appId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
