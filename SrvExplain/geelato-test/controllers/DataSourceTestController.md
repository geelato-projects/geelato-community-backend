# DataSourceTestController

## 基本信息
- 模块：geelato-test
- Controller：cn.geelato.test.controller.DataSourceTestController
- BasePath：/api/test/datasource
- 条件装配：
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 获取所有数据源信息 | GET | `/api/test/datasource/list` |
| 测试指定数据源的连接 | GET | `/api/test/datasource/test` |
| 测试所有数据源的连接 | GET | `/api/test/datasource/test/all` |

## 接口详情

### 获取所有数据源信息

- Method：GET
- Path：`/api/test/datasource/list`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/DataSourceTestController.java#L28-L32

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
- Java 返回类型：List<Map<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/datasource/list" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 测试指定数据源的连接

- Method：GET
- Path：`/api/test/datasource/test`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/DataSourceTestController.java#L40-L44

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
| dataSourceName | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：Map<String,Object>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/datasource/test" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 测试所有数据源的连接

- Method：GET
- Path：`/api/test/datasource/test/all`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/DataSourceTestController.java#L51-L55

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
- Java 返回类型：List<Map<String,Object>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/datasource/test/all" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
