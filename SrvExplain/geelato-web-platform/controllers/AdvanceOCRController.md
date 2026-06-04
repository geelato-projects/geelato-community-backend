# AdvanceOCRController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.srv.ocr.AdvanceOCRController
- BasePath：/ocr/advance
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| resolve | POST | `/ocr/advance/resolve` |

## 接口详情

### resolve

- Method：POST
- Path：`/ocr/advance/resolve`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///D:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/srv/ocr/AdvanceOCRController.java#L60-L100

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
| file | MultipartFile |  |  |  |
| config | String | 否 |  |  |

#### Body
无

#### Response
- Java 返回类型：ApiResult<?>

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/ocr/advance/resolve" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
```
