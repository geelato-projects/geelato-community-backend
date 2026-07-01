# 插件机制概览

这篇文档说明 Geelato 当前插件机制的整体结构、目录约定和运行时角色划分。

本文主要基于：

- `geelato-plugins`
- `plugins`
- `服务端插件开发指引.md`
- Runtime 中的插件装配与管理类

## 整体结构

当前插件机制可以分成三层：

1. 插件运行时
2. 插件接口契约
3. 插件实现工程

对应到当前仓库，分别是：

- 运行时装配与管理：
  - `geelato-community/geelato-web-runtime/.../plugin`
- 插件接口与共享契约：
  - `geelato-plugins/geelato-plugin-all`
- 插件实现：
  - `geelato-plugins/geelato-example-plugin`
  - `geelato-plugins/geelato-logging-plugin`
  - `geelato-plugins/geelato-ocr-plugin`

## 当前运行时核心类

### `PluginConfiguration`

运行时通过：

- `PluginConfiguration`

创建两个关键 Bean：

- `SpringPluginManager`
- `UpdateManager`

其中：

- `SpringPluginManager`
  - 负责插件目录扫描、插件类加载、扩展点管理
- `UpdateManager`
  - 负责插件仓库更新能力

### `PluginConfigurationProperties`

插件路径相关配置统一来自：

- `geelato.plugin.pluginDirectory`
- `geelato.plugin.pluginRepository`

当前默认值是：

- `pluginDirectory = plugins`
- `pluginRepository = plugins/repository`

这两个目录在启动时会自动创建。

### `PluginBeanProvider`

业务侧调用插件扩展时，统一通过：

- `PluginBeanProvider`

按：

- 扩展接口类型
- `pluginId`

获取插件实例。

### `PluginManagerController`

当前运行时还提供了一个插件管理接口：

- `/api/pm/*`

目前已暴露：

- `GET /api/pm/list`
- `GET /api/pm/switchStatus`
- `GET /api/pm/log`
- `GET /api/pm/clearLog`

也就是说，当前管理端已支持：

- 查看插件列表
- 启用插件
- 禁用插件
- 查看插件日志
- 清空插件日志

## 当前目录约定

### 插件源码工程

插件源码集中在：

- `d:\geelato\geelato-enterprise\geelato-plugins`

这里是插件开发与打包工程，不是 Runtime 直接扫描的插件目录。

它当前是一个 Maven 聚合工程，子模块包括：

- `geelato-plugin-all`
- `geelato-logging-plugin`
- `geelato-example-plugin`
- `geelato-ocr-plugin`

### 运行时插件目录

实际运行时插件目录位于：

- `d:\geelato\geelato-enterprise\plugins`

这里当前可以看到：

- 已构建的插件包，例如 `example-plugin-0.0.1-SNAPSHOT.jar`
- 目录型插件，例如 `hello-plugin`
- 仓库描述文件：
  - `plugins.json`
  - `repositories.json`
- 插件日志目录：
  - `plugins/logs`

所以开发态和运行态是两套目录：

- `geelato-plugins`
  - 负责开发、编译、打包
- `plugins`
  - 负责部署、加载、日志、仓库描述

## 插件接口层如何设计

当前插件机制不是让主工程直接依赖某个插件实现类，而是先抽象扩展点接口。

基础扩展点接口位于：

- `PluginExtensionPoint`

它继承：

- `org.pf4j.ExtensionPoint`

业务插件接口再继承 `PluginExtensionPoint`，例如：

- `cn.geelato.plugin.example.Greeting`

这种设计的意义是：

- 宿主工程只依赖接口
- 插件实现可独立替换
- 多个插件可以围绕同一扩展点演进

## 当前插件实现的典型结构

以 `geelato-example-plugin` 为例，当前结构包含：

- 插件主类 `HelloPlugin`
- Spring 配置类 `ExamplePluginConfiguration`
- 扩展实现类 `HelloGreeting`
- `plugin.properties`

其中：

- `HelloPlugin`
  - 继承 `SpringPlugin`
  - 创建插件自己的 Spring `ApplicationContext`
- `HelloGreeting`
  - 使用 `@Extension` 标记为 PF4J 扩展实现
- `ExamplePluginConfiguration`
  - 提供插件内部自己的 Bean

所以当前插件并不是一个简单 jar，而是：

- 有独立扩展点实现
- 有独立插件上下文
- 可借助 Spring 管理内部依赖

## 调用模式

当前宿主工程调用插件的标准方式是：

1. 注入 `PluginBeanProvider`
2. 按扩展接口和 `pluginId` 获取插件实例
3. 调用扩展方法

因此主工程与插件之间的边界是：

- 接口 + 插件 ID

而不是：

- 直接 new 某个插件类

## 当前实现下“加载插件”怎么理解

当前实现层面，插件加载主要依赖：

- `SpringPluginManager(pluginDirectory)`

这表示 Runtime 会以：

- `geelato.plugin.pluginDirectory`

指定的目录作为插件根目录。

插件需要先被打包并放入这个目录，运行时才能识别和管理。

从当前代码来看，管理端明确暴露的是：

- 启用
- 禁用

而不是完整的上传、安装、删除控制器。

因此当前“插件机制”更准确地说是：

- 具备目录扫描与运行期启停基础
- 具备仓库管理器对象
- 但安装 / 卸载 UI 与 API 仍偏基础

## 当前已存在的插件仓库描述

运行目录下已经存在：

- `plugins/repositories.json`
- `plugins/plugins.json`

说明当前实现已经预留了：

- 文件夹仓库
- 插件发布清单

这部分会在“插件仓库配置”页展开说明。

## 推荐继续阅读

- [定义与开发](development.md)
- [加载、启停与卸载](lifecycle.md)
- [插件仓库配置](repository.md)
