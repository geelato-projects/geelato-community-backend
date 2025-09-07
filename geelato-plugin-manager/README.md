# Geelato 插件管理器

基于PF4J的插件管理系统，用于管理Geelato平台的插件。

## 功能特性

- 获取已安装的插件列表
- 启用/禁用插件
- 查看插件日志
- 清除插件日志

## API接口

### 获取插件列表

```
GET /pm/list
```

返回所有已安装的插件信息，包括插件ID、版本、描述、提供者、依赖关系和状态。

### 切换插件状态

```
GET /pm/switchStatus?pluginId={pluginId}&status={status}
```

参数：
- `pluginId`: 插件ID
- `status`: 目标状态，可选值为 `enable` 或 `disable`

启用或禁用指定的插件。

### 获取插件日志

```
GET /pm/log?pluginId={pluginId}
```

参数：
- `pluginId`: 插件ID

获取指定插件的日志内容。

### 清除插件日志

```
GET /pm/clearLog?pluginId={pluginId}
```

参数：
- `pluginId`: 插件ID

清除指定插件的日志内容。

## 插件开发

### 插件结构

一个基本的插件目录结构如下：

```
plugins/
  ├── my-plugin/
  │   ├── plugin.properties  # 插件属性文件
  │   └── classes/           # 编译后的类文件
  │       └── com/
  │           └── example/
  │               └── MyPlugin.class
```

### plugin.properties

插件属性文件包含插件的基本信息：

```properties
plugin.id=my-plugin
plugin.version=1.0.0
plugin.description=My plugin description
plugin.provider=My Company
plugin.class=com.example.MyPlugin
plugin.dependencies=
plugin.requires=
plugin.license=Apache License 2.0
```

### 插件类

插件主类需要继承`org.pf4j.Plugin`：

```java
package com.example;

import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

public class MyPlugin extends Plugin {
    
    public MyPlugin(PluginWrapper wrapper) {
        super(wrapper);
    }
    
    @Override
    public void start() {
        // 插件启动时的逻辑
    }
    
    @Override
    public void stop() {
        // 插件停止时的逻辑
    }
}
```

### 扩展点

定义扩展点接口：

```java
package com.example;

import org.pf4j.ExtensionPoint;

public interface MyExtensionPoint extends ExtensionPoint {
    void doSomething();
}
```

实现扩展点：

```java
package com.example;

import org.pf4j.Extension;

@Extension
public class MyExtension implements MyExtensionPoint {
    
    @Override
    public void doSomething() {
        // 实现逻辑
    }
}
```

## 配置

插件目录默认为应用根目录下的`plugins`文件夹。插件日志存储在`plugins/logs`目录中。