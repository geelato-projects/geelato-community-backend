# AuxiliarySuiteHealthController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.base.AuxiliarySuiteHealthController
- BasePath：/system/monitor/auxiliary-suites
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| health | GET | `/system/monitor/auxiliary-suites/health` |
| refresh | POST,GET | `/system/monitor/auxiliary-suites/refresh` |

## 接口详情

### health

- Method：GET
- Path：`/system/monitor/auxiliary-suites/health`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/AuxiliarySuiteHealthController.java#L21-L32

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
  "{{baseUrl}}/system/monitor/auxiliary-suites/health" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### refresh

- Method：POST,GET
- Path：`/system/monitor/auxiliary-suites/refresh`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/AuxiliarySuiteHealthController.java#L34-L45

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
curl -X POST \
  "{{baseUrl}}/system/monitor/auxiliary-suites/refresh" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
