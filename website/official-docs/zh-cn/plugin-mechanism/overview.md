---
title: 插件机制概览
sidebar_label: 插件机制概览
---

# 插件机制概览

本页说明 Geelato 插件机制的整体结构、目录约定与运行时角色划分。插件机制基于 PF4J 实现，宿主工程仅依赖扩展点接口，插件实现可独立打包、按需启停。

## 能力范围

当前插件机制提供的能力如下：

| 能力 | 支持情况 | 说明 |
| --- | --- | --- |
| 插件目录扫描与类加载 | 支持 | 启动时扫描 `pluginDirectory` 下的插件。 |
| 运行期启用 / 禁用 | 支持 | 通过 `SpringPluginManager` 启停已加载的插件。 |
| 插件列表 / 日志查看 | 支持 | 通过 `/api/pm/*` 管理接口。 |
| 插件仓库管理器 | 支持 | `UpdateManager` Bean 已创建，预留仓库与发布清单。 |
| 通过 HTTP 上传 / 安装插件 | 未提供 | 无对应 REST 端点；安装需手动将插件包放入插件目录。 |
| 通过 HTTP 卸载 / 删除插件 | 未提供 | 无对应 REST 端点。 |

## 整体结构

插件机制分为三层：

1. **插件运行时**：位于 `geelato-web-runtime` 的 `plugin` 包，负责插件装配与管理。
2. **插件接口契约**：`geelato-plugins/geelato-plugin-all`，提供基础扩展点接口与共享契约。
3. **插件实现工程**：`geelato-plugins` 下的各插件模块，例如 `geelato-example-plugin`、`geelato-logging-plugin`、`geelato-ocr-plugin`。

## 运行时核心组件

### 插件装配 (`PluginConfiguration`)

`PluginConfiguration` 创建两个关键 Bean：

- **`SpringPluginManager`**：负责插件目录扫描、插件类加载、扩展点管理。
- **`UpdateManager`**：负责插件仓库更新能力（PF4J 的 `UpdateManager`，基于 `pluginRepository` 配置）。

两个目录在启动时会自动创建。

### 配置项 (`PluginConfigurationProperties`)

插件路径配置统一来自 `geelato.plugin.*`：

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `geelato.plugin.pluginDirectory` | `plugins` | 运行时插件根目录，应用启动时扫描此目录。 |
| `geelato.plugin.pluginRepository` | `plugins/repository` | 插件仓库目录，供 `UpdateManager` 使用。 |

### 插件调用 (`PluginBeanProvider`)

业务侧调用插件扩展时统一通过 `PluginBeanProvider`，按扩展接口类型与 `pluginId` 获取插件实例：

```java
public <T> T getBean(Class<T> type, String pluginId)
```

`pluginId` 为空时返回该扩展点的第一个实现；未找到实现时抛出 `UnFoundPluginException`。

### 管理接口 (`PluginManagerController`)

运行时提供插件管理接口，挂在 `/api/pm` 下，均为 GET 方法：

| 端点 | 作用 |
| --- | --- |
| `GET /api/pm/list` | 查看插件列表及状态。 |
| `GET /api/pm/switchStatus?pluginId=&status=` | 启用或禁用插件。`status` 取值 `enable` / `disable`。 |
| `GET /api/pm/log?pluginId=` | 查看插件日志。 |
| `GET /api/pm/clearLog?pluginId=` | 清空插件日志。 |

`switchStatus` 针对已加载插件调用 `startPlugin` / `stopPlugin`，状态已是目标值时直接返回成功，操作结果写入插件日志。

## 目录约定

开发态与运行态是两套目录：

| 目录 | 角色 | 内容 |
| --- | --- | --- |
| `geelato-plugins` | 开发、编译、打包 | Maven 聚合工程，包含 `geelato-plugin-all` 及各插件实现模块。 |
| `plugins` | 部署、加载、日志、仓库描述 | 已构建的插件包（如 `example-plugin-0.0.1-SNAPSHOT.jar`）、目录型插件（如 `hello-plugin`）、仓库描述文件（`plugins.json`、`repositories.json`）、插件日志目录（`plugins/logs`）。 |

运行时插件目录下已存在仓库描述文件：

- `plugins/repositories.json`：文件夹仓库描述。
- `plugins/plugins.json`：插件发布清单。

详见 [插件仓库配置](repository.md)。

## 插件接口设计

插件机制不要求主工程直接依赖某个插件实现类，而是先抽象扩展点接口。基础扩展点接口 `PluginExtensionPoint` 继承自 `org.pf4j.ExtensionPoint`，业务插件接口再继承 `PluginExtensionPoint`（例如 `cn.geelato.plugin.example.Greeting`）。

该设计的意义：

- 宿主工程只依赖接口。
- 插件实现可独立替换。
- 多个插件可围绕同一扩展点演进。

## 插件实现典型结构

以 `geelato-example-plugin` 为例，结构包含：

- **插件主类** `HelloPlugin`：继承 `SpringPlugin`，创建插件自己的 Spring `ApplicationContext`。
- **Spring 配置类** `ExamplePluginConfiguration`：提供插件内部 Bean。
- **扩展实现类** `HelloGreeting`：使用 `@Extension` 标记为 PF4J 扩展实现。
- **`plugin.properties`**：插件元信息。

插件并非简单 jar，而是具备独立扩展点实现、独立插件上下文，并可借助 Spring 管理内部依赖。

## 加载流程

插件加载依赖 `SpringPluginManager(pluginDirectory)`，运行时以 `geelato.plugin.pluginDirectory` 指定的目录作为插件根目录。插件需先打包并放入该目录，运行时才能识别与管理。

启用/禁用通过管理接口 `GET /api/pm/switchStatus` 触发（或直接调用 `SpringPluginManager`），针对已扫描到的插件执行启动或停止。

## 调用模式

宿主工程调用插件的标准方式：

1. 注入 `PluginBeanProvider`。
2. 按扩展接口类型与 `pluginId` 获取插件实例。
3. 调用扩展方法。

主工程与插件之间的边界是"接口 + 插件 ID"，而非直接实例化某个插件类。

## 继续阅读

- [定义与开发](development.md)
- [加载、启停与卸载](lifecycle.md)
- [插件仓库配置](repository.md)
