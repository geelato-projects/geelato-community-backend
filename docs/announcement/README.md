# 平台公告（announcement）

轻量公告机制：公告是 git 仓库根目录下的 markdown 文件，平台**直接读取远程仓库**（JGit
内存仓库拉取，不落盘、无本地目录），取其中**日期最新的一份**作为当前公告，缓存在内存中；
用户登录后前端自动弹出渲染，已读状态记在客户端 localStorage，服务端不落库。

## 公告文件规范

仓库内每个 `.md` 文件即一份公告，**文件名**必须符合：

```
随机数主键_标题_日期.md
```

| 段 | 说明 |
| --- | --- |
| 随机数主键 | 公告唯一标识，建议 UUID 或毫秒时间戳；不可含下划线 `_` |
| 标题 | 公告标题，可含中文、空格，也可含下划线（按最后一段日期切分） |
| 日期 | `yyyy-MM-dd` 或 `yyyyMMdd`，如 `2026-09-11` / `20260911` |

示例：

```
1726041600000_平台将于本周末升级_2026-09-11.md
f81d4fae-7dec-11d0-a765-00a0c91e6bf6_新版操作手册上线_20260901.md
```

规则：

- "最新"= 日期段最大；日期相同按文件修改时间，仍相同按随机数主键字典序（保证确定性）。
- 文件名不合规的 `.md` 文件**记 ERROR 日志后跳过**，不影响平台与其他公告的评选。
- 文件内容即 markdown 正文，UTF-8 编码。

## git 直连配置

```properties
# geelato-web-quickstart/src/main/resources/application.properties
geelato.announcement.git.repo-uri=${GEELATO_ANNOUNCEMENT_GIT_REPO_URI:https://gitea.geelato.cn/geelato-projects/system-announcement}
geelato.announcement.git.branch=${GEELATO_ANNOUNCEMENT_GIT_BRANCH:main}
geelato.announcement.git.username=${GEELATO_ANNOUNCEMENT_GIT_USERNAME:}
geelato.announcement.git.password=${GEELATO_ANNOUNCEMENT_GIT_PASSWORD:}
```

| 配置键 | 说明 |
| --- | --- |
| `geelato.announcement.git.repo-uri` | 仓库地址，**必配**（仅支持 https 协议；为空时启动记 ERROR，无公告） |
| `geelato.announcement.git.branch` | 分支，默认 `main` |
| `geelato.announcement.git.username` | 私有仓库用户名（公开仓库留空） |
| `geelato.announcement.git.password` | 私有仓库访问令牌 PAT（公开仓库留空；建议环境变量注入，勿落明文） |

## 读取机制

平台内嵌 JGit（`AnnouncementGitSynchronizer`），fetch 到**内存仓库**后遍历提交树读取
根目录 `.md` 文件内容，全程无磁盘文件、无本地工作目录：

- **启动时**：拉取一次，解析评选后缓存在内存，并记日志。
- **手动刷新**：`POST /api/announcement/refresh` 触发一次重新拉取并更新内存缓存（见下）。
- **登录读取**：`GET /api/announcement/current` 只读内存缓存，不出网、无 IO。
- **失败语义**：不做降级——启动拉取失败记 ERROR 日志（含原因栈）后继续启动（无公告）；
  refresh 失败则接口返回失败，调用方明确感知。密码不会出现在日志中。

发布新公告 = 仓库里提交一份日期更新的 md 文件并推送；平台重启或调 refresh 即生效。

## REST 接口

### GET /api/announcement/current

需登录（默认鉴权拦截）。返回内存缓存的最新公告（启动/refresh 时从 git 仓库拉取）：

```json
{
  "status": true,
  "code": 20000,
  "msg": "Success",
  "data": {
    "id": "1726041600000",
    "title": "平台将于本周末升级",
    "date": "2026-09-11",
    "fileName": "1726041600000_平台将于本周末升级_2026-09-11.md",
    "content": "# 升级通知\n\n本周末 02:00-06:00 停机升级……"
  }
}
```

- 无公告时 `data` 为 `null`。
- `content` 为 markdown 原文，由前端渲染（marked）。

### POST /api/announcement/refresh

强制刷新：从 git 仓库重新拉取（内存仓库，不落盘），解析评选后更新内存缓存，
返回刷新后的最新公告（结构同 `/current`，`data` 为 `null` 表示暂无）。

鉴权与站内信发送（`POST /api/notification/send`）一致——外部固定令牌，方法标注
`@AllowSystemAccess`，请求头：

```
Authorization: SystemToken <固定密钥>
```

密钥配置 `geelato.security.system-token.token`（默认取内置密钥，生产环境建议用环境变量
`GEELATO_SYSTEM_TOKEN` 注入随机长串覆盖）：

```properties
geelato.security.system-token.token=${GEELATO_SYSTEM_TOKEN}
```

调用示例：

```bash
curl -X POST http://127.0.0.1:8080/api/announcement/refresh \
  -H "Authorization: SystemToken <固定密钥>"
```

## 前端已读机制

前端（gl-admin-core-arco 共享包，dt-std / rt-std 两应用同时生效）在登录态就绪后：

1. **一次登录只请求一次** `GET /api/announcement/current`（SPA 内由 store 幂等保证；
   整页跳转由 sessionStorage 的登录 token 标记去重，重新登录/新开标签页才会再请求）；
2. `data` 非空且 `localStorage["{公告id}_{userId}_read"] !== "true"` 时弹出公告弹窗（markdown 渲染）；
3. 用户点击"我已阅读"后写入 `localStorage["{公告id}_{userId}_read"] = "true"`；
4. 之后该用户在此客户端登录不再弹出；换新公告（id 变化）或换账号（userId 变化）会重新弹出。

Esc / 关闭按钮只隐藏弹窗、**不标记已读**，下次登录会再次弹出，确保用户读过。

## scaffold 应用

公告能力**不进入 scaffold 裁剪型应用**：AnnouncementController 未登记到
`AppScaffoldControllerCatalog` 白名单，脚手架应用启动时会被 EndpointPruner 自动裁剪。
公告仅在完整平台（如 geelato-web-quickstart）生效。

## 代码位置

| 层 | 位置 |
| --- | --- |
| Controller | `geelato-web-platform/.../srv/announcement/AnnouncementController.java` |
| Service | `geelato-web-platform/.../srv/announcement/AnnouncementService.java` |
| git 同步 | `geelato-web-platform/.../srv/announcement/AnnouncementGitSynchronizer.java` |
| git 配置 | `geelato-web-platform/.../srv/announcement/AnnouncementGitProperties.java` |
| DTO | `geelato-web-platform/.../srv/announcement/dto/AnnouncementInfo.java` |
| 前端 store | geelato-front `packages/gl-admin-core-arco/src/store/modules/announcement/index.ts` |
| 前端弹窗 | geelato-front `packages/gl-admin-core-arco/src/components/announcement/announcement-modal.vue` |
