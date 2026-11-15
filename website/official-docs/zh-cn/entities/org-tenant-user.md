---
title: 组织与用户
sidebar_label: 组织与用户
---

# 组织与用户

本页覆盖组织、公司、用户、行政区划与租户共 6 个实体。User 是平台账号主体；Org 构成组织树；Company 承载法人公司信息并与组织挂钩；Tenant/TenantSite 承载多租户与站点配置。组织与用户在安全契约中的读取视角见[安全契约概览](../security/overview.md)。

## Org 组织

组织机构树节点。继承 `BaseSortableEntity`，公共字段见[概览](overview.md#公共基类字段)。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| name | name | String | 组织名称 |
| code | code | String | 组织编码 |
| pid | pid | String | 父组织编码 |
| type | type | String | 组织类型 |
| category | category | String | 组织分类 |
| status | status | int | 组织状态 |
| description | description | String | 组织描述 |
| extendId | extend_id | String | 组织形态扩展 |

## Company 公司信息

法人公司信息实体，与组织（Org）挂钩，支持上级公司层级。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| pid | pid | String | 上级公司 |
| name | name | String | 名称 |
| code | code | String | 编号 |
| remark | remark | String | 备注 |
| shortName | short_name | String | 公司简称 |
| enName | en_name | String | 英文名称 |
| address | address | String | 公司地址 |
| countryCode | country_code | String | 国家/地区 |
| standardCurrency | standard_currency | String | 本位币 |
| enAddress | en_address | String | 英文地址 |
| localLanguage | local_language | String | 本地语言 |
| cooperatingOrgId | cooperating_org_id | String | 绑定客商 |
| orgId | org_id | String | 组织 ID，关联 Org |
| weixinWorkInfo | weixin_work_info | String | 企业微信配置 |

## User 用户

平台用户账号主体：登录凭据、联系方式、账号类型与来源、归属组织与行政区划。继承 `BaseSortableEntity`，实现 `EntityEnableAble`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| name | name | String | 名称 |
| enName | en_name | String | 英文名 |
| loginName | login_name | String | 登录名，非空 |
| sex | sex | int | 性别 |
| orgId | org_id | String | 组织，`@ForeignKey` 指向 Org（platform_org） |
| password | password | String | 密码（加盐散列存储） |
| salt | salt | String | Salt |
| avatar | avatar | String | 头像 |
| mobilePrefix | mobile_prefix | String | 手机区号，长度 16 |
| mobilePhone | mobile_phone | String | 手机号，长度 16 |
| telephone | telephone | String | 电话 |
| email | email | String | 邮箱 |
| post | post | String | 职务 |
| type | type | int | 类型：0 员工账号、1 系统账号、2 企业外人员、3 虚拟员工账号 |
| source | source | int | 来源：0 本地用户、1 系统同步 |
| nationCode | nation_code | String | 国家 |
| provinceCode | province_code | String | 省份 |
| cityCode | city_code | String | 城市 |
| address | address | String | 详细地址 |
| description | description | String | 描述 |
| orgName | orgName | String | 部门，引用列（isRefColumn，随 orgId 取 platform_org.name；源码 @Col 名为驼峰 `orgName`） |
| jobNumber | job_number | String | 工号 |
| cooperatingOrgId | cooperating_org_id | String | 合作单位 id |
| enableStatus | enable_status | int | 启用状态：1 启用、0 未启用 |
| weixinUnionId | weixin_unionId | String | 微信用户统一标识 |
| weixinWorkUserId | weixin_work_userId | String | 企业微信标识 |
| plainPassword | — | String | `@Transient` 非落库明文密码，不显示在 Restful 接口属性中 |
| stocked | — | boolean | `@Transient` 非落库 |
| orgUserPost | — | String | `@Transient` 非落库 |

## Area 区县

行政区划（省/市/区县）基础数据。继承 `BaseSortableEntity`，未标注 catalog。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| name | name | String | 名称 |
| code | code | String | 编码 |
| cityId | city_id | String | 省份编码，非空。源码列名 `city_id` 与标题"省份"存在错位，按实际语义撰写 |
| description | description | String | 描述 |

## Tenant 租户

租户定义。类级标题误写为"租户站点"（与 TenantSite 重复），按实际语义撰写。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| code | code | String | 企业的编码即租户编码 |
| companyName | company_name | String | 公司名称 |
| companyDomain | company_domain | String | 公司域名 |
| mainEmail | main_email | String | 公司邮箱 |
| mainEmailPwd | main_email_pwd | String | 公司邮箱密码 |
| corpId | corp_id | String | 公司企业微信 corpId |
| corpToken | corp_token | String | 公司企业微信 corpToken |

## TenantSite 租户站点

租户站点配置：站点外观、备案信息、域名、多语言、单点登录与微信登录接入。继承 `BaseEntity`。

| 属性 | 列名 | 类型 | 说明 |
| --- | --- | --- | --- |
| name | name | String | 站点名称，一般可填写企业的名称 |
| copyright | copyright | String | 版权信息（源码 @Title 误复制为"一般可填写企业的名称"） |
| nsFilingNo | ns_filing_no | String | 网安备案号 |
| logoIcon | logo_icon | String | Logo 图标，一般为不带文字正方形或圆形小图标 |
| features | features | String | 特点、亮点，JSON 类型列 |
| logo | logo | String | Logo，一般带文字，区别于标志图标 |
| icpFilingNo | icp_filing_no | String | ICP 备案号 |
| lang | lang | String | 语言，如 `cn`（中文）、`en`（英文） |
| welcome | welcome | String | 欢迎语 |
| slogan | slogan | String | 口号标语，可用于首页 title |
| domain | domain | String | 站点配置绑定的域名，格式如 `www.xxx.com` |
| enableMultiLang | enable_multi_lang | boolean | 启动多语言，默认 false |
| ssoUrl | sso_url | String | 单点登录地址 |
| mainUrl | main_url | String | 后端接口地址 |
| forgetPwdUrl | forget_pwd_url | String | 忘记密码页面地址 |
| wxDomain | wx_domain | String | 微信开放平台回调域名 |
| wxLoginType | wx_login_type | String | 微信登录方式：微信 `default`；公众号 `gzh` |
| wxAppId | wx_app_id | String | 微信 appId |
| wxAppSecret | wx_app_secret | String | 微信 appSecret |
| wxGzhAppId | wx_gzh_app_id | String | 微信公众号 appId |
| wxGzhAppSecret | wx_gzh_app_secret | String | 微信公众号 appSecret |
| unSaveConfig | un_save_config | String | 不保存至配置文件的字段 |

## 推荐继续阅读

- [角色与权限](role-permission.md)——用户与组织、角色的挂靠关系表
- [认证鉴权](../authentication/security-authentication.md)——一次请求如何认证出 User 主体
- [安全契约概览](../security/overview.md)——User/Org/Role 在安全模型中的读取契约
