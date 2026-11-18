---
title: API · 公告
sidebar_label: 公告
---
# 公告（announcement）

1 个 Controller，共 2 个端点。标注 ⚙ 的为设计态接口。

## AnnouncementController — `/announcement`

登录弹窗公告机制：公告源为配置目录（`geelato.announcement.dir`）内的 markdown 文件（通常由 git 定时拉取维护），返回目录内日期最新一份公告原文；已读状态由前端 localStorage（`{公告id}_{userId}_read`）维护，服务端不落库。

### current 查询当前最新公告

| 项 | 内容 |
| --- | --- |
| URL | `GET /api/announcement/current` |
| 参数 | 无 |
| 返回 | `ApiResult<AnnouncementInfo>`：`data` 为当前最新公告（`id` 随机数主键即文件名首段、`title` 标题即文件名中段、`date` 公告日期 yyyy-MM-dd、`fileName` 公告文件名、`content` markdown 原文，由前端渲染）；无公告时 `data` 为 null。每次调用轻量重扫目录，git 拉取新公告后无需重启即生效。 |

### refresh 强制刷新公告

| 项 | 内容 |
| --- | --- |
| URL | `POST /api/announcement/refresh` |
| 参数 | 无 |
| 返回 | `ApiResult<AnnouncementInfo>`：`data` 为刷新后的最新公告（结构同上，null 表示暂无）；清空缓存立即重扫目录。供 git 拉取后的运维脚本调用（`Authorization: SystemToken <固定密钥>`，密钥配置 `geelato.security.system-token.token`，可用环境变量 `GEELATO_SYSTEM_TOKEN` 覆盖）。 |
