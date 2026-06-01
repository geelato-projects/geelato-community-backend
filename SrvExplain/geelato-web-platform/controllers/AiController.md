# AiController

## 基本信息
- 模块：geelato-web-platform
- Controller：cn.geelato.web.platform.ai.AiController
- BasePath：/ai
- 条件装配：designtime（@ApiRestController）
- 分类：

## 接口列表

| Summary | Method | Path |
|---|---|---|
| ask | POST | `/ai/ask` |

## 接口详情

### ask

- Method：POST
- Path：`/ai/ask`
- Produces：
- Consumes：
- 鉴权：需要 Authorization
- 源码：file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-platform/src/main/java/cn/geelato/web/platform/ai/AiController.java#L19-L23

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
- Java 类型：Map<String,String>


#### Response
- Java 返回类型：SseEmitter

#### curl 示例
```bash
curl -X POST \
  "{{baseUrl}}/ai/ask" \
  -H "App-Id: {{appId}}" \
  -H "Tenant-Code: {{tenantCode}}" \
  -H "Authorization: {{authorization}}" \
  -H "Content-Type: application/json" \
  --data '{{body}}'
```
