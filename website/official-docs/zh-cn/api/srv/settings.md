---
title: API · 应用设置
sidebar_label: 应用设置
---
# 应用设置（settings）

1 个 Controller，共 3 个端点。标注 ⚙ 的为设计态接口。

## SettingMessageController — `/app/settings/message`

平台消息记录的查询设置：面向 `Message` 实体（应用 ID、标题、内容、接收者、发送者、发送时间、发送方式、发送类别、发送状态等字段）提供分页查询、列表查询与已读标记占位接口。

### pageQuery 消息分页查询

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/settings/message/pageQuery` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiPagedResult`：`data` 为消息（`Message`）分页数据。 |

### query 消息列表查询

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/app/settings/message/query` |
| 参数 | 分页与过滤参数见[公共约定](../reference.md) |
| 返回 | `ApiResult<List<Message>>`：消息列表，按请求的排序规则返回。 |

### read 消息已读标记（占位）

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/app/settings/message/read` |
| 参数 | 无 |
| 返回 | `ApiResult<Message[]>`：占位实现，`data` 恒为空数组；实际的已读处理逻辑尚未实现。 |
