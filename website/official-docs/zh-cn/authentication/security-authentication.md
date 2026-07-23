---
title: 认证鉴权
sidebar_label: 认证鉴权
---
# 认证鉴权

本页说明平台运行时内部执行鉴权的入口：

- `DefaultSecurityInterceptor`

它不是“统一认证中心”的整体架构说明，而是“一个请求进入平台后，后端到底如何完成认证、建立安全上下文并放行”的运行时链路说明。

## 它解决什么问题

`DefaultSecurityInterceptor` 的职责不是签发 token，而是消费请求里已经带来的认证凭证，并在当前请求中建立：

- 当前用户
- 当前租户
- 当前密码或认证凭证上下文
- Shiro `Subject`
- 在线用户活跃状态
- 请求级流量染色上下文

也就是说：

- 统一认证章节解决“token 从哪里来”
- 本章节解决“后端拿到 token 后如何完成鉴权”

## 所在位置

当前运行时的主要鉴权拦截器位于：

- `geelato-web-common`
- `cn.geelato.web.common.interceptor.DefaultSecurityInterceptor`

它实现的是 Spring MVC 的：

- `HandlerInterceptor`

因此它工作在控制器执行之前，而不是 Servlet Filter 层直接注入主体。

## 整体主链路

一个请求进入平台后，`DefaultSecurityInterceptor` 的执行顺序可以概括为：

1. 先做流量染色
2. 判断当前接口是否跳过鉴权
3. 读取 `Authorization`
4. 尝试从本地缓存恢复用户上下文
5. 按顺序尝试多种认证方式
6. 认证成功后建立 `SecurityContext`
7. 更新在线用户活跃状态
8. 请求结束后清理请求级上下文

## 第 1 步：先做流量染色

在 `preHandle()` 一开始，拦截器会先执行：

- `applyTrafficTag(request, response)`

这一步发生在 `@IgnoreVerify` 判断之前，目的是保证即使某些接口不需要鉴权，也仍然能够拿到统一的：

- `trafficTag`
- MDC 日志上下文
- 请求级流量标记

这里的设计原则是：

- 染色失败只降级，不中断后续鉴权主流程

## 第 2 步：判断是否跳过鉴权

如果当前处理器不是 `HandlerMethod`，或者方法上标记了：

- `@IgnoreVerify`

则直接放行：

- `return true`

这类接口可以不进入认证逻辑，但并不影响上一步已经完成的流量染色。

## 第 3 步：读取 Authorization

对需要鉴权的请求，拦截器从 Header 中读取：

- `Authorization`

如果该请求头不存在，则直接抛出：

- `UnauthorizedException`

这意味着当前平台运行时把 `Authorization` 视为统一认证入口，而不是从业务参数或普通 Filter 中拼装主体。

## 第 4 步：优先尝试缓存恢复

在进入各类认证分支前，拦截器会先调用：

- `tryRestoreFromCache(token, request, response)`

这里使用的核心缓存是：

- `tokenContextCache`

它缓存的是：

- 用户
- 租户
- 当前密码
- 认证 token 对象
- 过期时间

命中缓存后，会直接恢复：

- `SecurityContext.setCurrentUser(...)`
- `SecurityContext.setCurrentTenant(...)`
- `SecurityContext.setCurrentPassword(...)`
- `Subject.login(...)`

然后继续执行：

- 认证后流量标记处理
- 在线用户触达更新

这样做的目的，是避免同一 token 在短时间内频繁重复解析和远程校验。

## 第 5 步：按顺序尝试多种认证方式

如果缓存没有命中，拦截器会按固定顺序尝试以下认证分支。

### 5.1 Anonymous 认证

前缀：

- `Authorization: Anonymous <token>`

处理逻辑：

- 校验匿名 JWT
- 读取 `loginName`、`orgId`、`tenantCode`
- 初始化当前用户
- 建立匿名密码上下文
- 构造 `UsernamePasswordToken`
- 调用 Shiro 登录

适用场景通常是：

- 平台内部受控匿名访问
- 已知匿名主体的轻量鉴权场景

### 5.2 JWTBearer 认证

前缀：

- `Authorization: JWTBearer <token>`

处理逻辑：

- 校验 JWT
- 读取 `loginName`、`passWord`、`orgId`、`tenantCode`
- 初始化当前用户
- 回填组织、部门、业务单元信息
- 写入 `SecurityContext`
- 构造 `UsernamePasswordToken`
- 调用 Shiro 登录

这是最典型的“本地 JWT 令牌换运行时安全上下文”的链路。

### 5.3 扩展键认证

当前支持两类扩展键：

- `WeixinUnionId`
- `WeixinWorkUserId`

前缀分别是：

- `Authorization: WeixinUnionId <key>`
- `Authorization: WeixinWorkUserId <key>`

处理逻辑：

- 通过 `UserProvider` 根据扩展键查询本地用户
- 初始化平台当前用户
- 写入 `SecurityContext`
- 构造对应的 Shiro `AuthenticationToken`
- 调用 Shiro 登录

这条链路适合：

- 企业微信
- 微信 UnionId
- 其他外部身份映射到本地用户体系的场景

### 5.4 OAuth2 Bearer 认证

前缀：

- `Authorization: Bearer <token>`

这是统一认证中心最关键的对接链路。

处理步骤分为两层缓存：

1. 先看 `tokenContextCache` 是否已有完整认证上下文
2. 再看 `tokenUserCache` 是否已有 OAuth2 用户信息
3. 如果都没有，再通过 `OAuth2Helper.getUserInfo(...)` 向认证中心取用户信息

拿到用户后，会执行：

- 初始化本地当前用户
- 设置当前租户
- 构造 `OAuth2Token`
- 调用 Shiro 登录
- 写入上下文缓存

因此这条链路实现的是：

- 后端消费统一认证中心下发的 Bearer token
- 再把统一身份落到当前平台运行时的安全上下文

## 第 6 步：认证成功后做什么

无论命中哪条认证分支，只要成功，拦截器都会完成几类后处理。

### 建立 SecurityContext

成功后会设置：

- `SecurityContext.setCurrentUser(...)`
- `SecurityContext.setCurrentTenant(...)`
- `SecurityContext.setCurrentPassword(...)`

这是当前业务代码读取用户、租户和密码上下文的主要来源。

### 执行 Shiro 登录

拦截器还会调用：

- `SecurityUtils.getSubject().login(...)`

也就是说，平台当前并不是只维护一个自定义线程上下文，而是同时把认证结果接入到 Shiro 的 `Subject` 体系里。

### 应用认证后流量标记

在用户明确识别出来后，还会调用：

- `applyTrafficTagAfterAuthenticated(...)`

这让流量染色策略可以在“已识别用户”的条件下继续细化。

### 更新在线用户状态

最后会调用：

- `touchOnline(user, request)`

如果配置了 `OnlineUserTracker`，就会把当前用户的最近活跃时间和请求信息写入在线状态跟踪体系。

## 第 7 步：认证失败如何处理

如果所有认证分支都失败，最终会抛出：

- `UnauthorizedException("未授权访问")`

因此从运行时语义上看，失败出口只有两类：

- 没有 `Authorization`
- 有 `Authorization`，但所有认证方式都无法识别

## 第 8 步：请求结束后的清理

在 `afterCompletion()` 中，拦截器会清理：

- 流量染色对应的 MDC key
- `TrafficTagContext`

这里的关键点是：

- 认证主体的清理不在这个拦截器里完成
- `SecurityContext` 的统一清理由更外层的 `SecurityContextFilter` 负责

这也符合当前框架的安全边界：

- Filter 负责兜底清理
- Interceptor 负责鉴权成功后的主体建立

## 当前链路中的几个关键对象

### `OrgProvider`

用于补齐：

- 组织
- 部门
- 业务单元

这使得当前用户不仅有登录身份，还有组织维度上下文。

如果你需要替换默认组织来源或组织关系解析方式，请继续阅读：

- [安全 Provider 扩展](../reference/security-provider-extension.md)

### `UserProvider`

用于按外部扩展键查询本地用户，主要服务于：

- `WeixinUnionId`
- `WeixinWorkUserId`

这类非标准用户名密码认证入口。

如果你需要替换默认用户来源或扩展键映射方式，请继续阅读：

- [安全 Provider 扩展](../reference/security-provider-extension.md)

### `OnlineUserTracker`

用于记录在线状态，帮助管理端做：

- 在线用户展示
- 最近活跃观察

### `TrafficTagResolver`

用于在请求进入和认证完成后分别执行流量标记处理。

## 设计结论

`DefaultSecurityInterceptor` 当前体现的是一条明确的运行时鉴权链路：

- 先染色
- 再放行免鉴权接口
- 再读取统一认证头
- 再优先走缓存恢复
- 然后按匿名、JWT、本地扩展键、OAuth2 Bearer 顺序完成认证
- 认证成功后建立 `SecurityContext` 与 Shiro `Subject`
- 最后更新在线状态并在请求结束时清理请求级上下文

这条链路把“认证凭证消费”“运行时主体建立”“在线态更新”“流量标记”收敛在同一个拦截器入口中，但又仍然保持：

- `SecurityContext` 只能在鉴权成功后由安全链路内部设置
- Filter 不负责直接信任前端主体注入
- 统一认证中心与运行时鉴权链路职责分离

## 推荐继续阅读

- [统一认证](overview.md)
- [平台能力：SecurityContext 生命周期](../runtime/security-context-lifecycle.md)
- [安全 Provider 扩展](../reference/security-provider-extension.md)
