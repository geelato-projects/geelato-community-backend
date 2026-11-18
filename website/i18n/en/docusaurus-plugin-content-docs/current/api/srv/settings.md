---
title: API · App Settings
sidebar_label: App Settings
---
# App Settings (settings)

1 controller with 3 endpoints in total. Controllers marked with ⚙ are design-time APIs.

## SettingMessageController — `/app/settings/message`

Query settings for platform message records: paging, listing and a read-marker placeholder over the `Message` entity (fields: app ID, title, content, receiver, sender, send time, send method, send type, send status, etc.).

### pageQuery Page query

| Item | Content |
| --- | --- |
| URL | `POST /api/app/settings/message/pageQuery` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiPagedResult`: `data` is the paged messages (`Message`). |

### query List query

| Item | Content |
| --- | --- |
| URL | `GET /api/app/settings/message/query` |
| Params | Pagination and filter params: see [Conventions](../reference.md) |
| Returns | `ApiResult<List<Message>>`: the list of messages, ordered by the requested sort rule. |

### read Read marker (placeholder)

| Item | Content |
| --- | --- |
| URL | `POST /api/app/settings/message/read` |
| Params | None |
| Returns | `ApiResult<Message[]>`: placeholder implementation, `data` is always an empty array; the actual read-marking logic is not implemented yet. |
