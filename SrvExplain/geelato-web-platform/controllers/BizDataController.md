# BizDataController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.platform.BizDataController
- BasePath：/bizData
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| 批量变更数据归属者（creator / creatorName） | POST | `/bizData/changeOwner` |

## 接口详情

### 批量变更数据归属者（creator / creatorName）

- Method：POST
- Path：`/bizData/changeOwner`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/platform/BizDataController.java#L48-L209

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
| owner | String |  |  |  |

#### Body
- Java 类型：Map<String,List<String>>


#### Response
- Java 返回类型：ApiResult<Map<String,Integer>>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/bizData/changeOwner" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
