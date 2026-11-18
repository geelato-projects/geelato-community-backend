---
title: API · 认证登录
sidebar_label: 认证登录
---
# 认证登录（auth）

2 个 Controller，共 18 个端点。标注 ⚙ 的为设计态接口。

## JWTAuthController — `/user`

登录认证与当前用户自助操作核心：账号/匿名登录、切换身份与租户、委托代办、当前用户信息与菜单、头像与资料维护、密码重置与找回、账号验证与绑定。

### login 登录认证

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/user/login` |
| 参数 | `username`：body，string，必填，登录名（最长 64）。<br />`password`：body，string，必填，登录密码（最长 128）。<br />`tenant`：body，string，可选，租户编码。<br />`suffix`：body，string，可选，登录名后缀（形如 `@租户`，非空时优先于 `tenant` 解析租户）。<br />`org`：body，string，可选，预留字段（当前登录逻辑未使用）。 |
| 返回 | `ApiResult<LoginResult>`：登录成功返回用户基本信息与 `token`；JWT 载荷字段为 `id`、`loginName`、`passWord`、`orgId`、`tenantCode`。登录名在多租户下不唯一时返回失败（code 为多租户异常码），`data.tenants` 携带候选租户列表供前端选择。 |

### loginAnonymous 匿名登录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/user/login/anonymous` |
| 参数 | `username`：body，string，必填，匿名账号登录名。<br />`password`：body，string，必填，须等于平台配置的匿名固定密码。<br />其余字段同 `login`。 |
| 返回 | `ApiResult<LoginResult>`：同 `login`；JWT 载荷额外携带 `anonymous` 字段。需平台开启匿名登录选项，未开启时返回失败。 |

### switchIdentity 切换身份

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/user/switchIdentity` |
| 参数 | `org`：query，string，可选，目标组织 ID。<br />`tenant`：query，string，可选，目标租户编码。 |
| 返回 | `ApiResult<LoginResult>`：按新 `orgId`/`tenantCode` 重签 JWT 并返回登录结果；传入的组织或租户不在当前用户可选范围时回退为用户默认值。 |

### delegators 可代办委托人列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/user/delegators` |
| 参数 | 无 |
| 返回 | `ApiResult<List<UserDelegateMap>>`：当前用户（代理人）可代为操作的委托人列表（`scope=mentor_assist`、启用有效的记录，按委托人 ID 去重），供右上角委托代办切换下拉。未登录返回失败。 |

### delegateAs 切换代办身份

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/user/delegateAs` |
| 参数 | `targetUserId`：query，string，目标委托人用户 ID。 |
| 返回 | `ApiResult<User>`：不重签 token，仅在服务端写入「凭证 → 委托目标」会话映射，返回目标委托人信息。校验：须存在有效的委托代办关系，禁止嵌套代办、不能代办自己。 |

### exitDelegate 退出代办身份

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/user/exitDelegate` |
| 参数 | 无 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；移除当前凭证的委托会话并清除代办态上下文缓存，恢复为代理人本人。 |

### getUserInfo 当前用户信息

| 项 | 内容 |
| --- | --- |
| URL | `GET\|POST /api/user/info` |
| 参数 | 无 |
| 返回 | `ApiResult<LoginResult>`：当前用户完整信息，含 `token`、组织列表 `orgs`、租户列表 `tenants`、角色信息 `roles`/`roleCodes`/`roleIds`，处于代办态时含 `delegateUserId`/`delegateUserName`。 |

### logout 退出登录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/user/logout` |
| 参数 | 无 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### uploadAvatar 上传头像

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/user/avatar/{userId}` |
| 参数 | `userId`：path，string，目标用户 ID。<br />`file`：form，multipart 文件，头像文件（参数名 `file`）。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### updateUserInfo 更新用户资料

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/user/update/{userId}` |
| 参数 | `userId`：path，string，目标用户 ID。<br />`name`：body，string，可选，姓名（最长 64）。<br />`enName`：body，string，可选，英文名（最长 64）。<br />`mobilePrefix`：body，string，可选，手机前缀（最长 16）。<br />`mobilePhone`：body，string，可选，手机号（最长 16）。<br />`telephone`：body，string，可选，电话（最长 64）。<br />`email`：body，string，可选，邮箱（最长 128）。<br />`nationCode`、`provinceCode`、`cityCode`：body，string，可选，国家/省/市编码（最长 32）。<br />`address`：body，string，可选，地址（最长 255）。<br />`description`：body，string，可选，描述（最长 255）。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空。 |

### getCurrentUserMenu 当前用户菜单

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/user/menu` |
| 参数 | `flag`：body，string，可选，透传给菜单查询的过滤标记。<br />`appId`：body，string，可选，应用 ID。<br />`tenantCode`：body，string，可选，租户编码；非空时须与当前用户租户一致，否则校验失败。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：当前用户菜单节点列表（按应用/租户查询授权菜单树节点）。`appId` 或租户编码缺失时返回空列表。 |

### resetPassword 重置当前用户密码

| 项 | 内容 |
| --- | --- |
| URL | `POST\|GET /api/user/resetPassword` |
| 参数 | `passwordLength`：query，int，可选，新密码长度，默认 8，最长 32。 |
| 返回 | `ApiResult<String>`：`data` 为重置后生成的随机明文密码。 |

### forgetValid 找回账号定位

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/user/forgetValid` |
| 参数 | `validType`：body，string，必填，验证方式（1 手机、2 邮箱、3 密码）。<br />`validBox`：body，string，必填，手机号或邮箱。<br />`prefix`：body，string，`validType` 为 1 时必填，手机前缀。 |
| 返回 | `ApiResult<User>`：`data` 仅含匹配用户的 `id`（供后续找回流程使用）；匹配不到唯一用户时抛出业务异常。 |

### forgetPassword 忘记密码重置

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/user/forget` |
| 参数 | `action`：body，string，必填，业务动作标识（用于验证码校验）。<br />`validType`：body，string，必填，验证方式（1 手机、2 邮箱）。<br />`validBox`：body，string，必填，手机号或邮箱。<br />`authCode`：body，string，必填，验证码。<br />`userId`：body，string，必填，用户 ID。<br />`password`：body，string，必填，新密码。<br />`prefix`：body，string，可选，手机前缀。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；验证码校验通过后重置该用户密码。 |

### validateUser 用户身份验证

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/user/validate` |
| 参数 | `validType`：body，string，必填，验证方式（1 手机、2 邮箱、3 密码）。<br />`userId`：body，string，必填，用户 ID。<br />`authCode`：body，string，必填，验证码；`validType` 为 3 时为登录密码。<br />`prefix`：body，string，可选，手机前缀。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；按验证方式校验验证码或密码，失败抛出业务异常。 |

### bindAccount 绑定账号

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/user/bindAccount` |
| 参数 | `validType`：body，string，必填，绑定方式（1 手机、2 邮箱、3 密码）。<br />`userId`：body，string，必填，用户 ID。<br />`authCode`：body，string，必填，验证码。<br />`validBox`：body，string，必填，手机号/邮箱/新密码。<br />`prefix`：body，string，可选，手机前缀。 |
| 返回 | `ApiResult<NullResult>`：`data` 为空；`validType` 为 3 时将 `validBox` 加密后作为新登录密码保存，为 1/2 时校验验证码后绑定手机或邮箱。 |

## OAuth2Controller — `/oauth2`

OAuth2 对接登录：以授权码到 OAuth 服务端换取令牌并拉取用户信息完成本平台登录，以及访问令牌的刷新。

### login OAuth2 登录

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/oauth2/login` |
| 参数 | `code`：query，string，OAuth2 授权码。 |
| 返回 | `ApiResult<LoginResult>`：以平台配置的 OAuth 服务端地址与客户端凭证换取访问令牌、拉取用户信息，存储 access/refresh 令牌映射后返回登录结果（`token` 为 OAuth 访问令牌）。换取或拉取用户信息失败时返回失败信息。 |

### refreshToken 刷新访问令牌

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/oauth2/refreshToken` |
| 参数 | `oldToken`：query，string，旧访问令牌。 |
| 返回 | `ApiResult<HashMap<String,String>>`：`data` 含 `access_token` 与 `refresh_token`；按旧令牌查得刷新令牌并向 OAuth 服务端换取新令牌，成功后更新令牌映射。查不到刷新令牌或换取失败时返回失败信息。 |
