---
title: 在线调试
sidebar_label: 在线调试
---

# 在线调试

> ⚠️ **实验性功能**：在线调试处于实验阶段，界面与体验待完善，接口与行为可能调整，暂不建议在生产环境依赖。

geelato-script 基于 GraalJS 内置的 CDP inspector 提供断点调试：为一组脚本内容启动**调试会话**，用 Chrome DevTools 连接后即可断点、单步、查看变量。调试执行与正常执行共用同一条 kit/变量/环境注入链，保证**调试副作用与线上一致**（写通道 dry-run 恒生效）。

## 快速上手：调试台

宿主开启 `geelato.script.rest.enabled=true` 后，访问 `GET /script-playground.html` 打开内置调试台：

- **调试台**：编辑脚本、传参执行、语法预检、浏览 kit 目录（自动补全数据源）；
- **调试会话**：以调试台中的脚本与参数创建/停止调试会话，查看会话状态与 inspector 地址。

## REST 端点

调试端点随 `geelato.script.rest.enabled` 装配，仅限内网可信环境：

| 端点 | 说明 |
| :--- | :--- |
| `POST /api/script/debug/start` | 创建调试会话，入参与 `/api/script/execute` 对齐（`lang`/`content`/`scriptName`/`engineVersion`/`profileId`/`params`/`bindings`），另可指定 `debugSessionId`（空由引擎分配）、`debugOwner`（会话归属，供代理鉴权）、`debugPort`（空/0 自动分配空闲端口）。返回 `DebugSession` |
| `POST /api/script/debug/stop/{sessionId}` | 停止会话，强关 Context 并释放 inspector 端口（幂等） |
| `GET /api/script/debug/{sessionId}/status` | 查询会话状态 |

`DebugSession` 返回字段：`sessionId`、`host`（恒 `127.0.0.1`，inspector 只绑回环，绝不公网暴露）、`port`、`path`（= sessionId，多会话经路径区分）、`owner`、`engineVersion`。面向浏览器的同源网关（如设计器）应剥离 `host`/`port`，只向浏览器下发代理路径。

```bash
curl -X POST http://127.0.0.1:8080/api/script/debug/start \
  -H 'Content-Type: application/json' \
  -d '{"scriptName":"EXC-BL-005","engineVersion":"v1",
       "params":{"days":90},
       "content":"const rows = $gl.sql.query(\"...\", {});\nreturn rows.length;"}'
# → {"sessionId":"a1b2...","host":"127.0.0.1","port":41235,"path":"a1b2...","owner":null,"engineVersion":"v1"}
```

## 连接 Chrome DevTools

调试会话创建后脚本**挂起等待**（`WaitAttached` + `Suspend`：DevTools 连接并 resume 前不执行）：

1. 打开 Chrome，访问 `chrome://inspect`；
2. **Configure** 添加目标地址 `127.0.0.1:{port}`（端口来自 `DebugSession`）；
3. 稍候出现 Remote Target，点击 **inspect** 打开 DevTools；
4. 在 Sources 面板定位调试源码 `gl-debug://<scriptName>`，行号与编辑器 **1:1 对应**，直接下断点后 resume。

`scriptName` 作为调试 Source 名，同一脚本重复调试时断点可持久。

## 会话状态机

| 状态 | 含义 |
| :--- | :--- |
| WAITING | 已创建，等待 DevTools 连接 |
| RUNNING | DevTools 已连接，执行中 |
| EXITED | 脚本自然执行结束（Context 已关闭） |
| STOPPED | 被 `stop` 主动停止，或会话不存在 |

`GET /api/script/debug/{sessionId}/status` 额外返回 `alive`（会话线程是否存活）与 `error`/`errorMessage`（执行异常信息）。

## 调试语义

- **仅支持 javascript**：`lang` 非 js 时启动报错；
- **与线上一致**：复用正常执行的 kit 注入、变量注入（`$ctx`/`$params`，受场景档案白名单过滤）与 `$env` 组装；写通道 dry-run 恒生效（写语句在事务内执行后回滚）；
- **资源上限仍生效**：语句数上限（`statement-limit`）防死循环；
- **不受同步超时约束**：调试是长会话，`timeoutMs` 仅注入 `$env` 供脚本读取，不会像正常执行那样超时强杀——记得调试完 `stop` 释放端口；
- 引擎版本语义同正常执行：RETIRED 版本拒绝创建会话。
