# 平台通知 / 消息投递文档

| 文档 | 说明 |
|---|---|
| [消息投递集成指南](./integration-guide.md) | geelato-message 如何投递消息（含新增站内信渠道）、community 站内信如何被投递与消费、两者关系 |

## 架构速览

- **geelato-message** 是统一消息中心，负责所有消息（邮件/短信/企业微信/站内信）的投递编排与可靠性。
- **站内信（inapp）** 与邮件平级，是 geelato-message 的一个投递渠道；投递时调用 community 的 `/api/notification/send` 落地。
- **community 站内信** 是独立、自包含的能力（主体表、收件箱、SSE、查询），与 geelato-message 无强耦合。

## 快速定位

- **我要让 geelato-message 投递站内信** → [1.2 新增站内信渠道（7 处改动）](./integration-guide.md#12-新增一个投递渠道以站内信-inapp为例)
- **我要调用 geelato-message 发任意消息** → [1.1 入队接口](./integration-guide.md#11-投递一条消息调用方视角)
- **我在 community 业务代码里发站内信** → [2.1 内部投递（事件/REST）](./integration-guide.md#21-community-自身如何投递站内信)
- **我要查询/消费收件箱** → [2.2 收件箱接口](./integration-guide.md#22-收件箱消费查询方)
- **geelato-message 和 community 站内信什么关系** → [第三部分](./integration-guide.md#第三部分两者的关系关键认知)
