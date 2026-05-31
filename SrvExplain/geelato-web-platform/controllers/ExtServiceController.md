# ExtServiceController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.script.ExtServiceController
- BasePath：/ext
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ExtServiceController.java#L34-L157

## 接口列表

| Summary | Method | Path |
|---|---|---|
| exec | POST,GET | `/ext/{outside_url}` |

## 接口详情

### exec

- Method：POST,GET
- Path：`/ext/{outside_url}`
- Produces：
- Consumes：
- 鉴权：无需 Authorization（@IgnoreVerify）
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ExtServiceController.java#L49-L102

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| outside_url | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：Object

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/ext/{outside_url}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
```
