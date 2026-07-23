---
title: 文件上传
sidebar_label: 文件上传
---

# 文件上传

本页说明文件上传的核心入口、存储方式、附件表模型及其与 OSS 配置的关系，涉及的核心组件包括 `UploadController`、`FileHandler`、`UploadService`、`Attachment`。

## 总体链路

当前最核心的上传入口是：

- `POST /api/upload/file`

整体链路可以概括为：

1. `UploadController` 接收上传请求和业务参数
2. 根据 `tableType / tenantCode / appId / root / isRename` 计算保存路径
3. 组装 `FileParam`
4. `FileHandler` 按 `serviceType` 决定走本地存储还是阿里云 OSS
5. 文件真正落盘或上传对象存储
6. 通过 `AccessoryHandler` 保存附件元数据
7. 返回 `Attachment`

也就是说，框架把：

- 文件内容存储
- 附件元数据落库

拆成了两个阶段。

## 上传入口

### 文件上传

接口：

- `POST /api/upload/file`

主要参数包括：

- `file`
- `serviceType`
- `tableType`
- `root`
- `isRename`
- `objectId`
- `formIds`
- `genre`
- `batchNo`
- `invalidTime`
- `validDuration`
- `isThumbnail`
- `onlyThumb`
- `dimension`
- `thumbScale`
- `appId`
- `tenantCode`

其中最关键的是：

- `serviceType`
  - 决定走本地还是 OSS
- `tableType`
  - 决定属于哪类附件来源
- `root`
  - 允许显式指定本地目录
- `isRename`
  - 控制是否把原文件名改成 UID

### 配置对象上传

除了标准文件上传，还提供了：

- `POST /api/upload/object`
- `POST /api/upload/json`
- `POST /api/upload/model/{entityName}/{id}`

这些接口不是普通附件上传，而是把对象或 JSON 写入：

- `geelato.upload.config-directory`

对应的配置文件目录。

## 文件存放位置

### 本地根目录

上传服务的本地目录由以下配置驱动：

- `geelato.upload.root-directory`
- `geelato.upload.convert-directory`
- `geelato.upload.config-directory`

它们在 `UploadService` 初始化后会变成静态根路径：

- `rootDirectory`
- `rootConvertDirectory`
- `rootConfigDirectory`

在默认 quickstart 配置里，常见值是：

- `geelato.upload.root-directory=/upload`
- `geelato.upload.convert-directory=/upload/convert`
- `geelato.upload.config-directory=/upload/config`

### 路径如何生成

对于标准附件上传，如果没有显式传 `root`，通常会走：

- `UploadService.getRootSavePath(...)`

生成规则大致是：

1. 以 `geelato.upload.root-directory` 为根
2. 拼接附件来源目录，例如 `attach`
3. 再拼接 `tenantCode/appId`
4. 再拼接日期目录
5. 最后落到具体文件名

也就是说，本地附件目录通常会体现：

- 附件来源
- 租户
- 应用
- 日期

这样便于按业务来源和租户隔离文件。

### 如果显式传了 `root`

当上传请求带了：

- `root`

就会改走：

- `UploadService.getSaveRootPath(root, fileName, isRename)`

这意味着文件会直接落到你指定的根目录下，而不是默认的上传根目录结构。

## 本地与 OSS 两种存储模式

`FileHandler` 是真正的存储分发入口。

它的核心判断非常直接：

- `serviceType = aliyun` 时走 OSS
- 否则走本地存储

### 本地存储

本地模式下：

- 文件内容写入本地磁盘
- `Attachment.path` 保存本地绝对路径
- `Attachment.objectId` 通常为空

### OSS 存储

OSS 模式下：

1. 先通过 `FileHelper.putFile(...)` 上传到阿里云 OSS
2. OSS 返回：
   - `objectId`
   - `objectName`
3. 再保存附件元数据
4. `Attachment.path` 保存 OSS 对象名
5. `Attachment.objectId` 保存 OSS 对象 ID

所以在当前模型里：

- `path` 不一定是本地磁盘路径
- 当 `objectId` 非空时，通常表示该附件已经位于 OSS

## 缩略图能力

上传图片时可以开启：

- `isThumbnail`

这会触发缩略图生成逻辑。

当前行为包括：

- 仅图片触发
- 支持多分辨率缩略图
- `onlyThumb=true` 时可以只保留缩略图
- 缩略图和原图都会保存为附件记录
- 通过 `pid` 建立父子关系

因此一张原图可能对应：

- 一条原图附件记录
- 多条缩略图附件记录

## 附件表与元数据

框架并不是把所有文件都简单塞进一个字段，而是维护一套附件元数据模型。

核心实体是：

- `Attachment`

其中关键字段包括：

- `id`
- `pid`
- `appId`
- `name`
- `type`
- `genre`
- `size`
- `path`
- `objectId`
- `formIds`
- `invalidTime`
- `batchNo`
- `resolution`

这里要特别注意：

- `path`
  - 本地模式下通常是磁盘路径
  - OSS 模式下通常是对象名
- `objectId`
  - OSS 对象标识
- `pid`
  - 用于缩略图、引用、副本等父子关系

### 实际查询聚合的表

附件查询 SQL 并不是只查一个表，而是把三类表聚合起来：

- `platform_attach`
- `platform_compress`
- `platform_resources`

对外统一映射成 `Attachment` 视图。

其中：

- `platform_attach`
  - 普通附件
- `platform_compress`
  - 压缩包附件
- `platform_resources`
  - 资源型附件

并且 SQL 会根据 `object_id` 自动推导：

- `storageType = aliyun`
- `storageType = local`

这就是为什么附件列表接口可以直接按存储方式筛选。

## OSS 配置

如果要启用阿里云 OSS，核心配置来自：

- `properties/oss.properties`

关键配置项是：

- `geelato.oss.accessKeyId`
- `geelato.oss.accessKeySecret`
- `geelato.oss.endPoint`
- `geelato.oss.bucketName`
- `geelato.oss.region`

当前 quickstart 通过环境变量注入：

- `GEELATO_OSS_ACCESSKEYID`
- `GEELATO_OSS_ACCESSKEYSECRET`
- `GEELATO_OSS_ENDPOINT`
- `GEELATO_OSS_BUCKETNAME`
- `GEELATO_OSS_REGION`

如果 OSS 未配置，而你又把 `serviceType` 指到 `aliyun`，`FileHandler` 会直接抛出明确错误，提示切回本地存储或补齐 OSS 配置。

## 上传后的常见附件管理接口

上传完成后，附件通常通过：

- `AttachController`

继续管理，常见接口包括：

- `POST /api/attach/copy/{id}`
- `POST /api/attach/quote/{id}`
- `GET /api/attach/get/{id}`
- `POST /api/attach/update/{id}`
- `POST /api/attach/list`
- `POST /api/attach/pageQuery`
- `DELETE /api/attach/remove/{id}`
- `POST /api/attach/storage/{type}`
- `GET /api/resources/file?id={id}&isPreview=true`

其中：

- `storage/{type}`
  - 支持本地和 OSS 之间切换存储
- `copy`
  - 复制一份新文件
- `quote`
  - 复用已有文件内容，仅新建引用记录
- `resources/file`
  - 用附件 `id` 获取文件内容
  - `isPreview=true` 时按预览方式输出（通常用于图片/PDF 等）

## 使用建议

- 普通业务文件优先统一走 `POST /api/upload/file`
- 本地开发环境可先使用本地存储，生产环境再切到 OSS
- 不要把 OSS 密钥写死在源码里，统一使用环境变量注入
- 如果需要缩略图，优先只对图片类型开启
- 业务上应把附件和自己的业务主数据通过 `objectId / formIds / batchNo / genre` 等字段建立关联

## 推荐继续阅读

- [文件下载](download.md)
- [OSS 模块](../system-config/oss.md)
