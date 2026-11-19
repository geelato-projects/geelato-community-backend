---
title: 认证鉴权
sidebar_label: 认证鉴权
---
# 认证鉴权

本页说明平台运行时内部执行鉴权的入口：

- `DefaultSecurityInterceptor`

它不是“统一认证中心”的整体架构说明，而是“一个请求进入平台后，后端到底如何完成认证、建立安全上下文并放行”的运行时链路说明。

拦截器本身不认识任何具体认证方案：所有认证方式（`JWTBearer`、`Anonymous`、扩展键、`SystemToken`、`Bearer`、`WeixinCode`）都是通过 `TokenAuthenticator` SPI 接入的，拦截器按 `Authorization` 前缀路由到对应认证器，并统一负责认证成功后的会话建立。新增认证方式的接入方式见[认证器扩展（SPI）](authenticator-spi.md)。

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
- 本章节解决“后端获取 token 后如何完成鉴权”

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
4. 按 `Authorization` 前缀经注册表路由到对应的认证器（`TokenAuthenticator`）
5. 执行认证器的方案级门禁（`preAuthCheck`，如 `SystemToken` 的 `@AllowSystemAccess` 校验）
6. 尝试从本地缓存恢复用户上下文，未命中才执行认证
7. 认证成功后统一建立 `SecurityContext` 与 Shiro 会话
8. 应用委托代办、更新在线用户活跃状态
9. 请求结束后清理请求级上下文

## 第 1 步：先做流量染色

在 `preHandle()` 一开始，拦截器会先执行：

- `applyTrafficTag(request, response)`

这一步发生在 `@IgnoreVerify` 判断之前，目的是保证即使某些接口不需要鉴权，也仍然能够获得统一的：

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

## 第 4 步：方案门禁与缓存恢复

拿到 `Authorization` 后，拦截器先按前缀路由：

- `TokenAuthenticatorRegistry.resolve(token)` 找到唯一的认证器
- 请求头携带了注册表不认识的前缀，直接抛 `UnauthorizedException`，不做任何兜底

路由成功后，先执行认证器的方案级门禁：

- `authenticator.preAuthCheck(...)`

门禁**必须放在缓存还原之前**：缓存命中会短路认证流程，若门禁放在认证里，已被缓存过的凭证就能借缓存绕过限制。典型例子是 `SystemToken` 方案对 `@AllowSystemAccess` 注解的校验。

门禁通过后，才尝试缓存恢复：

- `tryRestoreFromCache(token, request, response)`

这里使用的核心缓存是：

- `tokenContextCache`

它缓存的是：

- 用户
- 租户
- 当前密码
- 认证 token 对象

命中缓存后，会直接恢复：

- `SecurityContext.setCurrentUser(...)`
- `SecurityContext.setCurrentTenant(...)`
- `SecurityContext.setCurrentPassword(...)`
- `Subject.login(...)`

然后继续执行：

- 认证后流量标记处理
- 在线用户触达更新

这样做的目的，是避免同一 token 在短时间内频繁重复解析和远程校验。若缓存的凭证已失效（如用户改密后旧 token 无法通过 Shiro 校验），缓存会被清理并落回完整认证链路。

## 第 5 步：执行对应认证器的认证

缓存未命中时，拦截器调用路由到的认证器完成「凭证校验 + 用户解析」：

- `authenticator.authenticate(...)`

认证器返回统一的 `TokenAuthentication`（用户 + 凭证 + 会话建立所需的凭证描述），剩余步骤由拦截器统一完成。当前平台内置以下认证方案：

| 前缀 | 认证器 | 说明 |
|---|---|---|
| `JWTBearer <token>` | `JwtTokenAuthenticator` | 平台自签 JWT，本地验签 |
| `Anonymous <token>` | `JwtTokenAuthenticator` | 匿名 JWT，多一道匿名口令 claim 校验 |
| `WeixinUnionId <key>` | `WeixinTokenAuthenticator` | 微信 UnionId 扩展键 |
| `WeixinWorkUserId <key>` | `WeixinTokenAuthenticator` | 企业微信 userId 扩展键 |
| `SystemToken <key>` | `SystemTokenAuthenticator` | 外部系统固定令牌，仅限 `@AllowSystemAccess` 接口 |
| `Bearer <token>` | `OAuth2TokenAuthenticator` | 统一认证中心 OAuth2 token |
| `WeixinCode <code>` | `WeixinCodeTokenAuthenticator` | 微信小程序登录凭证（备用通道） |

### 5.1 Anonymous 认证

前缀：

- `Authorization: Anonymous <token>`

由 `JwtTokenAuthenticator` 处理（与 `JWTBearer` 同一认证器，共用验签与用户装载逻辑）：

- 校验匿名 JWT
- 校验 `anonymous` claim 与全局匿名密码一致
- 读取 `loginName`、`orgId`、`tenantCode`，装载当前用户
- 以匿名固定密码作为凭证返回，由拦截器建立会话

适用场景通常是：

- 平台内部受控匿名访问
- 已知匿名主体的轻量鉴权场景

匿名登录通道的开启与关闭由全局上下文决定：环境变量 `GEELATO_ANONYMOUS_ENABLED`（true/false）显式配置优先，未设置时按系统密级判定（密级 < 10 开启，≥ 10 关闭），详见[全局上下文](../platform-capabilities/global-context)。

### 5.2 JWTBearer 认证

前缀：

- `Authorization: JWTBearer <token>`

由 `JwtTokenAuthenticator` 处理：

- 本地验签（HMAC256 + payload 混淆，与平台登录端点签发的 token 同构，见 `cn.geelato.web.common.security.JWTUtil`）
- 读取 `loginName`、`passWord`、`orgId`、`tenantCode`
- 装载当前用户并回填组织、部门、业务单元信息
- 以 `loginName/passWord` 凭证描述返回，由拦截器建立会话

这是最典型的“本地 JWT 令牌换运行时安全上下文”的链路。

### 5.3 扩展键认证

当前支持两类扩展键：

- `WeixinUnionId`
- `WeixinWorkUserId`

前缀分别是：

- `Authorization: WeixinUnionId <key>`
- `Authorization: WeixinWorkUserId <key>`

由 `WeixinTokenAuthenticator` 处理：

- 通过 `UserProvider.getUserByExtendKey(key, type)` 按扩展键查询本地用户
- 装载平台当前用户
- 按扩展键类型返回凭证描述，由拦截器路由到对应的 Shiro Realm

这条链路适合：

- 企业微信
- 微信 UnionId
- 其他外部身份映射到本地用户体系的场景

### 5.4 SystemToken 认证

前缀：

- `Authorization: SystemToken <key>`

由 `SystemTokenAuthenticator` 处理，适用于给不具备用户 token 的外部系统调用的接口（如消息中心发送站内信）：

- 方案级门禁（`preAuthCheck`）：仅标注了 `@AllowSystemAccess` 注解的接口允许使用，否则 401
- 与配置的固定密钥常量时间比对
- 认证通过后以虚拟系统主体运行（`userId=system`，`systemPrincipal=true`），不关联平台用户、不计入在线用户

### 5.5 OAuth2 Bearer 认证

前缀：

- `Authorization: Bearer <token>`

这是统一认证中心最关键的对接链路，由 `OAuth2TokenAuthenticator` 处理。

处理步骤分为两层缓存：

1. 认证器内部维护 `tokenUserCache`（token → 认证中心用户信息）
2. 缓存未命中时，通过 `OAuth2Helper.getUserInfo(...)` 向认证中心取用户信息

获取用户后：

- 初始化本地当前用户并补全组织信息
- 返回认证结果，由拦截器建立会话并写入上下文缓存

因此这条链路实现的是：

- 后端消费统一认证中心下发的 Bearer token
- 再把统一身份落到当前平台运行时的安全上下文

### 5.6 WeixinCode 认证

前缀：

- `Authorization: WeixinCode <code>`

由 `WeixinCodeTokenAuthenticator` 处理，是微信小程序的备用鉴权通道（`code` 为 `wx.login()` 返回的一次性登录凭证，5 分钟有效）：

- 平台侧维护 code → unionid 缓存（5 分钟，覆盖前端超时重试复用同一 code 的场景）
- 缓存未命中时由宿主实现的 `WeixinCodeResolver` SPI 调用微信 `jscode2session` 换取 unionid
- 用 unionid 走 `WeixinUnionId` 扩展键链路反查平台用户；未绑定或账号不可用返回 401（提示先绑定工作台账号）

该通道每请求（缓存未命中时）需调用一次微信接口，定位为降级/兼容通道，推荐业务使用 `JWTBearer` token。宿主未提供 `WeixinCodeResolver` 实现时，该通道统一 401。详见[认证器扩展（SPI）](authenticator-spi.md)的实战示例。

## 第 6 步：认证成功后做什么

无论哪个认证器返回成功，拦截器都会执行同一套统一会话建立流程。

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

失败出口统一为 `UnauthorizedException`（HTTP 401），但按根因分为三类：

- 没有 `Authorization` 头
- `Authorization` 头的前缀注册表无法识别（未注册的方案，或方案通道被宿主关闭）
- 方案门禁未通过、凭证校验失败或 Shiro 会话建立失败——这类失败会携带根因（如“匿名口令校验失败”、“用户不存在”、“微信身份未绑定平台账号”），服务端日志可定位到具体环节，前端语义不变（统一 401 → 引导重新认证）

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

若需替换默认组织来源或组织关系解析方式，请继续阅读：

- [Provider 契约](../security/provider.md)

### `UserProvider`

用于按外部扩展键查询本地用户，主要服务于：

- `WeixinUnionId`
- `WeixinWorkUserId`
- `WeixinCode`（微信小程序备用通道：code 换取的 unionid 复用 `WeixinUnionId` 扩展键链路反查用户）

这类非标准用户名密码认证入口。

若需替换默认用户来源或扩展键映射方式，请继续阅读：

- [Provider 契约](../security/provider.md)

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
- 按前缀经注册表路由到唯一认证器，先执行方案级门禁，再优先走缓存恢复，未命中才执行认证
- 认证成功后统一建立 `SecurityContext` 与 Shiro `Subject`
- 最后应用委托代办、更新在线状态，并在请求结束时清理请求级上下文

这条链路把“认证凭证消费”“运行时主体建立”“在线态更新”“流量标记”收敛在同一个拦截器入口中，但又仍然保持：

- 认证方案全部通过 `TokenAuthenticator` SPI 接入，新增认证方式不需要改动拦截器
- `SecurityContext` 只能在鉴权成功后由安全链路内部设置
- Filter 不负责直接信任前端主体注入
- 统一认证中心与运行时鉴权链路职责分离

如何新增一种认证方案，见：

- [认证器扩展（SPI）](authenticator-spi.md)

## 推荐继续阅读

- [认证器扩展（SPI）](authenticator-spi.md)
- [统一认证](overview.md)
- [安全契约：安全上下文](../security/context.md)
- [Provider 契约](../security/provider.md)
