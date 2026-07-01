# 启动过程

这篇文档说明基于：

- `cn.geelato.web.platform.boot.BootApplication`

启动一个 Geelato Runtime 应用时，框架在启动阶段到底做了什么，顺序如何，以及元数据、SQL、数据源、Graal 上下文分别在什么阶段进入内存。

本文主要基于：

- `BootApplication`
- `MetaConfiguration`
- `MetaManager`
- `DataSourceManager`
- `SqlScriptManager`
- `DbScriptManager`
- `GraalManager`
- `EnvManager`

## 先理解两层启动

从应用开发者视角看，通常会写自己的启动类：

```java
@SpringBootApplication(scanBasePackages = {"cn.geelato", "com.acme.order"})
public class AcmeOrderApplication extends BootApplication {
}
```

这里有两层启动同时存在：

1. Spring Boot 先启动 ApplicationContext
2. Spring 容器就绪后，再执行 `BootApplication.run(...)`

所以：

- `BootApplication` 不是 Spring Boot 的最外层入口
- 它更像是 Geelato Runtime 自己的启动编排器

## 启动总时序

可以把当前启动链路粗略理解为下面这条时序：

1. Spring Boot 创建并刷新 ApplicationContext
2. `@ComponentScan("cn.geelato")` 与业务侧 `scanBasePackages` 一起完成 Bean 扫描
3. `MetaConfiguration` 在容器初始化阶段执行，完成元数据扫描与数据库元数据装载
4. Spring 完成 Bean 创建后，执行 `BootApplication.run(...)`
5. `BootApplication` 初始化数据源定义缓存
6. `BootApplication` 装载 SQL 脚本和数据库脚本
7. `BootApplication` 扫描 Graal Service / Variable
8. `BootApplication` 初始化环境配置缓存
9. Runtime 启动完成，对外提供能力

也就是说：

- 元数据初始化早于 `BootApplication.run(...)`
- `run(...)` 负责的是运行期管理器的收尾初始化

## 第 1 阶段：Spring 扫描和 Bean 装配

`BootApplication` 自身带有：

```java
@ComponentScan(basePackages = {"cn.geelato"})
```

这意味着：

- 框架默认会扫描 `cn.geelato` 下的运行时 Bean

而业务工程通常还会在自己的 `@SpringBootApplication` 上再补：

- 业务包名

例如：

- `cn.geelato`
- `com.acme.order`

这样最终形成：

- 框架 Bean 被 Spring 扫到
- 业务实体、Controller、Service 也被 Spring 扫到

如果只扫业务包，不扫 `cn.geelato`，框架运行时能力会缺失。

如果只扫 `cn.geelato`，业务自己的 Bean 又不会进入容器。

## 第 2 阶段：元数据初始化

### 元数据初始化入口

元数据初始化并不在 `BootApplication.run(...)` 里，而是在：

- `MetaConfiguration`

的 `setApplicationContext(...)` 中完成。

这里会做两件事：

1. `initClassPackageMeta()`
2. `initDataBaseMeta()`

也就是：

- 先扫类注解元数据
- 再装数据库元数据

### 2.1 扫描类注解元数据

`MetaConfiguration` 会读取：

- `geelato.meta.scan-package-names`

默认值是：

- `cn.geelato`

然后逐包调用：

- `MetaManager.scanAndParse(packageName, false)`

`MetaManager` 内部再通过：

- `ClassScanner.scan(packageName, true, Entity.class)`

扫描带有：

- `@Entity`

注解的类。

随后每个类会进入：

- `MetaManager.parseOne(clazz)`

这里会把类上的信息解析成：

- `EntityMeta`
- `FieldMeta`
- `ColumnMeta`

并缓存到内存中的多个 Map 里，例如：

- `entityMetadataMap`
- `entityMetadataMapFromClass`
- `tableNameMetadataMap`

所以类元数据的来源是：

- Java 注解

而不是数据库表。

### 2.2 数据库元数据装载

类扫描完成后，`MetaConfiguration` 接着调用：

- `metaManager.parseDBMeta(dao)`

这一步会从数据库元数据表中装载：

- 表
- 列
- 视图
- 校验规则
- 外键关系

默认来源由：

- `DefaultMetaStore`

提供。

`DefaultMetaStore` 会通过默认 SQL 查询以下几类元数据定义，再封装成 `MetaDefinitionBundle`：

- 表定义列表
- 列定义列表
- 视图定义列表
- Check 定义列表
- Foreign Key 定义列表

然后 `MetaManager` 对这些结果做二次解析：

- `parseTableEntity(...)`
- `parseViewEntity(...)`

最后把数据库来源的元数据合并进统一缓存。

### 2.3 类元数据和数据库元数据如何并存

`MetaManager` 当前会分别维护：

- `entityMetadataMapFromClass`
- `entityMetadataMapFromDatabase`

并在统一的：

- `entityMetadataMap`

中提供最终对外访问。

因此启动后，元数据并不是只有一种来源，而是：

- 一部分来自 Java 注解
- 一部分来自平台元数据表

这也是为什么 Runtime 既能识别静态实体，也能识别设计时配置出的模型和视图。

### 2.4 `MetaBootstrap` 的位置

数据库元数据装载完成后，如果容器里存在：

- `MetaBootstrap`

还会继续执行：

- `metaBootstrap.bootstrap(metaManager, dao)`

所以它是：

- 元数据初始化完成后的二次增强入口

适合做：

- 元数据补丁
- 启动时元数据注册
- 宿主工程自定义元数据收尾动作

## 第 3 阶段：进入 `BootApplication.run(...)`

当 Spring 容器完成初始化后，才会执行：

- `BootApplication.run(String... args)`

这个方法当前的顺序非常明确：

1. 记录启动参数和配置文件信息
2. 初始化数据源定义缓存
3. 加载 SQL / DB 脚本
4. 初始化 Graal 上下文
5. 初始化环境配置缓存
6. 输出版本信息

下面逐项说明。

## 第 4 阶段：数据源定义加载

在 `run(...)` 中，首先会处理：

- `DataSourceDefinitionLoader`

如果 Spring 容器里存在自定义实现，就会先执行：

- `DataSourceManager.singleInstance().setDefinitionLoader(dataSourceDefinitionLoader)`

然后执行：

- `DataSourceManager.singleInstance().parseDataSourceMeta(dao)`

这一步的动作是：

1. 把当前主数据源注册成 `primary`
2. 通过 `definitionLoader.load(dao)` 读取动态数据源定义
3. 把每条连接定义先缓存进 `lazyDynamicDataSourceMap`

这里要注意：

- 这一步通常只是把连接定义缓存起来
- 并不会在启动时就把所有动态数据源立即建好

真正访问某个 `connectId` 时，`DataSourceManager.getDataSource(connectId)` 才会延迟创建真实的 `HikariDataSource`。

所以当前策略是：

- 启动时缓存定义
- 运行时按需建连

## 第 5 阶段：SQL 脚本与数据库脚本加载

接下来 `BootApplication` 会执行：

- `resolveSqlScript(args)`

它会根据运行形态分成两条路径。

### 5.1 开发态 / exploded 目录运行

如果当前不是 fat jar 资源模式，就走：

- `initFromExploreFile(...)`

这里会做两件事：

1. `SqlScriptManagerFactory.get("sql").loadFiles(path + "/geelato/web/platform/sql/")`
2. `DbScriptManagerFactory.get("db").setDao(dao)` 后执行 `loadDb()`

第一步表示：

- 扫描 classpath 下 `geelato/web/platform/sql/` 目录中的 SQL 文件

这些 `.sql` 文件会由 `SqlScriptManager` 解析，编译成可执行的 SQL 模板函数。

第二步表示：

- 从数据库表 `platform_sql` 中加载数据库脚本定义

### 5.2 fat jar 运行

如果是单 fat jar 方式运行，则改走：

- `initFromFatJar()`

此时不能依赖文件系统目录，而是改成：

- `SqlScriptManagerFactory.get("sql").loadResource("/geelato/web/platform/sql/**/*.sql")`

也就是：

- 通过资源流扫描 jar 内部 SQL 资源

随后同样调用：

- `DbScriptManagerFactory.get("db").loadDb()`

### 5.3 SQL 脚本加载后的结果

这一阶段完成后，运行时会拥有两类 SQL 能力：

- 资源目录中的静态 SQL 脚本
- `platform_sql` 表中的数据库脚本

其中：

- `SqlScriptManager`
  - 负责 classpath SQL 资源
- `DbScriptManager`
  - 负责数据库脚本表

### 5.4 为什么 `platform_sql` 不存在也能启动

`DbScriptManager.loadDb()` 现在有保护逻辑。

它会先检查：

- `platform_sql`

表是否存在。

如果不存在，不会直接让应用启动失败，而是：

- 记录日志
- 跳过数据库脚本加载

所以脚本表缺失不会阻断整个 Runtime 启动。

## 第 6 阶段：Graal 上下文扫描

之后 `BootApplication` 会执行：

- `resolveGraalContext()`

它读取：

- `geelato.graal.scan-package-names`

默认值也是：

- `cn.geelato`

然后逐包调用：

- `GraalManager.initGraalService(packageName)`
- `GraalManager.initGraalVariable(packageName)`

### 6.1 Graal Service 扫描

`GraalManager` 会扫描带有：

- `@GraalService`

注解的类，并为它们：

- 反射创建实例
- 收集方法级 `@GraalFunction`
- 注册到 `graalServiceMap` 或 `globalGraalServiceMap`

此外还会尝试给这些服务注入：

- `Dao`

优先拿：

- `dynamicDao`

拿不到再退回普通 `Dao` Bean。

### 6.2 Graal Variable 扫描

同时也会扫描带有：

- `@GraalVariable`

注解的类，并注册到：

- `graalVariableMap`

所以这一阶段本质上是在初始化：

- 脚本执行上下文中的服务对象
- 脚本执行上下文中的变量对象

## 第 7 阶段：环境配置缓存初始化

最后 `BootApplication` 会执行：

- `initEnvironment()`

其内部逻辑是：

1. `EnvManager.singleInstance().setJdbcTemplate(dao.getJdbcTemplate())`
2. `EnvManager.singleInstance().EnvInit()`

而 `EnvInit()` 当前首先会做：

- `LoadSysConfig()`

也就是从：

- `platform_sys_config`

表中查询启用状态的系统配置，并放入内存缓存：

- `sysConfigMap`
- `sysConfigClassifyMap`

所以应用启动完成后，系统配置已经可以通过 `EnvManager` 直接读取，而不需要每次重新查库。

## 启动链路里“元数据”到底是怎么进来的

如果只看“元数据是如何加载和扫描的”，可以总结成下面这条链路：

1. Spring 初始化 `MetaConfiguration`
2. 读取 `geelato.meta.scan-package-names`
3. `MetaManager` 扫描指定包下所有 `@Entity`
4. 把注解实体解析成类元数据并放入缓存
5. 使用 `primaryDao` 查询元数据表
6. `DefaultMetaStore` 装载表、列、视图、校验、外键定义
7. `MetaManager` 再把数据库定义解析成表实体和视图实体
8. 如存在 `MetaBootstrap`，执行二次增强

因此最终进入运行时缓存的元数据，既包含：

- 代码里声明的实体
- 数据库里配置的表模型和视图模型

## 一个容易混淆的点

很多人会把以下两件事混在一起：

- Spring Bean 扫描
- 元数据实体扫描

它们并不是一回事。

### Spring Bean 扫描

关注的是：

- `@Component`
- `@Service`
- `@Controller`
- `@Configuration`

目标是把对象装进 Spring 容器。

### 元数据实体扫描

关注的是：

- `@Entity`

目标是把实体结构解析成：

- `EntityMeta`
- `FieldMeta`
- `ColumnMeta`

并放入 `MetaManager`。

所以一个类：

- 可以只是 Spring Bean
- 可以只是元数据实体
- 也可以两者兼有

## 推荐的阅读顺序

如果你要继续深入启动机制，建议按下面顺序阅读：

1. `BootApplication`
2. `MetaConfiguration`
3. `MetaManager`
4. `DataSourceManager`
5. `SqlScriptManager` / `DbScriptManager`
6. `GraalManager`
7. `EnvManager`

这样最容易把“Spring 启动”和“Geelato Runtime 初始化”分开理解。

## 总结

当前 `BootApplication` 启动时，核心完成的是四件事：

1. 装载动态数据源定义
2. 装载资源 SQL 和数据库脚本
3. 扫描 Graal 服务与变量
4. 初始化系统配置缓存

而元数据初始化则更早发生在：

- `MetaConfiguration`

里，先扫类，再读数据库，再执行 `MetaBootstrap`。

因此完整启动顺序可以记成：

- Spring 扫 Bean
- MetaConfiguration 扫元数据
- BootApplication 初始化运行期管理器
- Runtime 完成启动

## 推荐继续阅读

- [核心模块说明](core-modules.md)
- [MetaStore 扩展](metastore-extension.md)
- [动态数据源能力](../dynamic-datasource/overview.md)
- [系统配置](../system-config/overview.md)
