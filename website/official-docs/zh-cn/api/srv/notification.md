---
title: API · 站内通知
sidebar_label: 站内通知
---
# 站内通知（notification）

1 个 Controller，共 9 个端点。标注 ⚙ 的为设计态接口。

## NotificationController — `/notification`

平台通知中心：当前用户收件箱分页查询、未读数角标、已读/星标/归档/删除、主动发送与撤回。重要级别分四级：0 普通、1 提醒、2 重要、3 紧急。

### pageQuery 收件箱分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/notification/pageQuery` |
| 参数 | `readStatus`：body，int，可选，已读状态过滤（0 未读、1 已读）。<br />`archived`：body，int，可选，归档过滤（0/1）。<br />`bizType`：body，string，可选，业务类型过滤（如 order/contract/task）。<br />`keyword`：body，string，可选，标题或内容模糊检索（两者 OR 语义）。<br />`title`：body，string，可选，仅标题模糊检索；与 `keyword` 同传时为交集语义。<br />`priorityGe`：body，int，可选，重要级别下限（0-3，>= 该级别）。<br />`orderByPriority`：body，boolean，可选，为 `true` 时按级别从高到低排序（同级别内时间倒序），默认时间倒序。<br />分页与过滤参数见[公共约定](../reference.md)（页大小钳制为 [1,100]）。 |
| 返回 | `ApiPagedResult`：`data` 为当前用户收件箱扁平行（收件人状态与通知主体联查结果，含标题、内容、跳转地址 `actionUrl` 等）。 |

### unreadCount 未读数

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/notification/unread-count` |
| 参数 | `priorityGe`：query，int，可选，重要级别下限（0-3）；如传 2 统计「重要及以上」未读数。 |
| 返回 | `ApiResult<Long>`：当前用户未读通知数（铃铛角标）。 |

### markRead 标记单条已读

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/notification/read/{id}` |
| 参数 | `id`：path，string，通知收件记录 ID。 |
| 返回 | `ApiResult<Boolean>`：`data` 表示是否标记成功；带收件人归属校验。 |

### markAllRead 全部标记已读

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/notification/read-all` |
| 参数 | 无 |
| 返回 | `ApiResult<Integer>`：`data` 为本次标记已读的记录数。 |

### star 星标切换

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/notification/star/{id}` |
| 参数 | `id`：path，string，通知收件记录 ID。<br />`value`：body，int，可选，1 星标、0 取消星标，默认 1。 |
| 返回 | `ApiResult<Boolean>`：`data` 表示是否更新成功。 |

### archive 归档切换

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/notification/archive/{id}` |
| 参数 | `id`：path，string，通知收件记录 ID。<br />`value`：body，int，可选，1 归档、0 取消归档，默认 1。 |
| 返回 | `ApiResult<Boolean>`：`data` 表示是否更新成功。 |

### delete 删除收件通知

| 项 | 内容 |
| --- | --- |
| URL | `DELETE /api/notification/delete/{id}` |
| 参数 | `id`：path，string，通知收件记录 ID。 |
| 返回 | `ApiResult<Boolean>`：`data` 表示是否删除成功；仅删除当前用户的收件记录，不影响其他收件人。 |

### send 发送通知

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/notification/send` |
| 参数 | `recipients`：body，`List<String>`，必填，收件人 userId 列表。<br />`channels`：body，`List<String>`，可选，投递渠道，为空时默认仅站内信 `[inapp]`。<br />`title`：body，string，标题。<br />`content`：body，string，内容。<br />`senderId`：body，string，可选，发送者 userId，为空取当前登录用户，无登录上下文则为 system。<br />`senderName`：body，string，可选，发送者名称。<br />`senderType`：body，string，可选，发送者类型，默认 `USER`；系统触发应显式设为 `SYSTEM`。<br />`bizType`：body，string，可选，业务类型，用于业务幂等与归类。<br />`bizId`：body，string，可选，业务主键，配合 `bizType` 构成幂等键。<br />`actionUrl`：body，string，可选，点击跳转地址。<br />`priority`：body，int，可选，重要级别 0 普通（默认）/1 提醒/2 重要/3 紧急，非法值拒绝。 |
| 返回 | `ApiResult<String>`：`data` 为创建的通知主体 ID。走 dispatch 编排：写通知主体并按渠道写发件箱。支持外部系统调用（`Authorization: SystemToken <固定密钥>`）。 |

### recall 撤回通知

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/notification/recall/{id}` |
| 参数 | `id`：path，string，通知主体 ID。 |
| 返回 | `ApiResult<Boolean>`：`data` 表示是否撤回成功；逻辑删除通知主体（del_status），删除后全员收件箱关联失效，收件箱查询因关联主体而不再展示。通知不存在时返回失败。 |
