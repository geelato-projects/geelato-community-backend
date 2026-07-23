---
title: 插件仓库配置
sidebar_label: 插件仓库配置
---

# 插件仓库配置

本页说明插件仓库配置文件的结构、默认路径，以及本地文件夹仓库的组织方式。

## 两个路径配置

插件相关配置有两个路径：

- `geelato.plugin.pluginDirectory`：Runtime 实际加载插件的目录，是启停、列表、调用面对的插件根目录。
- `geelato.plugin.pluginRepository`：插件仓库描述目录，存放仓库索引与插件发布清单。

默认值分别为 `plugins` 与 `plugins/repository`。

## 仓库描述文件

仓库根目录下存在两个描述文件：

- `plugins/repositories.json`：仓库列表。
- `plugins/plugins.json`：某仓库下可发布的插件版本列表。

## `repositories.json` 结构

示例：

```json
[
  {
    "id": "folder",
    "url": "file:/path/to/plugins",
    "pluginsJsonFileName": "plugins.json"
  }
]
```

字段含义：

- `id`：仓库标识。
- `url`：仓库根地址（本地文件夹仓库使用 `file:` 协议）。
- `pluginsJsonFileName`：该仓库下插件清单文件名。

## `plugins.json` 结构

示例：

```json
[
  {
    "id": "hello-plugin",
    "description": "Hello plugin",
    "releases": [
      {
        "version": "0.0.1",
        "date": "Jun 5, 2014 9:12:35 PM",
        "url": "pf4j-demo-plugin2/0.0.1/pf4j-demo-plugin2-0.8.0.zip"
      }
    ]
  }
]
```

插件条目包含 `id`、`description`、`releases`，每个发布版本包含 `version`、`date`、`url`。该文件本质上是插件发行清单。

## 仓库接入

Runtime 在 `PluginConfiguration` 中创建 `UpdateManager`：

```java
Path pluginRepository = normalizeDirectory(pluginConfigurationProperties.getPluginRepository(), "plugins/repository");
return new UpdateManager(springPluginManager, pluginRepository);
```

仓库能力的底层基础（仓库目录、更新管理器）已具备，但尚未暴露完整的仓库浏览、在线安装、在线升级 API。

## 如何组织本地文件夹仓库

沿用文件夹仓库模型时，按以下步骤组织。

### 准备仓库根目录

配置为独立目录（如 `<应用运行目录>/plugins` 或其他自定义路径），避免与运行目录混用。

### 准备 `repositories.json`

如需 Runtime 识别该仓库，需在仓库描述中写入仓库 ID、仓库 URL、插件清单文件名。

### 准备 `plugins.json`

在仓库根目录放置 `plugins.json`，声明可发布的插件及其版本。

### 准备真实插件包

`plugins.json` 中每个 release 的 `url` 应指向真实可访问的插件包位置（zip、jar 或相对目录下的发布包）。

## 配置建议

- `pluginDirectory` 与 `pluginRepository` 建议逻辑分离。
- 开发环境可使用本地文件夹仓库；生产环境建议将仓库放到独立发布目录，而非与运行目录混用。
- `plugins.json` 中的版本号应与插件 Manifest、`plugin.properties` 保持一致。
- 仓库文件建议纳入统一发布流程维护。

## 能力现状

- 插件目录加载与启停管理已可用。
- 插件仓库元数据与更新管理器已预留。
- 完整的仓库安装/更新工作流尚未形成公开管理接口。

当前阶段，仓库配置定位为发布索引底座，实际部署仍以"打包后拷贝到插件目录"为主。

## 继续阅读

- [概览](overview.md)
- [定义与开发](development.md)
- [加载、启停与卸载](lifecycle.md)
