# PlatformLogSearchController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.run.PlatformLogSearchController
- BasePath：/run/log
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| search | GET | `/run/log/search` |
| searchByUserTime | GET | `/run/log/searchByUserTime` |

## 接口详情

### search

- Method：GET
- Path：`/run/log/search`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/run/PlatformLogSearchController.java#L22-L33

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
| tag | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/run/log/search" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### searchByUserTime

- Method：GET
- Path：`/run/log/searchByUserTime`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/run/PlatformLogSearchController.java#L35-L51

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
| userId | String | 否 |  |  |
| from | String | 否 |  |  |
| to | String | 否 |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/run/log/searchByUserTime" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
