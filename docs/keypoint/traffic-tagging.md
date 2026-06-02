# 流量染色（Traffic Tagging）

## 背景与目标

平台提供“流量染色”能力：为每次请求生成/解析一个 `trafficTag`，并通过 Cookie 持久化，同时在响应 Header 与服务端上下文中透出，便于后续做灰度发布、过滤、转发、拦截等。

该能力只负责“打标/透传”，不内置分流逻辑。分流通常由网关或其他上层组件消费 `trafficTag` 实现。

## 标记载体

服务端会对每个请求输出：

* Header：`X-Gl-Traffic-Tag`
* Cookie：`gl_traffic_tag`

其中 `gl_traffic_tag` 为签名 Cookie，服务端会校验签名；验签失败会按策略重算并覆盖写回。

## 执行位置与时序

染色入口在 [DefaultSecurityInterceptor](file:///d:/geelato/geelato-enterprise/geelato-community/geelato-web-common/src/main/java/cn/geelato/web/common/interceptor/DefaultSecurityInterceptor.java)：

* 请求进入 `preHandle` 时会先执行一次基础染色（用于解析/签发 cookie、透出 header、写入上下文）。
* 鉴权成功（或从 token 缓存恢复出 user）后，会再次按策略计算 `trafficTag` 并覆盖写回，保证“需要用户身份才能判断”的策略生效。

## 服务端可消费的上下文

* ThreadLocal：`cn.geelato.web.common.traffic.TrafficTagContext.get()`
* Request Attribute：`gl.traffic.tag`（默认 key，可配置）
* MDC：`trafficTag`（默认 key，可配置）

## 默认策略：账号白名单

内置默认策略为“账号白名单”，实现类：

* `cn.geelato.web.common.traffic.WhitelistTrafficTagStrategy`

规则：

* 白名单每行一个账号（loginName）
* 命中白名单：`gray`
* 否则：`default`

配置方式（两选一或同时使用）：

* `geelato.traffic.gray-whitelist`：多行字符串
* `geelato.traffic.gray-whitelist-location`：外部文件/资源位置，支持 `classpath:` 与 Spring 资源模式（例如 `file:/opt/conf/gray-whitelist.txt`）

示例（yaml）：

```yaml
geelato:
  traffic:
    gray-whitelist: |
      admin
      test01
      # 每行一个账号(loginName)
```

示例（外部文件）：

```yaml
geelato:
  traffic:
    gray-whitelist-location: file:/opt/conf/gray-whitelist.txt
```

## 非生产环境调试覆盖

仅在非 `product` 环境生效：

* Header：`X-Gl-Traffic-Override: gray`
* Query：`?glTrafficTag=gray`

该覆盖值会被服务端校验并签发回 `gl_traffic_tag`，方便联调。

## 如何自定义染色策略

### 1) 实现策略接口

实现接口：

* `cn.geelato.traffic.TrafficTagStrategy`

方法签名：

* `String resolveTag(HttpServletRequest request, User user)`

注意：

* `user` 可能为空（未登录/无需鉴权接口）。
* 返回的 tag 需符合格式限制：`[A-Za-z0-9_-]{1,32}`，否则会降级为 `default`。

### 2) 注册为 Spring Bean

将实现类声明为 Spring Bean：

* 单实现：直接 `@Component` 即可
* 多实现：需要对希望启用的实现标记 `@Primary`（避免注入歧义）

示例：

```java
import cn.geelato.security.User;
import cn.geelato.traffic.TrafficTagStrategy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class TenantAwareTrafficTagStrategy implements TrafficTagStrategy {
    @Override
    public String resolveTag(HttpServletRequest request, User user) {
        if (user != null && "geelato".equals(user.getTenantCode())) {
            return "gray";
        }
        return "default";
    }
}
```

### 3) 关于 MyBatis 的注意事项

平台 MyBatis 配置存在全包扫描（`@MapperScan(basePackages="cn.geelato.web,cn.geelato.meta")`）。

因此，策略接口不应放在 `cn.geelato.web.*` 包下，否则可能被误识别为 MyBatis Mapper 并出现类似报错：

* `Invalid bound statement (not found): ...TrafficTagStrategy.resolveTag`

推荐将策略接口与实现放在非 `cn.geelato.web.*` 的包中（本项目默认接口包为 `cn.geelato.traffic`）。

