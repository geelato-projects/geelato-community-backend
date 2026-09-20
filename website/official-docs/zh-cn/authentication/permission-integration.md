---
title: 权限接入（统一权限管理）
sidebar_label: 权限接入
---

# 权限接入（统一权限管理）

> 面向平台子系统和第三方系统开发者。本文档说明如何接入统一认证中心的权限管理能力，使本系统具备**菜单权限、元素权限、功能/接口权限、模型权限、字段权限、数据权限**，并支持跨系统、跨数据库的权限下发与生效。
>
> 前置阅读：请先完成 [OAuth2 业务系统接入](oauth2-integration.md) 或 [机器对机器接入（client_credentials）](client-credentials-integration.md)，获得应用凭证与令牌能力。

## 一、架构概览

认证中心是**权限权威源（控制平面）**，各业务子系统是**权限执行方（数据平面）**。权限在认证中心集中配置，通过下发通道贯穿到各子系统，并在子系统的查询/接口上实时生效。

```
┌─────────────────── 认证中心 (authorization 库) ───────────────────┐
│  auth_role / auth_menu / auth_permission / auth_*_r_*  关联表      │
│  管理界面(角色/菜单/权限 CRUD)  +  下发 API (/api/provisioning)  │
│  权限变更 → Redis.convertAndSend("geelato:permission:change")      │
└───────────────────────────┬───────────────────────────────────────┘
                            │
        ┌───────────────────┼────────────────────┐
        │ ① 登录/定时拉取(HTTP,带 access_token)   │ ② 变更通知(Redis pub/sub)
        ▼                                       ▼
┌─────────────────── 业务子系统 (各自数据库) ──────────────────────────┐
│  请求过滤器 → POST /oauth2/introspect（active + permissions/roles）  │
│  权限拉取服务（Bearer 调下发 API → 内存缓存 + 镜像表 auth_perm_mirror）│
│  FluentQueryFilterInjector Bean (各子系统自行实现)                   │
│    └─→ 读缓存/镜像 → 替换 #currentUser.xxx# → command.setOriginalWhere │
│  @RequirePermission/@RequireRole 自研 AOP（读 introspect 声明）      │
│  PermissionChangeListener (订阅 Redis channel → 失效缓存+刷新镜像) │
└──────────────────────────────────────────────────────────────────────┘
```

**贯穿的两个通道：**

| 通道 | 方向 | 机制 | 触发时机 |
|---|---|---|---|
| 拉取 | 认证中心 → 子系统 | HTTP `/api/provisioning/*` | 登录后 + 定时兜底（如每 5 分钟） |
| 通知 | 认证中心 → 子系统 | Redis pub/sub `geelato:permission:change` | 权限/角色/菜单/分配变更时即时推送 |

子系统采用**本地镜像表**（快照），既保证查询性能（无需每次远程调用），又具备容灾能力（认证中心短暂不可用时可降级为最近快照）。

## 二、五类权限模型

权限点统一存储于认证中心 `auth_permission` 表，以 `type` 区分五类；每类权限项均为统一对象结构（`code` 编码 / `name` 名称 / `rule` 规则 / `object` 作用对象 / `seqNo` 排序）：

| 类型 | 含义 | `object` 示例 | `rule` |
|---|---|---|---|
| `dp` 数据权限 | 行级数据可见范围 | 实体表名 | SQL 片段（含 `#currentUser.xxx#` 占位符） |
| `mp` 模型权限 | 实体增删改查操作 | 实体名 | 可空 |
| `cp` 字段权限 | 字段可见性 | 实体名 | 可描述可见/隐藏字段 |
| `ep` 元素权限 | 页面元素/按钮级控制 | 元素标识/组件路径 | 可空 |
| `fp` 功能权限 | 接口/功能点 | 接口路径 | 可空 |

生效权限 = **用户角色 ∪ 用户直挂的部门角色 ∪ 用户直授**。三个独立概念：

- **部门角色**（`auth_org_r_role`，角色挂靠部门）：定义"这个部门有哪些角色"，本身不给任何用户授权；
- **部门成员身份**（`auth_user_r_org`，多挂靠）：用户属于哪些部门，**不产生角色继承**；`auth_user.org_id` 为主部门（数据权限占位符 `#currentUser.orgId#` 取该值）；
- **用户直挂部门角色**（`auth_user_r_org_role`，user × 来源部门 × 角色）：部门角色对用户生效的**唯一途径**，来源部门不必是用户的成员部门。

菜单树为全部角色（含直挂的部门角色）的菜单并集。

## 三、前置条件

1. **子系统已注册为 OAuth 客户端**：在认证中心管理界面注册 `oauth_client`，获得 `client_id` / `client_secret`。其 `system_code` 即为该子系统的 **appCode**（权限归属标识）。
2. **网络互通**：子系统可访问认证中心的 HTTP 接口与 Redis 实例。
3. **已接入 OAuth2 登录**：子系统用户通过认证中心完成登录并获得 OAuth2 `access_token`，用于以 `Authorization: Bearer` 调用下发 API。子系统**无需依赖 Sa-Token、无需共享 Sa-Token 会话/Redis**。
4. **依赖 geelato-orm**：数据权限查询注入器基于 geelato-orm 的 `FluentQueryFilterInjector` SPI 实现。

## 四、下发 API 参考

所有下发接口均需 OAuth2 access_token：请求头携带 `Authorization: Bearer <access_token>`（或查询参数 `?access_token=`）。

### 4.1 拉取某用户的生效权限

```
GET /api/provisioning/user/{userId}?appCode=xxx&tenantCode=yyy&mode=simple
```

`mode` 可选（`simple`/`full`，默认 `simple`，非法值报错）：

- **simple（默认，轻量）**——每次调用做权限判断的最小报文：五类权限为 `PermissionItem`（`code/name/rule/object/seqNo`），`menus` 为精简树（仅渲染字段），不下发 `roles`。
- **full（完整超集）**——既能做权限判断、也能直接存入子系统自有数据库做镜像：五类权限为完整 `Permission` 对象（含 `id/type/appCode/tenantCode/remark/审计时间`），`roles` 为全部生效角色的完整对象（含 `id/code/name/appCode/enableStatus`，镜像 `auth_role` 可直接入库），`menus` 为完整树（含 `id/parentId/enableStatus/审计时间`，可拍平入库）；`roleCodes/deptRoleCodes` 编码列表两种模式均返回。

**响应体 `ProvisioningPayload`（simple 形态）：**

```jsonc
{
  "code": 20000,            // 注：响应外层为 ApiResult，data 为下方结构
  "success": true,
  "data": {
    "userId": "u123",
    "appCode": "community",
    "tenantCode": "geelato",
    "mode": "simple",        // 本次下发形态回显
    "version": 1690000000000,
    "dataPermissions": [    // 数据权限（type=dp），含未替换占位符的 rule
      { "code": "order:view-dept", "name": "看部门", "rule": "dept_id=#currentUser.deptId#", "object": "xxx_order", "seqNo": 2 }
    ],
    "modelPermissions": [],                                  // 模型权限（type=mp）
    "columnPermissions": [],                                 // 字段权限（type=cp）
    "elementPermissions": [],                                // 元素权限（type=ep，页面元素/按钮级）
    "functionPermissions": [                                 // 功能权限（type=fp）
      { "code": "order:export", "name": "订单导出", "object": "/api/orders/export", "seqNo": 0 }
    ],
    "menus": [                                               // 生效菜单精简树（层级由 children 表达，无 id/parentId）
      { "name": "订单管理", "path": "/orders", "component": "order/index",
        "sortNo": 1, "type": "menu", "permissionCode": "order:view", "children": [] }
    ],
    "roleCodes": ["sales"],                                  // 用户直挂角色编码集合
    "deptRoleCodes": ["manager"]                             // 用户直挂的部门角色编码集合
  }
}
```

### 4.2 拉取某用户的可见菜单树（前端菜单渲染）

```
GET /api/provisioning/user/{userId}/menus?appCode=xxx&tenantCode=yyy&mode=simple
```

`mode` 可选（默认 `simple`）：`simple` 返回精简树（仅 `name/path/component/icon/sortNo/permissionCode/type/children`，直接渲染）；`full` 返回完整树（含 `id/parentId/enableStatus/审计时间` 等，可拍平存入镜像表）。
两种形态均为树形、按 `sortNo` 排序。子系统登录后调用一次即可直接渲染前端菜单；4.1 载荷中的 `menus` 与此相同，只想取权限时无需再调本接口。

> **机器令牌调用**：下发 API 亦支持 client_credentials 机器令牌（`appCode` 需与客户端绑定的 `system_code` 一致）；机器令牌调用且未显式传 `appCode` 时，服务端自动采用客户端绑定的 `system_code`。
>
> **客户端 SDK（geelato-auth-client）**：可引入 `cn.geelato:geelato-auth-client` 模块，由 `AuthCenterProvisioningClient` 以机器令牌自动拉取下发载荷（内置可选 TTL 缓存与失效接口，配置前缀 `geelato.auth.client.*`），无需手写 HTTP 与令牌管理。

### 4.3 拉取某子系统的全部权限点（镜像重建）

```
GET /api/provisioning/app/{appCode}/permissions?type=dp&tenantCode=yyy
```

返回 `PermissionListItem[]`（含 `id/code/name/type/object/rule/appCode/seqNo`）。`type` 可选，不传则返回全部类型。

### 4.4 查询权限版本号

```
GET /api/provisioning/version?appCode=xxx
```

返回 `Long`（时间戳近似版本号）。子系统定时调用，与本地缓存的版本号比较，不同才触发全量刷新，减少无效拉取。

### 4.5 标准 OAuth2 自省 / 用户信息（含功能权限声明）

除下发 API 外，客户端还可通过标准 OAuth2 端点校验令牌并直接获取**功能权限/角色**，无需调用下发 API 即可做接口/按钮级鉴权：

- `POST /oauth2/introspect`（RFC 7662，需 client 认证）：除 `active/subject/scope/exp` 等标准字段外，**新增扩展声明 `permissions`（功能权限码集合）、`roles`（角色编码集合）**。可传 `appCode` 指定子系统维度（不传则返回 `__global__` 维度）。
- `GET /oauth2/userinfo-oidc?access_token=xxx&appCode=xxx`：claims 中含 `permissions` / `roles`。

> 说明：**功能权限**可直接来自 introspect/userinfo；**数据权限规则**（type=dp 的 `rule`）仍走 4.1 下发 API（体积较大、按实体匹配，适合本地镜像表）。两路配合即可覆盖「功能 + 数据」两层权限控制。

## 五、子系统接入步骤

> 以下步骤对任何基于 geelato-orm 的子系统通用。可运行参考实现：`geelato-sample-auth` 示例工程（远程客户端 demo，功能 + 数据权限，零 Sa-Token 依赖，含 Bearer 过滤器 / 拉取缓存镜像 / 注入器 / AOP / 变更监听全套代码）。

### 步骤 1：建本地镜像表

在子系统数据库执行以下 DDL（存储从认证中心拉取并解析后的权限快照）：

```sql
CREATE TABLE IF NOT EXISTS auth_perm_mirror (
    id              VARCHAR(64)  NOT NULL COMMENT '主键(=权限点ID或userId+权限ID)',
    user_id         VARCHAR(64)           DEFAULT NULL COMMENT '用户ID（用户级权限快照）',
    perm_type       VARCHAR(8)   NOT NULL COMMENT 'dp/mp/cp/ep/fp',
    entity          VARCHAR(255)          DEFAULT NULL COMMENT '受控实体名/接口路径',
    name            VARCHAR(128)          DEFAULT NULL COMMENT '权限名/规则名',
    rule            VARCHAR(1024)         DEFAULT NULL COMMENT '数据权限规则（含占位符）',
    weight          INT          NOT NULL DEFAULT 0,
    perm_code       VARCHAR(128)          DEFAULT NULL COMMENT '功能/模型权限码',
    menu_id         VARCHAR(64)           DEFAULT NULL COMMENT '菜单ID（菜单快照用）',
    app_code        VARCHAR(128)          DEFAULT NULL,
    tenant_code     VARCHAR(64)           DEFAULT NULL,
    synced_at       DATETIME              DEFAULT NULL COMMENT '最近同步时间',
    PRIMARY KEY (id),
    KEY idx_apm_user (user_id, perm_type),
    KEY idx_apm_entity (entity, perm_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证中心权限镜像表';
```

### 步骤 2：实现权限拉取（Bearer 调下发 API → 内存缓存 + 镜像表）

实现一个拉取服务：用**当前用户的 access_token**（`Authorization: Bearer`）调 4.1 下发 API，映射为子系统权限运行时模型后写入内存缓存与本地镜像表：

```java
@Service
public class AuthCenterPermissionPuller {
    private final RestTemplate restTemplate = new RestTemplate();
    private final JdbcTemplate jdbc;
    @Value("${geelato.permission.auth-center-base-url}")
    private String baseUrl;

    @SuppressWarnings("unchecked")
    public List<Permission> pull(String userId, String appCode, String tenantCode, String bearerToken) {
        // 1. 调下发 API —— 必须携带用户 Bearer（认证中心经 TokenIntrospector 校验）
        String url = UriComponentsBuilder.fromHttpUrl(baseUrl + "/api/provisioning/user/" + userId)
                .queryParam("appCode", appCode).queryParam("tenantCode", tenantCode).toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(bearerToken);
        Map<String, Object> resp = restTemplate.exchange(url, HttpMethod.GET,
                new HttpEntity<>(headers), new ParameterizedTypeReference<Map<String, Object>>() {}).getBody();

        // 2. 解包 ApiResult 外层 —— 注意字段是 status="success" / code=20000（没有 success 布尔字段）
        if (resp == null || !"success".equals(resp.get("status"))) return List.of();
        Map<String, Object> data = (Map<String, Object>) resp.get("data");
        List<Map<String, Object>> dps = (List<Map<String, Object>>) data.getOrDefault("dataPermissions", List.of());

        // 3. 映射为 Permission 并覆盖式写入镜像表（先删后插）
        //    注：dataPermissions 项字段为 code/name/rule/object/seqNo（五类权限统一结构）
        jdbc.update("DELETE FROM auth_perm_mirror WHERE user_id = ?", userId);
        List<Permission> result = new ArrayList<>();
        for (Map<String, Object> dp : dps) {
            String entity = (String) dp.get("object");
            String rule = (String) dp.get("rule");
            int weight = dp.get("seqNo") == null ? 0 : ((Number) dp.get("seqNo")).intValue();
            jdbc.update("INSERT INTO auth_perm_mirror(id,user_id,perm_type,entity,name,rule,weight,perm_code,synced_at) "
                            + "VALUES(?,?,?,?,?,?,?,?,NOW())",
                    UUID.randomUUID().toString(), userId, "dp", entity, (String) dp.get("name"), rule, weight, (String) dp.get("code"));
            Permission p = new Permission();
            p.setEntity(entity); p.setName((String) dp.get("name"));
            p.setRule(rule); p.setWeight(weight);
            result.add(p);
        }
        return result;   // 调用方写入内存缓存；认证中心不可达时降级读镜像表（见第八节）
    }
}
```

> **更省事的方式**：引入 `cn.geelato:geelato-auth-client` 模块即可省去上述手写 HTTP/令牌/解包代码——自动装配的 `AuthCenterProvisioningClient` 以 client_credentials 机器令牌调用下发 API（`getPermissions(userId, mode)` / `getMenus(userId, mode)`），内置可选 TTL 缓存与 `invalidate(userId)`；配置 `geelato.auth.client.base-url/client-id/client-secret` 即可用。需要镜像入库时传 `mode=full`（载荷含 `id/type/审计时间` 与完整 `roles`/`menus`）。

### 步骤 3：实现 `FluentQueryFilterInjector`（数据权限生效，核心）

这是数据权限真正生效的关键。实现一个 Spring Bean：取当前用户 → 按实体名匹配规则 → 替换占位符 → 追加到查询条件：

```java
@Component
public class DataPermissionInjector implements FluentQueryFilterInjector {

    @Value("${geelato.permission.data.enabled:true}")
    private boolean enabled;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void inject(QueryCommand command, MetaQuery query) {
        // 1. 当前用户上下文（ThreadLocal，由子系统请求过滤器 SecurityContext.setCurrentUser(user) 填充）
        User user = SecurityContext.getCurrentUser();
        if (user == null) return;

        // 2. 按查询实体名匹配数据权限（同实体多条按 weight 取最大者生效）
        String entity = command.getEntityName();
        if (!StringUtils.hasText(entity)) return;
        Permission dp = user.getDataPermissionByEntity(entity);
        if (dp == null || !StringUtils.hasText(dp.getRule())) return;

        // 3. 替换 #currentUser.xxx# 为字面值
        String rule = PermissionRuleUtils.replaceRuleVariable(dp, user);
        if (!StringUtils.hasText(rule)) return;

        // 4. 写入 originalWhere（原生 SQL 片段），与已有条件 AND 合并
        String existing = command.getOriginalWhere();
        command.setOriginalWhere(StringUtils.hasText(existing)
                ? "(" + existing + ") AND (" + rule + ")"
                : rule);
    }
}
```

> **关键注意事项：**
> - 注入点在 `QueryCommandAdapter.adapt()` 中，**早于数据源切换**执行，因此注入的条件对最终命中的任意数据源都生效——**天然支持子系统多数据库**。
> - 数据权限必须写入 `command.setOriginalWhere(...)`（原生 SQL 片段路径）；**不是** `QueryCommand.ACL` 字段——ACL 仅参与缓存签名，不会拼入 SQL。结构化条件（如租户过滤）才走 `FilterGroup`。
> - 当容器中存在多个 `FluentQueryFilterInjector` Bean 时，运行期要求**至多一个 `isEnabled()==true`**，否则抛 `IllegalStateException`。
> - 该实体**无规则时**的策略由子系统自定：示例默认不追加（=全可见，便于对比）；平台默认最小权限（`creator='<userId>'`）。
> - 请求过滤器需在业务执行前 `SecurityContext.setCurrentUser(user)`（含 `deptId/buId` 等业务字段，供占位符替换），请求结束 `SecurityContext.clear()`。

### 步骤 4：订阅变更通知（Redis pub/sub）

接收认证中心推送的变更事件，失效本地缓存（下次请求重新拉取并刷新镜像表）：

```java
@Component
public class PermissionChangeListener implements MessageListener {
    public static final String CHANNEL = "geelato:permission:change";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final RedisMessageListenerContainer container;

    public PermissionChangeListener(RedisMessageListenerContainer container) {
        this.container = container;
    }

    @PostConstruct
    public void register() {
        container.addMessageListener(this, new ChannelTopic(CHANNEL));
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            JsonNode evt = MAPPER.readTree(new String(message.getBody()));
            String userId = evt.path("userId").asText(null);
            if (userId != null && !userId.isBlank()) {
                // 失效该用户：独立客户端失效自己的权限快照缓存
            } else {
                // appCode/全局变更：失效全部缓存
            }
        } catch (Exception ignored) {
        }
    }
}
```

### 步骤 5：功能/接口权限鉴权

功能权限码（`fp` 类型）用于控制接口访问和前端按钮。**声明来源是 introspect/userinfo 的 `permissions`/`roles` 扩展声明**（见 4.5），子系统据此做鉴权，**无需依赖 Sa-Token**：

```java
// 推荐（协议级、零 Sa-Token）：自研注解 + AOP，读请求过滤器缓存的 introspect 声明
@RequirePermission("order:export")
@PostMapping("/api/orders/export")
public void export() { ... }

@RequireRole("admin")
@GetMapping("/api/orders/audit")
public void audit() { ... }
```

前端按钮控制：调 `GET /oauth2/userinfo-oidc?access_token=xxx&appCode=xxx` 取 claims 中的 `permissions`/`roles`，用 `v-permission` 指令或 `hasPermission(code)` 判断显隐；元素权限（`ep`）同理，取 4.1 载荷中的 `elementPermissions` 按码判断。

## 六、数据权限规则语法

数据权限的 `rule` 字段存放 SQL 片段，支持 `#currentUser.<field>#` 占位符，由子系统注入器在查询时替换为当前用户字面值。

### 6.1 支持的占位符字段（白名单）

| 占位符 | 含义 |
|---|---|
| `#currentUser.userId#` | 用户ID |
| `#currentUser.orgId#` | 组织ID（部门或科室） |
| `#currentUser.defaultOrgId#` | 默认组织ID |
| `#currentUser.cooperatingOrgId#` | 合作组织ID |
| `#currentUser.companyId#` | 公司ID |
| `#currentUser.extendId#` | 扩展ID |
| `#currentUser.deptId#` | 部门ID |
| `#currentUser.buId#` | 事业部ID |
| `#currentUser.weixinUnionId#` | 微信UnionID |
| `#currentUser.weixinWorkUserId#` | 企业微信UserID |
| `#currentUser.tenantCode#` | 租户编码 |
| `#currentUser.defaultOrg.orgId#` | 默认组织.组织ID |
| `#currentUser.defaultOrg.deptId#` | 默认组织.部门ID |
| `#currentUser.defaultOrg.companyId#` | 默认组织.公司ID |
| `#currentUser.defaultOrg.extendId#` | 默认组织.扩展ID |

不在白名单内的字段会在认证中心创建/更新权限时被**拒绝**。

### 6.2 内置四档数据范围

认证中心预置了 4 条通用数据范围权限（`__global__`，可供所有子系统引用）：

| 编码 | 名称 | 规则 | 权重 |
|---|---|---|---|
| `&all` | 看全部 | `1=1` | 4 |
| `&myBusiness` | 看公司/事业部 | `bu_id=#currentUser.buId#` | 3 |
| `&myDept` | 看部门 | `dept_id=#currentUser.deptId#` | 2 |
| `&myself` | 看自己创建 | `creator=#currentUser.userId#` | 0 |

**多条规则合并**：同一用户对同一实体若有多条数据权限（如角色授予「看部门」+ 直授「看自己」），按 `weight`（权重）取**最大者**生效（即范围最宽的优先）；不同实体分别独立计算。

### 6.3 自定义规则示例

```text
# 仅看本人创建且状态为启用的数据
creator=#currentUser.userId# AND status=1

# 按多个部门字段控制
(dept_id=#currentUser.deptId# OR bu_id=#currentUser.buId#)

# 按默认组织的部门
dept_id=#currentUser.defaultOrg.deptId#
```

## 七、配置项清单

子系统侧需新增以下配置（`application.properties`）：

```properties
# 权限来源：platform(本地直查) / auth-center(认证中心下发)
geelato.permission.source=auth-center

# 认证中心基础地址
geelato.permission.auth-center-base-url=http://auth-center-host:9000

# 本子系统的 appCode（与 oauth_client.system_code 一致）
geelato.permission.app-code=community

# 本子系统的 OAuth 客户端凭证（调 /oauth2/introspect 做 client 认证，取 permissions/roles 声明用）
geelato.permission.client-id=community
geelato.permission.client-secret=xxx

# introspect 结果按 token 缓存的秒数（减少重复网络调用）
geelato.permission.introspect-cache-seconds=30

# 数据权限注入开关
geelato.permission.data.enabled=true

# 定时全量刷新间隔（分钟），作为通知的兜底
geelato.permission.refresh-interval-minutes=5

# Redis 连接（订阅通知用，与认证中心同实例或同集群）
spring.data.redis.host=xxx
spring.data.redis.port=6379
spring.data.redis.password=xxx
```

## 八、容灾与降级

| 场景 | 行为 |
|---|---|
| 认证中心短暂不可用 | 子系统读本地镜像表 `auth_perm_mirror` 继续工作（降级为最近一次快照），不阻断业务 |
| Redis 通知丢失 | 定时全量刷新（`refresh-interval-minutes`）兜底，最终一致 |
| 新用户首次登录、镜像表无数据 | 触发一次拉取（拉取失败则无数据权限，按"最小权限"返回空） |
| 缓存 TTL | 用户上下文建议缓存 5~30 分钟，权限变更由通知主动失效 |

**建议缓存策略**：用户基本信息缓存 30 分钟，数据权限缓存 5 分钟（与刷新间隔一致），通知到达时立即失效。

## 九、常见问题 FAQ

**Q1：为什么数据权限只在 `MetaFactory.query(...)` 路径生效，JSON/文本 MQL 查询不生效？**
A：`FluentQueryFilterInjector` 仅覆盖 Fluent DSL 路径。若子系统有 MQL 查询路径，需另实现 geelato-core 的 `MqlQueryFilterInjector`（另一套 SPI）。

**Q2：多租户如何处理？**
A：所有权限表带 `tenant_code`。下发 API 支持 `tenantCode` 参数过滤；子系统请求时通过 `X-Tenant-Code` 请求头或登录态携带租户码，认证中心按租户隔离计算权限。

**Q3：权限变更后多久生效？**
A：通过 Redis pub/sub 通知为**秒级**；若通知异常，定时兜底为**分钟级**。排查生效延迟：检查子系统是否成功订阅 `geelato:permission:change` channel、缓存失效逻辑是否正确执行。

**Q4：子系统不基于 geelato-orm（如纯 MyBatis）如何接入？**
A：数据权限注入器依赖 geelato-orm 的 SPI，非 geelato-orm 技术栈需自行实现 SQL 拦截（如 MyBatis Plugin 解析并改写 SQL，追加数据权限 where 条件）。功能/菜单权限不依赖 ORM，任何技术栈均可通过下发 API + 调用鉴权接入。

**Q5：appCode 的作用是什么？**
A：标识权限归属哪个子系统。权限配置时指定 `appCode`（或留空为全局 `__global__`）。用户在某子系统的生效权限 = 该 appCode 专属权限 + 全局权限。这样不同子系统的权限相互隔离，又可共享通用规则。

## 附录：认证中心侧表结构速查

| 表 | 说明 |
|---|---|
| `auth_role` | 角色（code/name/appCode/enableStatus） |
| `auth_menu` | 菜单（树形，parentId/type=catalog,menu,button/permissionCode） |
| `auth_permission` | 权限点（统一，type=dp,mp,cp,ep,fp / object / rule / seqNo） |
| `auth_role_r_permission` | 角色-权限关联 |
| `auth_role_r_menu` | 角色-菜单关联 |
| `auth_user_r_role` | 用户-角色关联 |
| `auth_org_r_role` | 角色-部门关联（角色挂靠部门，定义该部门有哪些角色） |
| `auth_user_r_org` | 用户-部门成员关联（多挂靠，不产生角色继承） |
| `auth_user_r_org_role` | 用户直挂部门角色（user × 来源部门 × 角色，部门角色生效的唯一途径） |
| `auth_user_r_permission` | 用户-权限直授关联 |

> 建表由认证中心的 Flyway 迁移脚本自动执行，子系统**不需要**在本库创建这些表（子系统只用本地 `auth_perm_mirror` 镜像表）。

## 十、集成方自助管理（开放 API）

除只读的下发 API 外，认证中心还向集成方开放**自助管理 API**（`/api/open/v1/**`）：集成方用 `client_credentials` 机器令牌即可管理**属于自己的**角色、菜单、权限点、授权关系、用户、组织、会话，据此实现自己的管理 UI——与认证中心管理后台操作同一份数据，自动叠加调用方隔离（appCode/租户绑定，不可越权指定）。

要点：

- 令牌：`POST /oauth2/client_token`（grant_type=client_credentials），机器令牌专用于开放 API；用户令牌不可调用。
- 隔离：角色/菜单/权限点按 appCode 严格隔离（`__global__` 只读）；用户/组织为租户级共享。
- 联动：写操作触发 `geelato:permission:change` 通知与版本号递增；全部写操作记入审计哈希链。
- 下发 API 加固：client_credentials 令牌调用 `/api/provisioning/**` 时，appCode 参数必须等于自身 system_code，否则 403。
