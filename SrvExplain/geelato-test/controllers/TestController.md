# TestController

## 基本信息
- 模块：geelato-test
- Controller：cn.geelato.test.controller.TestController
- BasePath：/api/test
- 条件装配：
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 获取所有测试接口清单 | GET | `/api/test/apis` |
| 获取指定类的测试接口清单 | GET | `/api/test/apis/class` |
| 执行指定类和方法的测试 | GET | `/api/test/execute` |
| 执行所有测试 | GET | `/api/test/execute/all` |
| 执行指定类的所有测试 | GET | `/api/test/execute/class` |
| 执行指定包的所有测试 | GET | `/api/test/execute/package` |

## 接口详情

### 获取所有测试接口清单

- Method：GET
- Path：`/api/test/apis`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/TestController.java#L85-L89

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
- Java 返回类型：List<TestApiInfo>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/apis" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 获取指定类的测试接口清单

- Method：GET
- Path：`/api/test/apis/class`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/TestController.java#L97-L101

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
| className | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：List<TestApiInfo>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/apis/class" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 执行指定类和方法的测试

- Method：GET
- Path：`/api/test/execute`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/TestController.java#L39-L43

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
| className | String |  |  |  |
| methodName | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：TestResult

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/execute" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 执行所有测试

- Method：GET
- Path：`/api/test/execute/all`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/TestController.java#L74-L78

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
- Java 返回类型：List<TestResult>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/execute/all" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 执行指定类的所有测试

- Method：GET
- Path：`/api/test/execute/class`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/TestController.java#L51-L55

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
| className | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：List<TestResult>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/execute/class" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```

### 执行指定包的所有测试

- Method：GET
- Path：`/api/test/execute/package`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-test/src/main/java/cn/geelato/test/controller/TestController.java#L63-L67

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
| packageName | String |  |  |  |

#### Body
无

#### Response
- Java 返回类型：List<TestResult>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/api/test/execute/package" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
