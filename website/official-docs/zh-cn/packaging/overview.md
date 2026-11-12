---
title: 应用打包
sidebar_label: 应用打包
---

# 应用打包与部署机制

低代码应用在平台上的交付有三条并行路径，覆盖不同粒度与场景：

| 机制 | 产物 | 粒度 | 说明 |
| --- | --- | --- | --- |
| **应用包（本页主角）** | `.gdp` / `.zgdp` | 整个应用（元数据 + 资源） | 设计器产出的应用全量数据包，打包→传输→部署→回滚；对应模块 `geelato-package` + web-platform 的 `/package` 服务 |
| 插件包（pf4j） | 插件 jar | 代码级扩展能力 | 运行期启停的 Java 插件；见[插件机制](../plugin-mechanism/overview.md) |
| 脚手架 / 启动壳制品 | Spring Boot fat jar | 宿主工程 | `geelato-app-scaffold` 生成的工程或 runtime/designer 启动壳的常规构建部署；见[平台部署指南](../operations/runtime-designer-deployment.md) |

三者常组合使用：宿主用脚手架/启动壳交付运行时，低代码配置用应用包在环境间迁移，二方代码能力用插件包热插拔。

## 应用包结构

- 包数据 `AppPackData`：`sourceAppId / targetAppId / version / appCode / appName` + `appMetaList`（应用元数据行：页面、模型、流程等）+ `appResourceList`（静态资源）+ `basePlatformVersion`（打包时平台基准版本）。
- 产物：`.gdp`（JSON 全量包，默认名 `geelatoApp`）或压缩 `.zgdp`。
- 模块坐标 `cn.geelato:geelato-package`；配置前缀 `geelato.package`。

## 配置

| 键（`geelato.package.*`） | 默认 | 说明 |
| --- | --- | --- |
| `env` | — | 环境标识 |
| `path` | — | 包文件存储根目录 |
| `uploadFolder` | — | 上传包子目录 |
| `engine` | `v1` | 打包/部署引擎版本：`v1`（原始实现）或 `v2`（演进版，见下）；仅 v2 支持回滚 |

:::tip
三个基础键的含义另见[系统模块配置：Package](../system-config/package.md)；本页补充 `engine` 与操作流程。
:::

## 操作流程

`PackageController` 是普通 `@Controller`（**端点不带 `/api` 前缀**），`AppVersionController` 走 `/api/app/version`：

| 步骤 | 端点 | 说明 |
| --- | --- | --- |
| 1. 打包 | `GET/POST /package/packet/{appId}` | 生成应用包并登记版本；`engine=v2` 走 `packetV2` |
| 2. 合并打包 | `GET/POST /package/packet/merge` | 多应用合并为包 |
| 3. 下载 | `GET /package/downloadPackage/{versionId}` | 下载 `.zgdp` |
| 4. 上传 | `POST /package/uploadPackage/{appId}` | 上传到 `geelato.package.path/uploadFolder`，登记 `AppVersion` |
| 5. 部署 | `GET /package/deploy/{versionId}` | 校验→备份→落库→刷新缓存 |
| 6. 回滚 | `GET /package/rollback/{appId}` | 回滚到最近一次备份（**仅 v2**） |

版本管理：`POST /api/app/version/pageQuery`、`GET /api/app/version/get/{id}`、`POST /createOrUpdate`、`POST /validate`、`DELETE /isDelete/{id}`（实体 `AppVersion`，含 `packagePath`、`packetTime`）。

## 部署校验与安全边界

1. **平台版本匹配**：包的 `basePlatformVersion` 须与目标平台应用 `geelato_admin` 的 `platform_app.version_info` 匹配，不匹配报"请先更新平台应用 geelato_admin 至版本 X"；
2. **部署前备份**：v2 部署自动备份当前版本，供回滚；
3. **平台表白名单**：`PlatformTableConstant.isPlatformTable` 清洗，应用包不能改写平台表；
4. **表名防注入**：落库表名须匹配 `^[a-zA-Z_][a-zA-Z0-9_]*$`；
5. **批式落库**：元数据行分批 500 行写入（MQL SaveCommand / `JsonTextSaveParser`）；
6. **环境保护**：`init_source` 环境禁止部署；
7. **缓存刷新**：部署完成后刷新应用元数据缓存。

## v1 与 v2 引擎

`geelato.package.engine` 选择打包/部署引擎（绞杀者模式，v1 原地冻结、新能力只在 v2）：

| 维度 | v1 | v2 |
| --- | --- | --- |
| 打包/部署入口 | `/package/packet`、`/deploy`（同一端点按 engine 分流） | 同左 |
| 状态管理 | 单例可变状态 | 无单例可变状态 |
| 部署事务 | 编程式 | 声明式 `@Transactional` |
| 备份/回滚 | 无备份 | 部署前自动备份 + `GET /package/rollback/{appId}` |
| 数据清洗 | — | 平台表白名单 + 表名防注入 + 分批 500 |

## 与插件包的边界

应用包搬移的是**数据形态**的低代码配置（页面/模型/流程元数据与资源）；插件包（pf4j）部署的是**代码形态**的扩展（Java jar，实现 `PluginExtensionPoint`）。需要"环境间同步代码级能力"时打插件包并拷入插件目录（HTTP 上传/安装端点暂未提供，见插件机制章节），低代码配置变更则走应用包。
