# PackageController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.pack.PackageController
- BasePath：/package
- 条件装配：
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| deployPackage | GET | `/package/deploy/{versionId}` |
| downloadPackage | GET | `/package/downloadPackage/{versionId}` |
| packetMergeApp | GET,POST | `/package/packet/merge` |
| packetApp | GET,POST | `/package/packet/{appId}` |
| uploadPackage | POST | `/package/uploadPackage/{appId}` |

## 接口详情

### deployPackage

- Method：GET
- Path：`/package/deploy/{versionId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/pack/PackageController.java#L264-L308

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| versionId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/package/deploy/{versionId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### downloadPackage

- Method：GET
- Path：`/package/downloadPackage/{versionId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/pack/PackageController.java#L226-L243

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| versionId | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：void

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/package/downloadPackage/{versionId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### packetMergeApp

- Method：GET,POST
- Path：`/package/packet/merge`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/pack/PackageController.java#L160-L186

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
- Java 返回类型：ApiResult<AppVersion>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/package/packet/merge" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### packetApp

- Method：GET,POST
- Path：`/package/packet/{appId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/pack/PackageController.java#L97-L158

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| appId | String | 是 |  |  |

#### Query 参数
无

#### Body
- Java 类型：String
- 推导不确定：是


#### Response
- Java 返回类型：ApiResult<AppVersion>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/package/packet/{appId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### uploadPackage

- Method：POST
- Path：`/package/uploadPackage/{appId}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/pack/PackageController.java#L248-L259

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| appId | String | 是 |  |  |

#### Query 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| file | MultipartFile |  |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<AppVersion>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/package/uploadPackage/{appId}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
