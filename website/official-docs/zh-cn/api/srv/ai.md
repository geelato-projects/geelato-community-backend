---
title: API · AI 助手
sidebar_label: AI 助手
---
# AI 助手（ai）

1 个 Controller，共 1 个端点。标注 ⚙ 的为设计态接口。

## AiController — `/ai`

AI 问答：将提问内容交由大模型处理，以 SSE 流式返回应答。

### ask AI 问答流式应答

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/ai/ask` |
| 参数 | body：`Map<String,String>`，键 `content`，提问内容。 |
| 返回 | 直接写响应流（无 JSON body）：SSE 流式返回（content-type `text/event-stream`），逐段推送 AI 应答内容；返回值类型为 `SseEmitter`（超时 30 分钟）。 |
