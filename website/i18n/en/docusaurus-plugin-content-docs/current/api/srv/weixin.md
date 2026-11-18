---
title: API · WeCom
sidebar_label: WeCom
---
# WeCom (weixin)

1 controller with 5 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## WeixinWorkController — `/weixin/work`

WeCom (WeChat Work) integration: user binding sync, customer group list and detail, department and department-user queries. The WeCom configuration (corpId/corpSecret) is read from the company's extended info.

### syncContacts Sync user bindings

| Item | Content |
| --- | --- |
| URL | `GET /api/weixin/work/syncUser` |
| Params | `orgId`: query, string, required, org ID (reads the company's WeCom configuration). |
| Returns | `ApiResult`: `data` is empty and `msg` carries the result message ("N users synced"); for users whose mobile phone is registered but who have no WeCom user ID yet, the WeCom UserID is resolved by mobile phone and written back; fails when the configuration is missing or malformed. |

### queryGroup Customer group list

| Item | Content |
| --- | --- |
| URL | `GET /api/weixin/work/queryGroup` |
| Params | `userId`: query, string, optional, user ID; defaults to the current user, and for non-admins only groups owned by that user are returned. |
| Returns | `ApiResult<List<Map<String,Object>>>`: `data` is the list of group summaries, each with `chat_id`, `name`, `owner`; results are cached for 12 hours keyed by company, bound user and owner-filter flag; fails when the user is not bound to WeCom, the company is not configured, or the WeCom API call fails. |

### getGroupDetail Get group detail

| Item | Content |
| --- | --- |
| URL | `GET /api/weixin/work/getGroupDetail` |
| Params | `chatId`: query, string, required, group chat ID.<br />`userId`: query, string, optional, user ID (determines the company; defaults to the current user's company). |
| Returns | `ApiResult<Map<String,Object>>`: `data` contains `errcode` and `errmsg`, plus `group_chat` (the group detail returned by WeCom, including the group name) on success; fails when the company is not configured. |

### queryOrg Department list

| Item | Content |
| --- | --- |
| URL | `GET /api/weixin/work/queryOrg` |
| Params | `parentOrgId`: query, string, required, org ID (reads the company's WeCom configuration). |
| Returns | `ApiResult`: `data` is the department list returned by WeCom (`department`); fails when the configuration is missing or the WeCom API call fails. |

### queryUserByOrg Department user list

| Item | Content |
| --- | --- |
| URL | `GET /api/weixin/work/queryUserByOrg` |
| Params | `departmentId`: query, string, required, WeCom department ID.<br />`fetchChild`: query, int, optional, defaults to 1, whether to recursively include users of sub-departments. |
| Returns | `ApiResult`: `data` is the department user list returned by WeCom (`userlist`); fails when the WeCom API call fails. |
