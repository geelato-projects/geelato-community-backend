---
title: 插件加载、启停与卸载
sidebar_label: 加载、启停与卸载
---

# 插件加载、启停与卸载

本页说明 Geelato Runtime 如何识别插件目录、管理插件状态，以及"禁用"与"卸载"的区别。

## 加载入口

插件运行时管理器由 `PluginConfiguration` 创建 `SpringPluginManager`，其初始化目录来自 `geelato.plugin.pluginDirectory`，默认值为 `plugins`。

最基本的加载方式：

1. 将插件 jar 或目录型插件放入 `plugins` 目录。
2. Runtime 以该目录作为插件根目录进行管理。

## 运行目录结构

运行目录（`plugins`）示例内容：

- `example-plugin-0.0.1-SNAPSHOT.jar`
- `ocr-plugin-0.0.1-SNAPSHOT.jar`
- `hello-plugin/`
- `plugins.json`
- `repositories.json`
- `logs/`

说明当前运行时兼容两类形态：

- 单 jar 插件
- 目录型插件

## 当前管理端接口

插件管理控制器是：

- `PluginManagerController`

对外路径前缀：

- `/api/pm`

当前已实现的接口包括：

- `GET /api/pm/list`
- `GET /api/pm/switchStatus`
- `GET /api/pm/log`
- `GET /api/pm/clearLog`

## 查看插件列表

接口：

- `GET /api/pm/list`

返回信息来自：

- `pluginManager.getPlugins()`

当前返回字段包括：

- `id`
- `version`
- `description`
- `provider`
- `dependencies`
- `state`
- `enabled`

所以它既能看到插件描述信息，也能看到当前运行态。

## 启用和禁用

接口：

- `GET /api/pm/switchStatus?pluginId=...&status=enable`
- `GET /api/pm/switchStatus?pluginId=...&status=disable`

### 启用

启用时，当前代码调用：

- `pluginManager.startPlugin(pluginId)`

并检查返回状态是否为：

- `PluginState.STARTED`

### 禁用

禁用时，当前代码调用：

- `pluginManager.stopPlugin(pluginId)`

并检查返回状态是否为：

- `PluginState.STOPPED`

### 重要边界

这里的：

- 启用 / 禁用

只是运行态状态切换，不等于：

- 安装
- 卸载
- 删除插件文件

## 当前实现下如何理解“卸载插件”

从当前代码来看，Runtime 只明确暴露了：

- `startPlugin`
- `stopPlugin`

并没有对外暴露：

- `unloadPlugin`
- `deletePlugin`
- 上传安装接口

因此当前“卸载插件”要分两层理解。

### 运行态卸载

目前公开接口只支持：

- 停止插件

这相当于：

- 禁用
- 释放运行态功能入口

但插件文件本身仍然保留在插件目录中。

### 物理移除

如果要彻底从当前部署中移除插件，当前更稳妥的做法是：

1. 先通过 `/api/pm/switchStatus` 停止插件
2. 删除 `plugins` 目录下对应 jar 或插件目录
3. 重启应用，让插件列表重新从目录收敛

也就是说，当前代码层面更成熟的是：

- 启停

而不是：

- 在线卸载并删除物理文件

## 插件日志

当前插件日志辅助能力来自：

- `PluginLogUtil`

日志目录固定写入：

- `plugins/logs`

日志文件命名规则是：

- `{pluginId}.log`

### 查看日志

接口：

- `GET /api/pm/log?pluginId=...`

当前会读取：

- `plugins/logs/{pluginId}.log`

### 清空日志

接口：

- `GET /api/pm/clearLog?pluginId=...`

当前逻辑是：

- 直接删除日志文件

## 业务侧如何拿到插件实例

当前业务代码并不是直接从 `PluginManager` 拿实现，而是通过：

- `PluginBeanProvider`

调用：

- `getExtensions(type)`
- `getExtensions(type, pluginId)`

然后返回第一个匹配扩展实例。

所以如果某个插件已经：

- 被禁用
- 未正确加载
- 没有对应扩展实现

业务侧调用时就会抛出：

- `UnFoundPluginException`

## 当前能力边界

目前可以明确认为已经具备的能力有：

- 插件目录配置
- 插件管理器装配
- 插件扩展点调用
- 插件列表查看
- 插件启用 / 禁用
- 插件日志查看与清理
- 插件仓库管理器对象初始化

而当前尚未明确以管理 API 暴露的能力包括：

- 插件上传安装
- 在线删除插件文件
- 在线 `unloadPlugin`
- 插件版本升级工作流

这些能力并不是完全不可能，而是：

- 当前代码里还没有形成稳定对外接口

## 推荐运维流程

当前更推荐的运维动作是：

1. 在构建工程里打包插件
2. 将插件包复制到 `plugins` 目录
3. 启动或重启 Runtime
4. 通过 `/api/pm/list` 查看是否识别成功
5. 通过 `/api/pm/switchStatus` 控制启停
6. 如需彻底移除，先停用再删文件并重启

## 推荐继续阅读

- [概览](overview.md)
- [定义与开发](development.md)
- [插件仓库配置](repository.md)
