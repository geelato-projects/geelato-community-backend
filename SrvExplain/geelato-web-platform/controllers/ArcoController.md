# ArcoController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.arco.ArcoController
- BasePath：/arco
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/arco/ArcoController.java#L12-L26

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 根据枚举码获取选择项数据 | GET | `/arco/sod/{code}` |

## 接口详情

### 根据枚举码获取选择项数据

- Method：GET
- Path：`/arco/sod/{code}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/arco/ArcoController.java#L22-L25

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| code | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/arco/sod/{code}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
