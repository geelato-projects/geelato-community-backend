# DevTableImportController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.model.DevTableImportController
- BasePath：/model/table/import
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 新建导入（不包含外部ID） | POST | `/model/table/import/create` |
| 原样导入（保留外部ID） | POST | `/model/table/import/preserveId` |

## 接口详情

### 新建导入（不包含外部ID）

- Method：POST
- Path：`/model/table/import/create`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevTableImportController.java#L306-L349

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
| ddl | boolean |  | true |  |

#### Body
- Java 类型：String


#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/model/table/import/create" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 原样导入（保留外部ID）

- Method：POST
- Path：`/model/table/import/preserveId`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/model/DevTableImportController.java#L251-L295

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
| ddl | boolean |  | true |  |

#### Body
- Java 类型：String


#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/model/table/import/preserveId" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
