---
title: 机器对机器接入（client_credentials）
sidebar_label: 机器对机器接入
---

# 机器对机器接入（client_credentials）

如果您的场景**没有用户参与**——例如服务端之间的集成、定时任务、后台批处理，或者需要调用统一认证中心的**开放 API**（`/api/open/v1/**`）——无需走 OAuth2 重定向流程，可直接使用 `client_id` + `client_secret` 换取**机器令牌**（client_credentials 模式）。

## 适用场景

- 后端服务之间对接，不涉及任何用户登录交互。
- 定时任务、批处理等无人值守的程序化调用。
- 集成方通过**开放 API** 自助管理本系统的角色、用户、组织、权限点等数据（机器令牌是调用开放 API 的唯一合法凭证，用户令牌不可调用）。完整端点说明见 [开放管理 API](open-api-management.md)。

## 接入前提

集成前，需向统一认证中心管理员申请应用接入凭证：

- **`client_id`**：应用的唯一标识。
- **`client_secret`**：应用的安全秘钥，必须存放在服务端（环境变量、密钥管理服务等），**不可**写入前端代码或提交到代码仓库。

此外，客户端的**授权模式（grant types）必须包含 `client_credentials`**。新注册客户端默认包含该模式；若调用被拒绝并返回 403，请联系管理员确认客户端已启用且开通了该授权模式。

## 获取令牌

**请求方式**：`POST`
**请求地址**：`https://<auth-host>/oauth2/client_token`
**Content-Type**：`application/x-www-form-urlencoded`

**请求参数（Form 表单数据）**：
- `grant_type`：固定填 `client_credentials`
- `client_id`：申请获得的 `client_id`
- `client_secret`：申请获得的 `client_secret`

**示例**：

```bash
curl -X POST "https://<auth-host>/oauth2/client_token" \
  -d "grant_type=client_credentials" \
  -d "client_id=<your_client_id>" \
  -d "client_secret=<your_client_secret>"
```

**成功响应示例**：
```json
{
  "access_token": "eyJhbGciOiJIUzI1NiIs...",
  "client_token": "...",
  "token_type": "bearer",
  "expires_in": 7200
}
```

> **注意**：`client_credentials` 必须使用独立端点 `/oauth2/client_token`。标准的 `/oauth2/token` 端点**不支持**该授权模式。

## 使用令牌调用接口

后续请求在 Header 中携带 Bearer 令牌：

```bash
curl -H "Authorization: Bearer <access_token>" \
  "https://<auth-host>/api/open/v1/roles/page?pageNum=1&pageSize=10"
```

（兼容 `?access_token=` 查询参数方式，但推荐使用 Header。）

## 错误码

| HTTP 状态码 | 含义 |
| --- | --- |
| `401` | 缺少令牌、令牌无效或已过期。 |
| `403` | 携带的不是 client_credentials 机器令牌（用户令牌不能调开放 API），或客户端已停用 / 未开通 client_credentials 授权模式。 |

## 常见问题

- **为什么不能用 `/oauth2/token` 获取 client_credentials 令牌？**
  认证中心基于 sa-token 1.46+ 实现，`client_credentials` 是其内置的独立端点 `/oauth2/client_token`，标准 `/oauth2/token` 端点不支持该授权模式。

- **令牌过期了怎么办？**
  机器令牌不提供 refresh_token，过期后重新调用 `/oauth2/client_token` 获取即可。建议在代码中根据 `expires_in` 提前刷新并缓存令牌，而非每次请求都重新获取。

- **`client_secret` 不慎泄露了怎么办？**
  必须立即联系统一认证中心管理员重置秘钥。机器令牌代表调用方应用的身份，secret 泄露意味着第三方可以冒充您的应用调用开放 API。

- **机器令牌能当用户身份用吗？**
  不能。机器令牌的主体是 `client_id`（应用身份），仅用于开放 API 等机器对机器场景；需要识别具体登录用户的场景请使用 [OAuth2 授权码模式](oauth2-integration.md) 或 [lite-login 集成](lite-login-integration.md)。
