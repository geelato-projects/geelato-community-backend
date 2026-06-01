# DictionaryController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.base.DictionaryController
- BasePath：/dictionary
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 清除指定字典的缓存 | POST | `/dictionary/cache/clear` |
| 设置强制刷新缓存标志 | POST | `/dictionary/cache/refresh` |
| 根据字典编码获取字典项列表 | GET | `/dictionary/{code}` |

## 接口详情

### 清除指定字典的缓存

- Method：POST
- Path：`/dictionary/cache/clear`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/DictionaryController.java#L98-L115

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
| code | String | 否 |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<String>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/dictionary/cache/clear" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 设置强制刷新缓存标志

- Method：POST
- Path：`/dictionary/cache/refresh`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/DictionaryController.java#L85-L90

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
| refresh | boolean |  | true |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<String>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/dictionary/cache/refresh" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 根据字典编码获取字典项列表

- Method：GET
- Path：`/dictionary/{code}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/base/DictionaryController.java#L41-L77

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
- Java 返回类型：ApiResult<List<DictionaryItem>>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/dictionary/{code}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
