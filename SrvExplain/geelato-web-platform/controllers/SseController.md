# SseController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.sse.SseController
- BasePath：/subscribe
- 条件装配：
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| all | GET | `/subscribe/topic/all` |
| subscribe | GET | `/subscribe/{topic}` |

## 接口详情

### all

- Method：GET
- Path：`/subscribe/topic/all`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/sse/SseController.java#L24-L27

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
- Java 返回类型：SseEmitter

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/subscribe/topic/all" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### subscribe

- Method：GET
- Path：`/subscribe/{topic}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/sse/SseController.java#L16-L22

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| topic | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：SseEmitter

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/subscribe/{topic}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
