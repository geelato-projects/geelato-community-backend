# 统一认证

这一章说明 Geelato Framework 当前统一认证能力的接入边界，重点覆盖 `auth-server` 与 `lite-login` 的职责划分，以及第三方应用如何复用统一认证中心。

## 统一认证解决什么问题

统一认证的核心目标是把“认证能力”和“业务系统自身会话”解耦：

- `auth-server` 负责统一签发 token
- `lite-login` 负责提供轻量登录门面
- 第三方应用前端负责拿到 token 并传给自己后端
- 第三方应用后端负责基于 token 向认证中心确认用户身份

这样新增任意一个门户、SaaS、客户自有系统时，不需要每个系统都自己实现一套用户名密码登录流程。

## 核心组件

### `auth-server`

`auth-server` 是统一认证中心，也是唯一可信的 token 签发方。

它负责：

- 签发访问令牌
- 暴露 `/oauth2/userinfo` 等 OAuth 能力
- 作为第三方应用后端确认用户身份的可信来源

### `lite-login`

`lite-login` 是统一认证中心对外提供的轻量登录门面。

它负责：

- 承载登录交互
- 支持 iframe 或窗口方式嵌入第三方应用
- 通过 `postMessage` 把登录结果回传给第三方应用前端

## 适用范围

推荐用于：

- 第三方门户
- 第三方 SaaS
- 客户自有系统
- 需要嵌入统一登录页的前后端分离应用
- 独立部署但希望复用统一认证中心的外部系统

不适用于：

- 平台内部 `admin-sso` 原始登录页承载模式
- `auth-server templates` 内直接承载的传统一体化登录页

## 职责边界

### 认证中心职责

- `auth-server` 是唯一 token 签发方
- `lite-login` 是轻量登录门面
- 登录成功后由 `lite-login` 回传 token
- 用户真实身份最终仍以后端调用 `/oauth2/userinfo` 的结果为准

### 第三方应用前端职责

- 打开或嵌入 `lite-login`
- 监听 `postMessage`
- 提取 `accessToken` 或 `token`
- 暂存 token
- 在调用自己后端时附带 `Authorization: Bearer <token>`

### 第三方应用后端职责

- 从请求头读取 Bearer token
- 调用认证中心 `/oauth2/userinfo`
- 从返回结果中提取 `data.user`
- 建立本系统自己的账号映射、权限上下文或本地会话

## 与框架其他能力的关系

统一认证与其他章节的边界建议如下：

- 统一认证：解决“如何拿到认证中心 token 并确认身份”
- 安全认证：解决“平台运行时如何消费 token 并建立当前请求的认证主体”
- Runtime 安全链路：解决“认证成功后如何在后端建立安全上下文”
- MQL / ORM：解决“身份确认之后如何访问业务数据”

需要特别注意：

- 前端从 `LOGIN_SUCCESS` 里拿到的 `user` 只能作为展示辅助信息
- 最终可信身份必须以后端确认结果为准
- `SecurityContext` 只能在鉴权成功后由安全链路内部设置

## 统一入口约束

推荐统一使用：

```text
https://<auth-host>/lite-login
```

不要再复用：

```text
/login?display=embedded
```

每个第三方应用仍建议保留自己的 `/login` 页面，但它只作为承接页，不自己做用户名密码认证。

## 推荐阅读顺序

1. 先看 [统一认证中心架构设计](architecture.md)
2. 再看 [安全认证](security-authentication.md)，理解 `DefaultSecurityInterceptor` 的鉴权链路
3. 再看 [lite-login 第三方应用接入](lite-login-integration.md)
4. 再看 [PlatformWebRuntime](../runtime/platform-web-runtime.md)，理解运行时安全链路位置
5. 如需平台协议层数据访问，再看 [MQL 总览](../mql/overview.md)
