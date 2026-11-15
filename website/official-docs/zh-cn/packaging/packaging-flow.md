---
title: 打包流程
sidebar_label: 打包流程
---

# 打包流程

本页说明把一个应用打成 `.zgdp` 包的完整流程：前置门禁、三种打包方式、产物生成链路、下载与上传。操作入口在 IDE 的应用版本管理页，也可直接调用本文所列端点。

`PackageController` 是普通 `@Controller`（**端点不带 `/api` 前缀**），按 `geelato.package.engine` 在 v1/v2 间分流；应用版本管理接口 `AppVersionController` 走 `/api/app/version`。

## 前置条件与门禁

打包在查询数据后、写包前执行两道校验，任一不通过即硬失败，不产出包文件：

1. **应用存在**：`appId` 必须能查到 `platform_app` 行，否则报 60001。
2. **列一致性**：物理表的实际列（`select *` 结果列集合）必须与实体定义（`EntityMeta` 字段）一致。物理表存在实体定义中没有的列时报 60002"打包校验失败：物理表与实体定义不一致，禁止打包"。

:::note 为什么要先过列一致性
以 `select *` 全列打包的行，若携带目标平台实体定义中没有的列，部署校验必然失败。打包前拦截可把问题暴露在源环境——先在模型设计器中同步表结构（或清理冗余物理列）再打包。
:::

版本号可传入或省略；省略时自动生成，格式为 `<appCode>_versionyyyyMMddHHmmss`。同一应用+租户+版本号需唯一（`POST /api/app/version/validate` 校验，前端打包对话框自动调用）。

## 三种打包方式

### 整应用打包

`GET/POST /package/packet/{appId}`，可选参数 `version`、`description`：

- 携带 19 张平台白名单表本应用全部行 + `pack_bus_data` 大于 0 的业务表全部行。
- 不传额外参数即为整应用打包，是环境间整体迁移的常规方式。

### 指定范围打包

同一端点，请求体传入 `appointMetas`（`{表名: "行id1,行id2"}`）可按行挑选元数据，用于只交付部分内容：

:::warning v1 与 v2 语义不同
- **v1（默认引擎）**：`appointMetas` 是白名单——**列出的表按所选行打包，未列出的表整张排除**（仅剩包头）。
- **v2**：`appointMetas` 是选行修饰——列出的表按所选行打包，**未列出的表仍全量打包**。

按范围交付前先确认引擎取值，避免包内容与预期不符。
:::

`platform_app` 不受 `appointMetas` 影响，恒作为包头携带。

### 合并打包（多版本对比合并）

`GET/POST /package/packet/merge`，请求体 `{版本号: {表名: "行id1,行id2"}}`：

1. 按版本号逐个读取已登记的包文件并解析。
2. 只合并 `appointMetas` 中**列出**的实体；值为空字符串表示该实体选 0 行。
3. 同一实体在多版本间按行 `id` 去重合并。
4. 生成新包并登记（描述缺省为"对比合并产生的包"）。

典型场景：先做版本对比（前端版本管理页），把多次增量交付的内容合并为一个包。合并只按行 `id` 去重，不做跨实体引用一致性检查。

## 产物生成链路

打包请求的内部执行顺序：

1. 构建查询映射：19 张平台表 + 业务表（业务表名做 `^[a-zA-Z_][a-zA-Z0-9_]*$` 防注入校验）。
2. 逐表查询本应用数据；`platform_app` 行写入包头。
3. 列一致性校验（见上）。
4. fastjson2 序列化整包（启用 `LargeObject` 特性以支撑大包）。
5. 写出 `<geelato.package.path>/<appCode>/<appCode>.gdp`。
6. 压缩为 `<版本号>.zgdp`，按租户与应用目录归档，登记为附件。
7. 写入应用版本记录 `platform_app_version`：`packagePath`（附件标识）、来源 `packet`、状态 `draft`、打包时间、租户。

包内 JSON 的日期值统一为 `yyyy-MM-dd HH:mm:ss` 格式，避免源/目标环境序列化差异导致的日期解析问题。

## 下载与上传

| 操作 | 方式 | 说明 |
| --- | --- | --- |
| 下载 | 版本管理页下载按钮（按附件标识走通用文件下载）；或 `GET /package/downloadPackage/{versionId}` | 得到 `.zgdp` 文件 |
| 上传 | 版本管理页上传（accept `.zgdp`）后 `POST /api/app/version/createOrUpdate` 登记；或 `POST /package/uploadPackage/{appId}` | 文件落盘到 `geelato.package.path/uploadFolder`，登记应用版本记录 |

上传登记后即可在目标环境版本列表中对该版本执行部署。

## 版本管理接口

| 端点 | 说明 |
| --- | --- |
| `POST /api/app/version/pageQuery` | 版本分页查询 |
| `GET /api/app/version/get/{id}` | 版本详情 |
| `GET /api/app/version/package/{id}` | 读取版本包内容（`AppPackData`） |
| `POST /api/app/version/createOrUpdate` | 版本登记/更新（上传后调用） |
| `POST /api/app/version/validate` | 版本号唯一性校验 |
| `DELETE /api/app/version/isDelete/{id}` | 删除版本记录 |

## 打包注意点

- **敏感信息原样进包**：包按 `select *` 全列携带，`platform_app.app_key/token`、数据源连接串等不做脱敏，包文件交付与留存需按敏感数据处理。
- **依赖不自动带入**：`depend_app_code` 仅为声明，被依赖应用需在目标环境预先就绪（见[打包内容与运行作用](contents.md)）。
- **附件二进制不进包**：`platform_resources` 只携带元数据行。
- **版本状态初始为 `draft`**：部署成功后自动变更为 `deployed`；备份版本由 v2 部署自动生成（版本号追加 `_backup`）。
- **日志独立路由**：打包/合并/部署全过程日志写入 `${LOG_DIR}/pack/`，按包名 `cn.geelato.web.platform.srv.pack` 路由，含每表行数与耗时，便于排查。

## 推荐继续阅读

- [应用包部署](deployment-flow.md)：包到达目标环境后的校验、落库与生效
- [打包内容与运行作用](contents.md)：包内每类内容与运行期作用
- [系统模块配置：Package](../system-config/package.md)：`geelato.package.*` 配置键
