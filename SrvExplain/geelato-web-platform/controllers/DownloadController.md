# DownloadController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.base.DownloadController
- BasePath：/resources
- 条件装配：designtime（@ApiRestController）
- 分类：
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/DownloadController.java#L29-L83

## 接口列表

| Summary | Method | Path |
|---|---|---|
| downloadFile | GET | `/resources/file` |
| downloadJson | GET | `/resources/json` |

## 接口详情

### downloadFile

- Method：GET
- Path：`/resources/file`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/DownloadController.java#L47-L50

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
- Java 类型：String
- 推导不确定：是


#### Response
- Java 返回类型：void

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/resources/file" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### downloadJson

- Method：GET
- Path：`/resources/json`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/DownloadController.java#L52-L82

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
- Java 类型：String
- 推导不确定：是


#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/resources/json" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
