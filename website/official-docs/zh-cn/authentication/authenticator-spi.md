---
title: 认证器扩展（SPI）
sidebar_label: 认证器扩展
---
# 认证器扩展（SPI）

本页说明如何为平台运行时鉴权链路扩展一种新的认证方案（鉴权器）。

平台的运行时鉴权入口是 `DefaultSecurityInterceptor`，它本身不认识任何具体的认证方案。所有认证方案——`JWTBearer`、`Anonymous`、`WeixinUnionId`、`WeixinWorkUserId`、`SystemToken`、`Bearer`（OAuth2）、`WeixinCode`——都是通过同一个 SPI 接口 `TokenAuthenticator` 接入的。新增一种认证方式，不需要改动拦截器，只需要实现接口并注册为 Spring Bean。

## 适用场景判断

若需求属于以下情况，阅读本页：

- 想让平台识别一种新的 `Authorization` 头前缀（如 `DingtalkCode <code>`、`ApiKey <key>`）
- 想接入一个新的外部身份来源（钉钉扫码、第三方 OAuth、设备指纹等），复用平台既有的用户体系与会话链路
- 想让某个外部系统的凭证直接换成本平台用户的运行时上下文

若需求属于以下情况，本页并非首选：

- 只是想换用户数据从哪张表加载 → 看 [Provider 契约](../security/provider.md)
- 只是想调整组织、角色的加载方式 → 看 [Provider 契约](../security/provider.md)
- 需要一种全新的 Shiro 凭证类型（既有 Realm 都无法表达校验语义）→ 属于框架层演进，见下文[语义边界](#logincredential-语义边界)

## 架构与职责边界

鉴权链路的职责分成两半，扩展前必须先分清：

```
请求（Authorization: <方案> <凭证>）
        │
        ▼
TokenAuthenticatorRegistry        按前缀路由，找到唯一的认证器
        │
        ▼
TokenAuthenticator（你实现的）     只做两件事：凭证校验 + 用户解析
        │                          ——不得引入任何 Shiro 类型
        ▼
DefaultSecurityInterceptor        统一负责：
        │                          SecurityContext 填充
        │                          Shiro 会话建立（按凭证类型路由既有 Realm）
        │                          上下文缓存 / 委托代办 / 流量染色 / 在线打点
        ▼
放行业务请求
```

这条边界是硬约束：

- 认证器实现类**不得 import 任何 `org.apache.shiro` 类型**。Shiro 的 `Subject.login(...)`、各类 `AuthenticationToken` 由拦截器统一处理
- 认证器**不要自己写 `SecurityContext.setCurrentUser(...)` 以外的上下文操作**，返回结果即可，拦截器会按统一顺序完成剩余步骤
- 所有认证方案共享同一条"统一会话建立"尾部逻辑，这样新方案天然继承缓存还原、委托代办、在线打点等既有能力

## SPI 契约

接口与数据载体都在 `cn.geelato.web.common.security.spi` 包下。

### TokenAuthenticator

| 方法 | 契约 |
|---|---|
| `Collection<String> schemePrefixes()` | 本认证器接管的 `Authorization` 头前缀集合，**带尾随空格**（如 `"DingtalkCode "`）。各认证器之间前缀必须互斥 |
| `default boolean supports(String rawToken)` | 请求头值是否以本认证器前缀开头，默认按前缀集合判断 |
| `default void preAuthCheck(TokenAuthenticationContext ctx)` | 方法级访问门禁，**先于缓存还原执行**，防止借缓存绕过。默认无门禁 |
| `TokenAuthentication authenticate(ctx)` | 凭证校验 + 用户解析。成功返回结果；失败抛 `UnauthorizedException`（携带根因），不要吞异常返回 null |

### TokenAuthentication

认证成功的结果载体：

```java
public record TokenAuthentication(User user, String password, LoginCredential login, boolean trackOnline) {
    public TokenAuthentication(User user, String password, LoginCredential login) {
        this(user, password, login, true);   // trackOnline 默认 true
    }
}
```

| 字段 | 说明 |
|---|---|
| `user` | 会话用户（已按 token 维度克隆，拦截器直接使用，无需再克隆） |
| `password` | 写入 `SecurityContext` 的凭证，Shiro 会话建立依赖它 |
| `login` | 会话建立所需的凭证描述，见下 |
| `trackOnline` | 是否计入在线用户。虚拟主体（如系统令牌）传 `false` |

### LoginCredential

```java
public record LoginCredential(String type, String principal, String credential)
```

拦截器按 `type` 把认证结果路由到既有的 Shiro Realm：

| type 常量 | 对应 Realm | 校验语义 |
|---|---|---|
| `TYPE_USERNAME_PASSWORD` | DbRealm / AnonymousRealm | 登录名 + 密码（盐值哈希或固定匿名密码） |
| `TYPE_WEIXIN_UNION_ID` | WeixinUnionIdRealm | 按 `User.weixinUnionId` 列反查校验 |
| `TYPE_WEIXIN_WORK_USER_ID` | WeixinWorkUserIdRealm | 按企业微信 userId 反查校验 |
| `TYPE_OAUTH2` | OAuth2Realm | 按 accessToken 反查校验 |
| `TYPE_SYSTEM_TOKEN` | SystemTokenRealm | 固定密钥比对 |

`principal` / `credential` 的取值随 type 而异（用户名密码方案填登录名与密码，扩展键方案填扩展键本身），参照既有认证器实现。

### TokenAuthenticationContext

单次认证的输入：

| 字段 | 说明 |
|---|---|
| `rawToken` | `Authorization` 头原始值（含前缀） |
| `request` | 当前请求 |
| `handlerMethod` | 目标方法（供门禁读注解；拦截器保证非空） |
| `stripped(prefix)` | 便捷方法：去掉方案前缀后的凭证主体 |

## 关键语义

以下语义是框架强制的，扩展时必须遵守：

1. **前缀互斥，启动期硬失败**。两个认证器注册了相同前缀，应用启动直接抛 `IllegalStateException`。选前缀时确认不与既有方案冲突（既有：`JWTBearer `、`Anonymous `、`WeixinUnionId `、`WeixinWorkUserId `、`SystemToken `、`Bearer `、`WeixinCode `）。
2. **无匹配前缀直接 401**。请求带了注册表不认识的前缀，抛 `UnauthorizedException`，不做任何兜底。新认证器注册成功后，其前缀的请求才会进入认证。
3. **失败必须抛异常并携带根因**。不要 `catch (Exception) { return false; }`，也不要返回 null。旧实现吞异常导致排障困难，现契约是失败即抛 `UnauthorizedException("...: " + 根因)`，HTTP 出口仍是 401，但日志能定位到具体环节。
4. **`preAuthCheck` 先于缓存还原**。缓存命中会短路认证，方法级门禁若放在 `authenticate` 里就会被缓存绕过——这正是 `SystemToken` 的 `@AllowSystemAccess` 门禁放在 `preAuthCheck` 的原因。若你的方案有类似"按接口注解限制调用方"的需求，门禁必须覆写在 `preAuthCheck`。

## 最短接入步骤

### 第 1 步：确定前缀

选一个不与既有方案冲突的前缀，尾随空格不能省（前缀匹配是 `startsWith`，空格用于与凭证主体分隔）。

### 第 2 步：实现认证器并注册为 Spring Bean

```java
package com.example.auth;

import cn.geelato.security.OrgProvider;
import cn.geelato.security.User;
import cn.geelato.security.UserProvider;
import cn.geelato.core.GlobalContext;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.common.security.auth.AbstractTokenAuthenticator;
import cn.geelato.web.common.security.spi.LoginCredential;
import cn.geelato.web.common.security.spi.TokenAuthentication;
import cn.geelato.web.common.security.spi.TokenAuthenticationContext;
import cn.geelato.web.common.security.spi.TokenAuthenticator;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Component
public class DingtalkCodeTokenAuthenticator extends AbstractTokenAuthenticator {

    public static final String PREFIX = "DingtalkCode ";

    private final DingtalkClient dingtalkClient;
    private final UserProvider userProvider;

    public DingtalkCodeTokenAuthenticator(OrgProvider orgProvider,
                                          UserProvider userProvider,
                                          DingtalkClient dingtalkClient) {
        super(orgProvider);
        this.userProvider = userProvider;
        this.dingtalkClient = dingtalkClient;
    }

    @Override
    public Collection<String> schemePrefixes() {
        return List.of(PREFIX);
    }

    @Override
    public TokenAuthentication authenticate(TokenAuthenticationContext ctx) {
        String code = ctx.stripped(PREFIX);
        try {
            // 1. 凭证校验：调外部系统换身份标识
            String unionId = dingtalkClient.exchange(code);
            // 2. 用户解析：映射到本平台用户
            User user = userProvider.getUserByExtendKey(unionId, "weixinUnionId");
            if (user == null) {
                throw new UnauthorizedException("钉钉身份未绑定平台账号");
            }
            // 3. 装载完整会话用户（含组织信息），返回结果，剩余步骤交给拦截器
            User currentUser = loadUser(user.getLoginName(), user.getTenantCode());
            return new TokenAuthentication(currentUser, GlobalContext.getAnonymousPwd(),
                    new LoginCredential(LoginCredential.TYPE_USERNAME_PASSWORD,
                            currentUser.getLoginName(), GlobalContext.getAnonymousPwd()));
        } catch (UnauthorizedException e) {
            throw e;
        } catch (Exception e) {
            throw new UnauthorizedException("钉钉凭证认证失败: " + e.getMessage());
        }
    }
}
```

三个要点：

- 继承 `cn.geelato.web.common.security.auth.AbstractTokenAuthenticator`，直接复用 `loadUser(loginName, tenantCode)`（装载用户 + 补组织信息 + 防共享快照污染的克隆）、`setupOrgInfo(user, orgId)`、`copyOf(user)`
- `@Component` 注册即可。`BootApplication` 扫描 `cn.geelato` 全包，宿主工程通常也扫描自身包，`TokenAuthenticatorRegistry` 会自动收集所有 `TokenAuthenticator` Bean
- 构造器需要的 `OrgProvider`、`UserProvider` 都是容器里已有的 Bean，直接注入

### 第 3 步：（按需）覆写 preAuthCheck

仅当你的方案有"某些接口才允许用该凭证调用"的限制时才需要，参考 `SystemTokenAuthenticator` 对 `@AllowSystemAccess` 的处理。

### 第 4 步：用真实请求验证

不要只看 Spring 启动成功。带新前缀的 `Authorization` 头实际调一次受保护接口，确认返回业务数据、`SecurityContext` 有当前用户；再分别用错误凭证、未绑定身份各调一次，确认 401 且日志带根因。

## LoginCredential 语义边界

SPI 扩展覆盖的是"**凭证校验 + 用户解析**"，即：一个新的凭证来源，映射到既有 Realm 语义下的会话建立。

- 既有 5 种 `type`（见上表）能覆盖绝大多数场景：密码类、微信类、OAuth2 类、系统令牌类
- 若外部身份体系无法落入任何一种语义（例如需要双向挑战应答的凭证），则需要在框架层新增 Realm 并扩展 `DefaultSecurityInterceptor#createShiroToken` 的路由 switch——这属于框架层演进，与旧框架改动成本一致，不属于上层 SPI 扩展范围

## 实战示例：WeixinCode 小程序备用通道

这是一个真实的二层扩展案例：**平台提供通用认证器，宿主只实现一个更小的 SPI**。

微信小程序场景下，业务请求可以携带 `Authorization: WeixinCode <js_code>`（`wx.login()` 的一次性凭证）完成鉴权。由于各宿主接入的小程序 appid/secret 各不相同，平台侧的 `WeixinCodeTokenAuthenticator` 只负责通用编排：

- code → unionid 结果缓存（5 分钟，对齐 code 有效期，覆盖前端超时重试复用同一 code 的场景，避免微信 40163）
- unionid → 平台用户：复用 `UserProvider.getUserByExtendKey(unionid, "weixinUnionId")` 既有链路
- 会话建立：`LoginCredential(TYPE_WEIXIN_UNION_ID, ...)` → `WeixinUnionIdRealm`

而 code 如何换成 unionid（即调用微信 `jscode2session`，需要 appid/secret 配置）由宿主实现平台暴露的更小 SPI：

```java
package cn.geelato.web.common.security.spi;

public interface WeixinCodeResolver {
    /** js_code → unionid。失败抛 UnauthorizedException（携带根因）。 */
    String resolveUnionId(String jsCode);
}
```

宿主侧实现（节选自 customer-client）：

```java
@Component
@ConditionalOnProperty(prefix = "customer-client.wx.miniapp", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class WxMiniAppCodeResolver implements WeixinCodeResolver {
    // GET https://api.weixin.qq.com/sns/jscode2session?appid=..&secret=..&js_code=..
    // errcode 40029/40163/45011 映射为带文案的 UnauthorizedException
    // unionid 缺失（开放平台未绑定小程序）→ UnauthorizedException
}
```

`WeixinCodeTokenAuthenticator` 经 `ObjectProvider` 可选装配 resolver：宿主未提供实现时，`WeixinCode` 通道统一 401（提示通道未启用），其他认证方案不受影响。配合 `@ConditionalOnProperty` 总开关，可一键收口该通道。

这个案例展示的模式可以复用：**当一类认证方案的"编排逻辑"是通用的、但"凭证换取"依赖各宿主自己的外部配置时，在平台放通用认证器 + 一个宿主实现的换取 SPI**。

## 注意事项与排查

- **401 的三种根因**：无 `Authorization` 头 / 前缀未注册（含通道开关关闭）/ 门禁或凭证校验失败。前两种看拦截器与注册表的 warn 日志，第三种看认证器抛出的 `UnauthorizedException` message（带根因）
- **认证成功但接口仍 401**：检查是否在 `authenticate` 里正确返回了 `user`（含 `loginName`/`tenantCode`），`loadUser` 找不到用户会抛"用户不存在"
- **缓存与时效**：认证成功后拦截器会写入 `tokenContextCache`（30 分钟），同一凭证的后续请求走缓存还原；方案自带的外部凭证缓存（如 WeixinCode 的 code→unionid）由各认证器自行维护
- **日志脱敏**：认证器里不要打印凭证原文（token、code、密钥），参照 `OAuth2TokenAuthenticator` 只记录方案与前缀

## 推荐继续阅读

- [认证鉴权](security-authentication.md) — 运行时鉴权链路全景
- [Provider 契约](../security/provider.md) — 用户 / 组织 / 角色来源的替换
- [安全上下文](../security/context.md) — `SecurityContext` 的生命周期与使用约束
