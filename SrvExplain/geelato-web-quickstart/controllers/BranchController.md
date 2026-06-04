# BranchController

## 基本信息
- 模块：geelato-web-quickstart
- Controller：cn.geelato.web.quickstart.BranchController
- BasePath：
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| branch | GET | `/branch` |

## 接口详情

### branch

- Method：GET
- Path：`/branch`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-quickstart/src/main/java/cn/geelato/web/quickstart/BranchController.java#L15-L27

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
- Java 返回类型：ApiResult<String>

#### curl 示例
```bash
curl -X GET \
  "{{baseUrl}}/branch" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
