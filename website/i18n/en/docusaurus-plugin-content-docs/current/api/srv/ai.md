---
title: API · AI Assistant
sidebar_label: AI Assistant
---
# AI Assistant (ai)

1 controller with 1 endpoint in total. Controllers marked with ⚙ are design-time APIs.

## AiController — `/ai`

AI Q&A: sends the question to a large language model and streams the answer back over SSE.

### ask Streaming AI Q&A

| Item | Content |
| --- | --- |
| URL | `POST /api/ai/ask` |
| Params | body: `Map<String,String>`, key `content`, the question text. |
| Returns | Writes directly to the response stream (no JSON body): SSE streaming response (content-type `text/event-stream`), pushing the AI answer chunk by chunk; return type is `SseEmitter` (30-minute timeout). |
