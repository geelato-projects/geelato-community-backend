# OnlineUserController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.security.OnlineUserController
- BasePath：/online
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/OnlineUserController.java#L14-L37

## 接口列表

| Summary | Method | Path |
|---|---|---|
| list | GET | `/online/list` |

## 接口详情

### list

- Method：GET
- Path：`/online/list`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/OnlineUserController.java#L24-L36

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
| windowMinutes | Integer | 否 |  |  |
| limit | Integer | 否 |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/online/list" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
