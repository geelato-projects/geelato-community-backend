# CompanyController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.company.CompanyController
- BasePath：/company
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 保存公司信息 | POST | `/company/createOrUpdate` |
| 根据ID删除公司信息 | DELETE,GET | `/company/delete/{id}` |
| 根据ID获取公司信息 | GET | `/company/get/{id}` |
| 分页查询公司列表 | POST | `/company/pageQuery` |

## 接口详情

### 保存公司信息

- Method：POST
- Path：`/company/createOrUpdate`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/company/CompanyController.java#L51-L75

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
- Java 类型：Company


#### Response
- Java 返回类型：ApiResult<String>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/company/createOrUpdate" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```

### 根据ID删除公司信息

- Method：DELETE,GET
- Path：`/company/delete/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/company/CompanyController.java#L105-L118

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<String>

#### curl 示例
```bash
curl -X DELETE \
  "{{baseUrl}}/company/delete/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 根据ID获取公司信息

- Method：GET
- Path：`/company/get/{id}`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/company/CompanyController.java#L83-L96

#### Header
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| App-Id | String | 否 |  |  |
| Tenant-Code | String | 否 |  |  |
| Authorization | String | 否 |  |  |

#### Path 参数
| 名称 | Java 类型 | 必填 | 默认值 | 说明 |
|---|---|---|---|---|
| id | String | 是 |  |  |

#### Query 参数
无

#### Body
无

#### Response
- Java 返回类型：ApiResult<Company>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/company/get/{id}" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 分页查询公司列表

- Method：POST
- Path：`/company/pageQuery`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/company/CompanyController.java#L30-L42

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
- Java 返回类型：ApiPagedResult<List<Company>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/company/pageQuery" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
