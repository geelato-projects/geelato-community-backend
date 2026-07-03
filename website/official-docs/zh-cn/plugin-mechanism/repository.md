# 插件仓库配置

这篇文档说明当前插件仓库配置文件的结构、默认路径，以及本地文件夹仓库如何组织。

本文主要基于：

- `PluginConfigurationProperties`
- `PluginConfiguration`
- `plugins/repositories.json`
- `plugins/plugins.json`

## 两个容易混淆的路径

当前插件相关配置有两个路径：

- `geelato.plugin.pluginDirectory`
- `geelato.plugin.pluginRepository`

默认值分别是：

- `plugins`
- `plugins/repository`

它们的职责不同。

### `pluginDirectory`

表示：

- Runtime 实际加载插件的目录

也就是当前启停、列表、调用真正面对的插件根目录。

### `pluginRepository`

表示：

- 插件仓库描述目录

也就是：

- 仓库索引
- 插件发布清单

所在的位置。

## 当前仓库描述文件

在当前仓库根目录下，已经存在：

- `plugins/repositories.json`
- `plugins/plugins.json`

这两个文件描述的是：

- 仓库列表
- 某个仓库下可发布的插件版本列表

## `repositories.json` 结构

当前示例内容是：

```json
[
  {
    "id": "folder",
    "url": "file:D:\\geelato\\geelato-enterprise\\plugins",
    "pluginsJsonFileName": "plugins.json"
  }
]
```

这表示当前定义了一个：

- 本地文件夹仓库

关键字段含义如下：

- `id`
  - 仓库标识
- `url`
  - 仓库根地址
- `pluginsJsonFileName`
  - 该仓库下插件清单文件名

当前这个配置说明：

- 插件仓库是一个本地文件夹
- 根地址就是 `file:D:\geelato\geelato-enterprise\plugins`
- 该目录下的插件清单文件叫 `plugins.json`

## `plugins.json` 结构

当前示例内容类似：

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

一个插件条目通常包含：

- `id`
- `description`
- `releases`

而每个发布版本又包含：

- `version`
- `date`
- `url`

因此这个文件本质上是：

- 插件发行清单

## 当前实现里仓库如何接入

Runtime 在：

- `PluginConfiguration`

中创建：

- `UpdateManager`

创建方式是：

```java
Path pluginRepository = normalizeDirectory(pluginConfigurationProperties.getPluginRepository(), "plugins/repository");
return new UpdateManager(springPluginManager, pluginRepository);
```

这说明当前仓库能力的底层基础已经具备：

- 仓库目录
- 更新管理器

但当前代码里还没有进一步暴露完整的：

- 仓库浏览 API
- 在线安装 API
- 在线升级 API

所以当前仓库配置更像是：

- 已预留底座
- 已有样例配置
- 后续可继续扩展管理端功能

## 如何组织本地文件夹仓库

如果你要继续沿用当前的文件夹仓库模型，建议按下面的思路组织。

### 第 1 步：准备仓库根目录

例如：

- `D:\geelato\geelato-enterprise\plugins`

或者配置成一个独立目录：

- `D:\geelato\plugin-repository`

### 第 2 步：准备 `repositories.json`

如果希望 Runtime 识别该仓库，需要在仓库描述里写入：

- 仓库 ID
- 仓库 URL
- 插件清单文件名

### 第 3 步：准备 `plugins.json`

在仓库根目录放置：

- `plugins.json`

用来声明有哪些插件、哪些版本可发布。

### 第 4 步：准备真实插件包

`plugins.json` 中每个 release 的：

- `url`

都应指向真实可访问的插件包位置，例如：

- zip
- jar
- 某个相对目录下的发布包

## 配置建议

- `pluginDirectory` 和 `pluginRepository` 建议逻辑分离
- 开发环境可以先用本地文件夹仓库
- 生产环境建议把仓库放到独立发布目录，而不是直接和运行目录混用
- `plugins.json` 中的版本号应与插件 Manifest、`plugin.properties` 保持一致
- 仓库文件建议纳入统一发布流程维护，不要手工长期漂移

## 当前实现下的现实结论

如果只看当前仓库代码，可以得出一个比较准确的结论：

- 插件目录加载已经可用
- 插件启停管理已经可用
- 插件仓库元数据和更新管理器已预留
- 但完整的仓库安装 / 更新工作流还未形成公开管理接口

因此在当前阶段，最稳妥的策略仍然是：

- 把仓库配置看成发布索引底座
- 把真正部署仍然看成“打包后拷贝到插件目录”

## 推荐继续阅读

- [概览](overview.md)
- [定义与开发](development.md)
- [加载、启停与卸载](lifecycle.md)
