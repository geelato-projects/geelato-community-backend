---
title: API · Notifications
sidebar_label: Notifications
---
# Notifications (notification)

1 controller with 9 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## NotificationController — `/notification`

Platform notification center: inbox paging for the current user, unread-count badge, read/star/archive/delete, proactive sending and recall. Importance has four levels: 0 normal, 1 reminder, 2 important, 3 urgent.

### pageQuery Inbox page query

| Item | Content |
| --- | --- |
| URL | `POST /api/notification/pageQuery` |
| Params | `readStatus`: body, int, optional, read-status filter (0 unread, 1 read).<br />`archived`: body, int, optional, archive filter (0/1).<br />`bizType`: body, string, optional, business-type filter (e.g. order/contract/task).<br />`keyword`: body, string, optional, fuzzy search over title or content (OR semantics).<br />`title`: body, string, optional, fuzzy search over the title only; when passed together with `keyword` the semantics are an intersection.<br />`priorityGe`: body, int, optional, importance lower bound (0-3, >= the given level).<br />`orderByPriority`: body, boolean, optional, when `true` sorts by importance from high to low (time descending within the same level); time descending by default.<br />Pagination and filter params: see [Conventions](../reference.md) (page size is clamped to [1,100]). |
| Returns | `ApiPagedResult`: `data` is a flat row set of your inbox (recipient status joined with the notification subject, including title, content, action URL `actionUrl`, etc.). |

### unreadCount Unread count

| Item | Content |
| --- | --- |
| URL | `GET /api/notification/unread-count` |
| Params | `priorityGe`: query, int, optional, importance lower bound (0-3); e.g. `priorityGe=2` counts unread items that are "important and above". |
| Returns | `ApiResult<Long>`: unread notification count of the current user (bell badge). |

### markRead Mark one as read

| Item | Content |
| --- | --- |
| URL | `POST /api/notification/read/{id}` |
| Params | `id`: path, string, inbox record ID. |
| Returns | `ApiResult<Boolean>`: `data` indicates whether the marking succeeded; validated against inbox ownership. |

### markAllRead Mark all as read

| Item | Content |
| --- | --- |
| URL | `POST /api/notification/read-all` |
| Params | None |
| Returns | `ApiResult<Integer>`: `data` is the number of records marked as read. |

### star Toggle star

| Item | Content |
| --- | --- |
| URL | `POST /api/notification/star/{id}` |
| Params | `id`: path, string, inbox record ID.<br />`value`: body, int, optional, 1 to star, 0 to unstar; default 1. |
| Returns | `ApiResult<Boolean>`: `data` indicates whether the update succeeded. |

### archive Toggle archive

| Item | Content |
| --- | --- |
| URL | `POST /api/notification/archive/{id}` |
| Params | `id`: path, string, inbox record ID.<br />`value`: body, int, optional, 1 to archive, 0 to unarchive; default 1. |
| Returns | `ApiResult<Boolean>`: `data` indicates whether the update succeeded. |

### delete Delete inbox notification

| Item | Content |
| --- | --- |
| URL | `DELETE /api/notification/delete/{id}` |
| Params | `id`: path, string, inbox record ID. |
| Returns | `ApiResult<Boolean>`: `data` indicates whether the deletion succeeded; only removes your own inbox record and does not affect other recipients. |

### send Send notification

| Item | Content |
| --- | --- |
| URL | `POST /api/notification/send` |
| Params | `recipients`: body, `List<String>`, required, list of recipient userIds.<br />`channels`: body, `List<String>`, optional, delivery channels; defaults to in-app only `[inapp]` when empty.<br />`title`: body, string, title.<br />`content`: body, string, content.<br />`senderId`: body, string, optional, sender userId; falls back to the current logged-in user, or `system` when there is no login context.<br />`senderName`: body, string, optional, sender name.<br />`senderType`: body, string, optional, sender type; defaults to `USER`, set it to `SYSTEM` explicitly for system triggers.<br />`bizType`: body, string, optional, business type for business idempotency and classification.<br />`bizId`: body, string, optional, business key; combined with `bizType` it forms the idempotency key.<br />`actionUrl`: body, string, optional, click-through URL.<br />`priority`: body, int, optional, importance: 0 normal (default)/1 reminder/2 important/3 urgent; invalid values are rejected. |
| Returns | `ApiResult<String>`: `data` is the created notification subject ID. Dispatch orchestration: writes the subject and the outbox per channel. Callable by external systems (`Authorization: SystemToken <fixed key>`). |

### recall Recall notification

| Item | Content |
| --- | --- |
| URL | `POST /api/notification/recall/{id}` |
| Params | `id`: path, string, notification subject ID. |
| Returns | `ApiResult<Boolean>`: `data` indicates whether the recall succeeded; logically deletes the notification subject (del_status), which invalidates every user's inbox association, so inbox queries no longer show it. Fails when the notification does not exist. |
