---
title: API · 企业微信
sidebar_label: 企业微信
---
# 企业微信（weixin）

1 个 Controller，共 5 个端点。标注 ⚙ 的为设计态接口。

## WeixinWorkController — `/weixin/work`

企业微信集成：用户绑定同步、客户群组列表与详情、部门与部门用户查询。企业微信配置（corpId/corpSecret）从公司扩展信息读取。

### syncContacts 同步用户绑定

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/weixin/work/syncUser` |
| 参数 | `orgId`：query，string，必填，组织 ID（读取该公司企业微信配置）。 |
| 返回 | `ApiResult`：data 为空，`msg` 为同步结果（成功同步 N 个用户）；为手机号已登记但未绑定企业微信账号的用户按手机号回填企业微信 UserID；配置缺失、格式错误时返回失败。 |

### queryGroup 客户群列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/weixin/work/queryGroup` |
| 参数 | `userId`：query，string，可选，用户 ID；缺省取当前登录用户，且非管理员时仅返回其作为群主的群。 |
| 返回 | `ApiResult<List<Map<String,Object>>>`：data 为群组摘要列表，每项含 `chat_id`、`name`、`owner`；结果按公司、绑定用户、是否按群主过滤缓存 12 小时；用户未绑定企业微信、公司未配置或微信接口失败时返回失败。 |

### getGroupDetail 群组详情

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/weixin/work/getGroupDetail` |
| 参数 | `chatId`：query，string，必填，群聊 ID。<br />`userId`：query，string，可选，用户 ID（用于确定公司，缺省取当前登录用户所属公司）。 |
| 返回 | `ApiResult<Map<String,Object>>`：data 含 `errcode`、`errmsg`，成功时含 `group_chat`（微信返回的群详情，含群名称等）；公司未配置时返回失败。 |

### queryOrg 部门列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/weixin/work/queryOrg` |
| 参数 | `parentOrgId`：query，string，必填，组织 ID（读取该公司企业微信配置）。 |
| 返回 | `ApiResult`：data 为微信返回的部门列表（`department`）；配置缺失或微信返回失败时返回失败。 |

### queryUserByOrg 部门用户列表

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/weixin/work/queryUserByOrg` |
| 参数 | `departmentId`：query，string，必填，企业微信部门 ID。<br />`fetchChild`：query，int，可选，默认 1，是否递归获取子部门用户。 |
| 返回 | `ApiResult`：data 为微信返回的部门用户列表（`userlist`）；微信返回失败时返回失败。 |
