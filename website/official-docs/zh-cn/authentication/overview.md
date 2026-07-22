---
title: 独立服务：统一认证中心
sidebar_label: 统一认证中心
---

# 独立服务：统一认证中心

> **说明：统一认证不属于框架底座的内置能力，而是一个独立的统一认证中心（Auth Server）服务。**
>
> 统一认证中心对外提供两种集成能力：
> 1. **标准 OAuth2 集成**
> 2. **轻量化 lite-login 集成**
>
> 本节说明内外部业务系统如何接入该统一认证中心，由认证中心集中提供用户登录与身份识别能力。

## 两种集成方式对比与选择

认证中心对外提供两种接入方式，外部业务系统可根据自身的技术栈和前端交互需求进行灵活选择：

| 维度 | 方式一：轻量化 lite-login 集成 | 方式二：标准 OAuth2 集成 |
| --- | --- | --- |
| **核心机制** | 认证中心提供现成的前端登录门面（`lite-login`），通过跨域 `postMessage` 机制向业务系统前端下发 token。 | 走标准的 OAuth2 授权码模式（Authorization Code Flow），通过后端服务器间的重定向交换 token。 |
| **前端交互** | 业务系统以 iframe 嵌入或新开窗口拉起 `lite-login` 页面，**无需**业务系统自己写登录 UI，用户体验无缝。 | 浏览器发生**全页重定向**，跳到认证中心的统一登录页，登录后再重定向回业务系统。 |
| **对接复杂度** | **极低**。纯前端对接为主，业务系统后端只需增加拦截器来验证拿到的 Bearer token 即可。 | **中等**。需要业务系统后端支持完整的 OAuth2 客户端协议栈能力。 |
| **适用场景** | 1. 现代的前后端分离架构 (Vue/React 等)<br/>2. 希望在业务系统内部直接弹出登录框（不离开当前页面）<br/>3. 纯前端 SPA 应用 | 1. **拥有独立后端的任意应用**<br/>2. 强安全要求，token 绝对不能暴露给浏览器前端<br/>3. 现有的外部系统已经内置了标准 OAuth2 Client 模块 |
| **如何接入** | 👉 [阅读 lite-login 业务系统接入指南](lite-login-integration.md) | 👉 [阅读标准 OAuth2 业务系统接入指南](oauth2-integration.md) |

## 统一认证解决什么问题

统一认证的核心目标是把“认证能力”和“业务系统自身会话”解耦：

- `auth-server` 负责独立、统一地签发 token，是唯一可信的认证源。
- `lite-login` 负责提供轻量的、可跨域嵌入的登录前端门面。
- 业务系统前端（第三方应用）负责拿到 token 并传给自己后端的业务服务。
- 业务系统后端负责拿着 token，向独立的认证中心确认用户真实身份。

这样，无论是新增门户、SaaS 还是客户自有系统，都不需要每个系统再去重复实现一套“用户名+密码”的底层认证流程，全部委托给独立的认证中心服务即可。

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

## 独立服务与框架能力的边界

独立认证服务（Auth Server）与 Geelato 框架本身的边界建议如下：

- **Auth Server（独立服务）**：解决“业务系统如何接入、如何拿到统一 token 并确认用户身份”的问题。
- **安全认证（框架能力）**：解决“业务系统拿到 token 后，如何在平台运行时消费 token 并建立当前请求的鉴权主体”的问题。
- **Runtime 链路（框架能力）**：解决“认证成功后如何在后端建立安全上下文”的问题。
- **MQL / ORM（框架能力）**：解决“身份确认之后如何访问业务数据”的问题。

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

1. [lite-login 业务系统接入指南](lite-login-integration.md)
2. [标准 OAuth2 业务系统接入指南](oauth2-integration.md)
3. 了解框架如何消费 Token，请看 [平台能力：认证鉴权](security-authentication.md)
4. 了解安全上下文，请看 [平台能力：SecurityContext 生命周期](../runtime/security-context-lifecycle.md)
