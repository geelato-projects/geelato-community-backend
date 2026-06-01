# ServiceController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.script.ServiceController
- BasePath：/service
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| exec | POST | `/service/exec/{scriptId}` |

## 接口详情

### exec

- Method：POST
- Path：`/service/exec/{scriptId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/script/ServiceController.java#L39-L68

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| scriptId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/service/exec/{scriptId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
