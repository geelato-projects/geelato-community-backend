# PluginRuntimeController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.example.PluginRuntimeController
- BasePath：/plugin
- 条件装配：runtime（@ApiRuntimeRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/example/PluginRuntimeController.java#L13-L33

## 接口列表

| Summary | Method | Path |
|---|---|---|
| example | GET | `/plugin/example2` |
| example3 | GET | `/plugin/example3` |

## 接口详情

### example

- Method：GET
- Path：`/plugin/example2`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/example/PluginRuntimeController.java#L22-L27

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
- Java 返回类型：ApiResult<String>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/plugin/example2" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### example3

- Method：GET
- Path：`/plugin/example3`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/example/PluginRuntimeController.java#L29-L32

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
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/plugin/example3" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
