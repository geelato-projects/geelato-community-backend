# AccountController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.security.AccountController
- BasePath：/sys/account
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| create | POST | `/sys/account/` |

## 接口详情

### create

- Method：POST
- Path：`/sys/account/`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/security/AccountController.java#L25-L29

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
- Java 类型：User


#### Response
- Java 返回类型：ApiResult<NullResult>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/sys/account/" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
