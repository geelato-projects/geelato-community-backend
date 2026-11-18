---
title: API Reference
sidebar_label: API Reference
---
# API Reference

平台 `cn.geelato.web.platform.srv` 全部 REST 接口的静态参考清单，覆盖 90 个 Controller、500+ 端点。

本章按业务域组织：每个业务域（srv 子包）一页，页内按 Controller 分节，每个端点一张表，涵盖 URL、参数与返回结果。接口信息直接从源码签名梳理，与代码同步维护。

## 公共约定

### URL 前缀

- Controller 标注自研注解 `@ApiRestController`（含 `@DesignTimeApiRestController`、`@ApiRuntimeRestController`）时，运行期自动追加 `/api` 前缀：实际 URL = `/api` + 类级路径 + 方法级路径。
- 少数使用字面 `@Controller` / `@RestController` 的接口不追加 `/api`（如应用打包的 `/package` 系列），以各端点标注的 URL 为准。
- 前缀由 `ApiPrefixAutoConfiguration` 注册，仅 quickstart 宿主生效。

### 公共请求头

- `appId`：应用标识，兼容 `appId` / `AppId` / `App-Id` 三种写法。
- `tenantCode`：租户编码，兼容 `tenantCode` / `TenantCode` / `Tenant-Code`，缺省回落当前会话。

所有接口均可携带，不在各端点参数表中重复列出。

### 分页参数

列表/分页类端点通用：

- `pageNum`：页码，别名 `current`、`page`。GET 请求未传时为 `-1`，即不分页全量查询；POST body 未传时默认 `1`；传入 `<1` 直接报错。
- `pageSize`：页容量，别名 `limit`。GET 未传时为 `-1`（全量）；POST body 未传时默认 `10`；越界钳制到 `[1, 1000]`。
- `orderBy`：排序，别名 `order`；缺省 `seq_no ASC,update_at DESC`。

### 过滤条件

query 或 body 键支持 `字段名|操作符` 语法（如 `name|like`），只保留实体已有字段的键。操作符缺省 `eq`，支持 `bt`（between，两值）、`nil`（is null）等。

### 设计态接口

标注 ⚙ 的 Controller 为设计态接口（`@DesignTimeApiRestController`），主要供设计器 / IDE 使用；其余为运行态接口（`@ApiRestController`）。`@ApiRuntimeRestController` 且 category 为 `platform-design` 的 Controller 同样按设计态标注。

## 统一返回结构

| 字段 | ApiResult | ApiPagedResult | ApiMetaResult | 说明 |
| --- | --- | --- | --- | --- |
| `code` | ✓ | ✓ | ✓ | 结果码，`200` 成功 |
| `msg` | ✓ | ✓ | ✓ | 消息 |
| `status` | ✓ | ✓ | ✓ | 状态 |
| `data` | ✓ | ✓ | ✓ | 数据 |
| `cache` | ✓ | ✓ | ✓ | 缓存标记 |
| `total` / `page` / `size` / `dataSize` | — | ✓ | — | 总数 / 页码 / 页容量 / 本页数据条数 |
| `meta` | — | ✓ | ✓ | 元数据 |

`ApiMultiPagedResult` 的 `data` 为 `Map<String, PageData>`，`PageData` 含 `total` / `page` / `size` / `dataSize` / `data` / `meta`，用于一次请求返回多实体分页。

## 业务域索引

| 业务域 | Controller | 端点 |
| --- | --- | --- |
| [AI 助手](./srv/ai.md) | 1 | 1 |
| [公告](./srv/announcement.md) | 1 | 2 |
| [Arco 组件](./srv/arco.md) ⚙ | 1 | 1 |
| [认证登录](./srv/auth.md) | 2 | 18 |
| [平台基础](./srv/base.md) | 15 | 46 |
| [Excel 导入导出](./srv/excel.md) | 3 | 17 |
| [文件与附件](./srv/file.md) | 3 | 21 |
| [灰度规则](./srv/gray.md) | 1 | 2 |
| [元数据](./srv/meta.md) | 2 | 26 |
| [数据建模](./srv/model.md) ⚙ | 7 | 48 |
| [物化视图](./srv/mv.md) ⚙ | 1 | 13 |
| [站内通知](./srv/notification.md) | 1 | 9 |
| [OCR 识别](./srv/ocr.md) | 6 | 24 |
| [ORM 钩子](./srv/ormhook.md) | 1 | 7 |
| [应用打包](./srv/pack.md) | 2 | 12 |
| [应用与平台配置](./srv/platform.md) | 13 | 80 |
| [周期报表](./srv/report.md) | 1 | 5 |
| [脚本与外部服务](./srv/script.md) | 5 | 16 |
| [安全与权限](./srv/security.md) | 19 | 125 |
| [应用设置](./srv/settings.md) | 1 | 3 |
| [静态站点](./srv/site.md) ⚙ | 1 | 13 |
| [租户](./srv/tenant.md) ⚙ | 1 | 8 |
| [企业微信](./srv/weixin.md) | 1 | 5 |
| [条码](./srv/zxing.md) | 1 | 9 |

## 标准契约与补充入口

- **OpenAPI** 是对外官方标准契约，主要服务于外部集成、运行时 API 消费、接口评审与网关 / 客户端生成；能力存在于 quickstart 侧的 `springdoc`。
- 本章静态清单直接从源码梳理，作为按 Controller 浏览的补充入口。
- `MQL` 不是对外标准契约，而是平台内部通用元数据访问协议，见 [MQL](../mql/overview.md)。
