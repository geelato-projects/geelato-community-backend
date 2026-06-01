# SrvLogQueryController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srvlog.web.SrvLogQueryController
- BasePath：/srv-log
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| exceptions | GET | `/srv-log/exceptions` |
| recent | GET | `/srv-log/exceptions/recent` |

## 接口详情

### exceptions

- Method：GET
- Path：`/srv-log/exceptions`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srvlog/web/SrvLogQueryController.java#L27-L49

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
| methodKey | String | 否 |  |  |
| httpMethod | String | 否 |  |  |
| pathPattern | String | 否 |  |  |
| startTime | Long | 否 |  |  |
| endTime | Long | 否 |  |  |
| page | int | 否 | 1 |  |
| size | int | 否 | 20 |  |

#### Body
无

#### Response
- Java 返回类型：ApiPagedResult<List<SrvLogRecord>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/srv-log/exceptions" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### recent

- Method：GET
- Path：`/srv-log/exceptions/recent`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srvlog/web/SrvLogQueryController.java#L51-L60

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
| days | int |  |  |  |
| topN | int | 否 | 200 |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<List<SrvExceptionSummary>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/srv-log/exceptions/recent" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
