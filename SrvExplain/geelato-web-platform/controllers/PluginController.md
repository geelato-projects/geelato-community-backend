# PluginController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.example.PluginController
- BasePath：/plugin
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| example | GET | `/plugin/example` |

## 接口详情

### example

- Method：GET
- Path：`/plugin/example`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/example/PluginController.java#L28-L36

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
  "{{baseUrl}}/plugin/example" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
