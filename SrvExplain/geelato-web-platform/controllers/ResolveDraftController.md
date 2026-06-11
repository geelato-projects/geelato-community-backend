# ResolveDraftController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.resolve.ResolveDraftController
- BasePath：/resolve/draft
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 创建解析草稿，返回标准化字段、匹配建议与步骤明细。 | POST | `/resolve/draft/` |
| 按 draftId 查询已生成的解析草稿。 | GET | `/resolve/draft/{draftId}` |
| 更新草稿中允许人工修正的字段和值。 | PUT | `/resolve/draft/{draftId}` |
| 确认草稿并触发后续持久化处理。 | POST | `/resolve/draft/{draftId}/confirm` |

## 接口详情

### 创建解析草稿，返回标准化字段、匹配建议与步骤明细。

- Method：POST
- Path：`/resolve/draft/`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/resolve/ResolveDraftController.java#L26-L34

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
| fileId | String | 否 |  |  |
| biztag | String | 否 |  |  |

#### Body
- Java 类型：MultipartFile
- 推导不确定：是


#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/resolve/draft/" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 按 draftId 查询已生成的解析草稿。

- Method：GET
- Path：`/resolve/draft/{draftId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/resolve/ResolveDraftController.java#L39-L42

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| draftId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/resolve/draft/{draftId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 更新草稿中允许人工修正的字段和值。

- Method：PUT
- Path：`/resolve/draft/{draftId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/resolve/ResolveDraftController.java#L47-L50

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| draftId | String | 是 |  |  |

#### Query 参数
无

#### Body
- Java 类型：ResolveDraftUpdateRequest


#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X PUT \
  "{{baseUrl}}/resolve/draft/{draftId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 确认草稿并触发后续持久化处理。

- Method：POST
- Path：`/resolve/draft/{draftId}/confirm`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/resolve/ResolveDraftController.java#L55-L58

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| draftId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/resolve/draft/{draftId}/confirm" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
