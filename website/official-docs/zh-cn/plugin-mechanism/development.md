# 插件定义与开发

这篇文档说明当前 Geelato 插件应该如何定义扩展点、实现插件、打包插件，以及与宿主工程之间的依赖边界。

本文结合：

- `服务端插件开发指引.md`
- `geelato-plugin-all`
- `geelato-example-plugin`

进行整理。

## 开发时的模块分层

当前推荐把插件开发拆成两部分：

1. 扩展点契约层
2. 插件实现层

### 扩展点契约层

放在：

- `geelato-plugin-all`

这里主要定义：

- `PluginExtensionPoint`
- 具体业务扩展接口
- 共享的 `PluginInfo`
- 共享 DTO / VO / 元数据对象

例如：

- `cn.geelato.plugin.example.Greeting`

### 插件实现层

放在具体插件模块里，例如：

- `geelato-example-plugin`
- `geelato-ocr-plugin`

这里主要包含：

- 插件主类
- 插件 Spring 配置
- 扩展实现类
- `plugin.properties`

## 第 1 步：定义扩展点接口

当前基础扩展点接口是：

- `PluginExtensionPoint`

它本身继承：

- `ExtensionPoint`

业务接口要继续继承它，例如：

```java
public interface Greeting extends PluginExtensionPoint {
    String getGreeting();
}
```

这样宿主工程和插件实现就会围绕同一个扩展接口协作。

## 第 2 步：实现插件主类

当前示例插件主类是：

- `HelloPlugin`

它继承：

- `SpringPlugin`

并重写：

- `createApplicationContext()`

典型写法是：

1. 创建 `AnnotationConfigApplicationContext`
2. 设置插件自己的 `ClassLoader`
3. 注册插件自己的 Spring 配置类
4. `refresh()`

这表示当前插件不是简单静态类集合，而是可以拥有：

- 自己的 Spring 容器
- 自己的 Bean 定义
- 自己的内部依赖关系

## 第 3 步：定义插件内部 Spring 配置

示例里使用：

- `ExamplePluginConfiguration`

这个类负责在插件上下文里声明自己的 Bean，例如：

- `GreetingService`

这样插件扩展实现类就可以直接使用：

- `@Autowired`

注入插件内部服务。

## 第 4 步：实现扩展类

示例扩展类是：

- `HelloGreeting`

关键点有两个：

1. 实现扩展点接口 `Greeting`
2. 添加 `@Extension`

例如：

```java
@Extension
public class HelloGreeting implements Greeting {
    @Autowired
    private GreetingService greetingService;

    @Override
    public String getGreeting() {
        return greetingService.rtnString();
    }
}
```

这里说明当前实现支持：

- PF4J 扩展点发现
- 插件内部 Spring Bean 注入

## 第 5 步：配置 `plugin.properties`

示例插件中：

- `src/main/plugin.properties`

当前包含：

- `plugin.class`
- `plugin.dependencies`
- `plugin.id`
- `plugin.provider`
- `plugin.version`

示例：

```properties
plugin.class=cn.geelato.plugin.example.HelloPlugin
plugin.dependencies=x, y, z
plugin.id=example-plugin
plugin.provider=geelato
plugin.version=0.0.1
```

这些字段描述的是：

- 插件主类
- 插件 ID
- 插件版本
- 插件提供者
- 插件依赖关系

## 第 6 步：打包插件

当前插件模块需要在 `pom.xml` 中至少关注两件事：

### 依赖 `plugin-all`

例如示例插件依赖：

- `cn.geelato:plugin-all`

这是为了拿到：

- 扩展点接口
- 共享契约对象

### 配置 PF4J 扩展注解处理器

示例里通过：

- `maven-compiler-plugin`

声明：

- `org.pf4j.processor.ExtensionAnnotationProcessor`

这是为了生成：

- `META-INF/extensions.idx`

没有这个文件，扩展点发现通常会失败。

### 配置 JAR Manifest

示例插件还通过：

- `maven-jar-plugin`

写入：

- `Plugin-Class`
- `Plugin-Id`
- `Plugin-Version`
- `Plugin-Description`
- `Plugin-Provider`

这部分是 PF4J 识别插件包的重要元数据。

## 打包后应检查什么

打包成功后，建议检查插件 jar 至少包含：

- `META-INF/extensions.idx`
- `META-INF/MANIFEST.MF`
- `plugin.properties`

如果缺少这些内容，通常意味着：

- 扩展注解处理器未生效
- 插件主类元数据未写入
- 插件描述文件位置不正确

## 推荐目录结构

当前推荐结构仍然是：

```text
example-plugin
├─ pom.xml
└─ src
   └─ main
      ├─ java
      │  └─ cn/geelato/plugin/example
      │     ├─ HelloPlugin.java
      │     ├─ ExamplePluginConfiguration.java
      │     ├─ HelloGreeting.java
      │     └─ GreetingService.java
      └─ plugin.properties
```

而扩展点接口则放在：

- `geelato-plugin-all`

## 宿主工程如何调用插件

宿主工程标准调用方式是：

1. 注入 `PluginBeanProvider`
2. 通过接口类型和插件 ID 获取扩展实例
3. 调用扩展方法

例如：

```java
Greeting greeting = pluginBeanProvider.getBean(Greeting.class, "example-plugin");
String result = greeting.getGreeting();
```

如果拿不到扩展，当前会抛出：

- `UnFoundPluginException`

所以调用侧要做好异常兜底。

## 当前开发建议

- 共享接口始终放在 `geelato-plugin-all`
- 插件实现不要把宿主工程强耦合进去
- 插件主类优先继承 `SpringPlugin`
- 每个扩展实现都明确加 `@Extension`
- 尽量让插件内部 Bean 都收敛到插件自己的配置类中
- 对插件 ID、版本号、`plugin.properties` 和 JAR Manifest 保持一致

## 推荐继续阅读

- [概览](overview.md)
- [加载、启停与卸载](lifecycle.md)
- [插件仓库配置](repository.md)
