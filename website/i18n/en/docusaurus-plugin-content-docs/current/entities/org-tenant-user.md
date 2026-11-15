---
title: Organization & Users
sidebar_label: Organization & Users
---

# Organization & Users

This page covers 6 entities across organizations, companies, users, administrative divisions and tenants. User is the platform account principal; Org forms the organization tree; Company carries legal-entity company information and links to organizations; Tenant/TenantSite carry multi-tenancy and site configuration. For the read-side view of organizations and users in the security contract, see [Security Contract Overview](../security/overview.md).

## Org

Organization tree node. Extends `BaseSortableEntity`; common fields are listed in [Overview](overview.md#common-base-class-fields).

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| name | name | String | Organization name |
| code | code | String | Organization code |
| pid | pid | String | Parent organization code |
| type | type | String | Organization type |
| category | category | String | Organization category |
| status | status | int | Organization status |
| description | description | String | Organization description |
| extendId | extend_id | String | Organization form extension |

## Company

Legal-entity company information, linked to an organization (Org), with parent-company hierarchy. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| pid | pid | String | Parent company |
| name | name | String | Name |
| code | code | String | Number |
| remark | remark | String | Remark |
| shortName | short_name | String | Short name |
| enName | en_name | String | English name |
| address | address | String | Address |
| countryCode | country_code | String | Country/region |
| standardCurrency | standard_currency | String | Standard currency |
| enAddress | en_address | String | English address |
| localLanguage | local_language | String | Local language |
| cooperatingOrgId | cooperating_org_id | String | Bound business partner |
| orgId | org_id | String | Organization id, referencing Org |
| weixinWorkInfo | weixin_work_info | String | WeCom (WeChat Work) configuration |

## User

The platform user account principal: login credentials, contact info, account type and source, organization and administrative division. Extends `BaseSortableEntity`, implements `EntityEnableAble`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| name | name | String | Name |
| enName | en_name | String | English name |
| loginName | login_name | String | Login name, non-null |
| sex | sex | int | Gender |
| orgId | org_id | String | Organization, `@ForeignKey` to Org (platform_org) |
| password | password | String | Password (salted hash) |
| salt | salt | String | Salt |
| avatar | avatar | String | Avatar |
| mobilePrefix | mobile_prefix | String | Mobile prefix, length 16 |
| mobilePhone | mobile_phone | String | Mobile phone, length 16 |
| telephone | telephone | String | Telephone |
| email | email | String | Email |
| post | post | String | Position |
| type | type | int | Type: 0 employee account, 1 system account, 2 external personnel, 3 virtual employee account |
| source | source | int | Source: 0 local user, 1 system-synced |
| nationCode | nation_code | String | Country |
| provinceCode | province_code | String | Province |
| cityCode | city_code | String | City |
| address | address | String | Detailed address |
| description | description | String | Description |
| orgName | orgName | String | Department, reference column (isRefColumn, fetched from platform_org.name by orgId; the source @Col name is camel-case `orgName`) |
| jobNumber | job_number | String | Job number |
| cooperatingOrgId | cooperating_org_id | String | Partner unit id |
| enableStatus | enable_status | int | Enable status: 1 enabled, 0 disabled |
| weixinUnionId | weixin_unionId | String | WeChat union id |
| weixinWorkUserId | weixin_work_userId | String | WeCom user id |
| plainPassword | — | String | `@Transient` plain password, not persisted and not shown in RESTful properties |
| stocked | — | boolean | `@Transient`, not persisted |
| orgUserPost | — | String | `@Transient`, not persisted |

## Area

Administrative division (province/city/district) reference data. Extends `BaseSortableEntity`, no catalog specified.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| name | name | String | Name |
| code | code | String | Code |
| cityId | city_id | String | Province code, non-null. The source column `city_id` is misaligned with its title "province"; documented here by actual semantics |
| description | description | String | Description |

## Tenant

Tenant definition. Its class title is mislabeled as "租户站点" (tenant site, duplicating TenantSite); documented here by actual semantics. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| code | code | String | The enterprise code, i.e. the tenant code |
| companyName | company_name | String | Company name |
| companyDomain | company_domain | String | Company domain |
| mainEmail | main_email | String | Company email |
| mainEmailPwd | main_email_pwd | String | Company email password |
| corpId | corp_id | String | Company WeCom corpId |
| corpToken | corp_token | String | Company WeCom corpToken |

## TenantSite

Tenant site configuration: site appearance, filing numbers, domain, multi-language, SSO and WeChat login integration. Extends `BaseEntity`.

| Property | Column | Type | Description |
| --- | --- | --- | --- |
| name | name | String | Site name, usually the enterprise name |
| copyright | copyright | String | Copyright statement (source @Title mis-copied as "usually the enterprise name") |
| nsFilingNo | ns_filing_no | String | Public security network filing number |
| logoIcon | logo_icon | String | Logo icon, usually a small square or circular icon without text |
| features | features | String | Features and highlights, JSON column |
| logo | logo | String | Logo, usually the version with text, distinct from the icon |
| icpFilingNo | icp_filing_no | String | ICP filing number |
| lang | lang | String | Language, e.g. `cn` (Chinese), `en` (English) |
| welcome | welcome | String | Welcome message |
| slogan | slogan | String | Slogan, usable in the home page title |
| domain | domain | String | Domain bound to this site configuration, e.g. `www.xxx.com` |
| enableMultiLang | enable_multi_lang | boolean | Enable multi-language, defaults to false |
| ssoUrl | sso_url | String | SSO address |
| mainUrl | main_url | String | Backend API address |
| forgetPwdUrl | forget_pwd_url | String | Forgot-password page address |
| wxDomain | wx_domain | String | WeChat open-platform callback domain |
| wxLoginType | wx_login_type | String | WeChat login type: WeChat `default`; official account `gzh` |
| wxAppId | wx_app_id | String | WeChat appId |
| wxAppSecret | wx_app_secret | String | WeChat appSecret |
| wxGzhAppId | wx_gzh_app_id | String | WeChat official account appId |
| wxGzhAppSecret | wx_gzh_app_secret | String | WeChat official account appSecret |
| unSaveConfig | un_save_config | String | Fields not saved to the configuration file |

## Recommended Reading

- [Roles & Permissions](role-permission.md) — the relation tables attaching users to organizations and roles
- [Authentication & Authorization](../authentication/security-authentication.md) — how a request authenticates into a User principal
- [Security Contract Overview](../security/overview.md) — how User/Org/Role are read in the security model
