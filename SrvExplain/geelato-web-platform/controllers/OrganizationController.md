# OrganizationController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.base.OrganizationController
- BasePath：/org
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/OrganizationController.java#L18-L59

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 根据组织ID获取组织数据 | GET | `/org/{orgId}` |

## 接口详情

### 根据组织ID获取组织数据

- Method：GET
- Path：`/org/{orgId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/OrganizationController.java#L31-L58

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| orgId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：Map<String,Object>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/org/{orgId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
