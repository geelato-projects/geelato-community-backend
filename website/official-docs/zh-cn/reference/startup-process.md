---
title: 启动过程
sidebar_label: 启动过程
---

# 启动过程

本页说明基于 `cn.geelato.web.platform.boot.BootApplication` 启动 Geelato Runtime 应用时，框架在启动阶段的执行顺序，以及元数据、SQL、数据源、Graal 上下文、系统配置分别在哪个阶段完成初始化。

## 两层启动

应用启动类通常继承 `BootApplication`：

```java
@SpringBootApplication(scanBasePackages = {"cn.geelato", "com.acme.order"})
public class AcmeOrderApplication extends BootApplication {
}
```

启动分两层：

1. Spring Boot 创建并刷新 ApplicationContext，完成 Bean 扫描。
2. Spring 容器就绪后，执行 `BootApplication.run(...)`，由 Geelato Runtime 完成运行期管理器的收尾初始化。

因此 `BootApplication` 并非 Spring Boot 的最外层入口，而是 Geelato Runtime 的启动编排器。元数据初始化发生在前一层（`MetaConfiguration`），早于 `BootApplication.run(...)`。

## 启动时序概览

1. Spring Boot 创建并刷新 ApplicationContext。
2. `@ComponentScan("cn.geelato")` 与业务侧 `scanBasePackages` 共同完成 Bean 扫描。
3. `MetaConfiguration` 在容器初始化阶段完成元数据扫描与数据库元数据装载。
4. Spring 完成 Bean 创建后，执行 `BootApplication.run(...)`。
5. `BootApplication` 初始化数据源定义缓存。
6. `BootApplication` 装载 SQL 脚本与数据库脚本。
7. `BootApplication` 扫描 Graal Service / Variable。
8. `BootApplication` 初始化环境配置缓存。
9. Runtime 启动完成，对外提供能力。

要点：元数据初始化早于 `BootApplication.run(...)`；`run(...)` 负责运行期管理器的收尾初始化。

## 阶段一：Spring 扫描与 Bean 装配

`BootApplication` 自带 `@ComponentScan(basePackages = {"cn.geelato"})`，框架默认扫描 `cn.geelato` 下的运行时 Bean。业务工程通常在 `@SpringBootApplication` 上补充业务包名（如 `cn.geelato`、`com.acme.order`），使框架 Bean 与业务实体、Controller、Service 同时进入容器。

- 仅扫业务包、不扫 `cn.geelato`：框架运行时能力会缺失。
- 仅扫 `cn.geelato`、不扫业务包：业务 Bean 不会进入容器。

## 阶段二：元数据初始化

元数据初始化不在 `BootApplication.run(...)` 中，而在 `MetaConfiguration` 的 `setApplicationContext(...)` 中完成，包含两步：先扫描类注解元数据，再装载数据库元数据。

### 扫描类注解元数据

`MetaConfiguration` 读取 `geelato.meta.scan-package-names`（默认 `cn.geelato`），逐包扫描带 `@Entity` 注解的类，将其解析为 `EntityMeta`、`FieldMeta`、`ColumnMeta` 并缓存。类元数据来源是 Java 注解，而非数据库表。

### 装载数据库元数据

类扫描完成后，`MetaConfiguration` 调用元数据装载，从数据库元数据表中读取表、列、视图、校验规则、外键关系。默认来源由 `DefaultMetaStore` 提供，结果封装为 `MetaDefinitionBundle`，再经 `MetaManager` 解析为表实体与视图实体，合并进统一缓存。

启动后元数据有两种来源：一部分来自 Java 注解（静态实体），一部分来自平台元数据表（设计时配置的模型与视图）。这也是 Runtime 既能识别静态实体、也能识别设计时模型与视图的原因。

### MetaBootstrap 二次增强

数据库元数据装载完成后，若容器中存在 `MetaBootstrap`，会执行 `metaBootstrap.bootstrap(metaManager, dao)`，作为元数据初始化完成后的增强入口，适用于元数据补丁、启动时元数据注册、宿主工程自定义收尾动作。

## 阶段三：BootApplication.run(...) 执行

Spring 容器完成初始化后执行 `BootApplication.run(String... args)`，顺序如下：

1. 记录启动参数与配置文件信息。
2. 初始化数据源定义缓存。
3. 加载 SQL / DB 脚本。
4. 初始化 Graal 上下文。
5. 初始化环境配置缓存。
6. 输出版本信息。

## 阶段四：数据源定义加载

`run(...)` 处理 `DataSourceDefinitionLoader`（若容器中存在自定义实现），将主数据源注册为 `primary`，通过 `definitionLoader.load(dao)` 读取动态数据源定义并缓存连接定义。

此阶段仅缓存连接定义，不在启动时立即建立所有动态数据源。真正访问某个 `connectId` 时，由 `DataSourceManager.getDataSource(connectId)` 按需延迟创建真实数据源。即：启动时缓存定义，运行时按需建连。

## 阶段五：SQL 脚本与数据库脚本加载

`resolveSqlScript(args)` 根据运行形态分两条路径：

### 开发态 / exploded 目录运行

非 fat jar 模式下扫描 classpath 下 `geelato/web/platform/sql/` 目录中的 SQL 文件，解析编译为可执行的 SQL 模板函数；同时从数据库表 `platform_sql` 加载数据库脚本定义。

### fat jar 运行

单 fat jar 方式下改用资源流扫描 jar 内部 SQL 资源（`/geelato/web/platform/sql/**/*.sql`），随后同样从 `platform_sql` 加载数据库脚本。

加载完成后，运行时具备两类 SQL 能力：资源目录中的静态 SQL 脚本（`SqlScriptManager` 管理）与 `platform_sql` 表中的数据库脚本（`DbScriptManager` 管理）。

`DbScriptManager.loadDb()` 带有保护逻辑：若 `platform_sql` 表不存在，记录日志并跳过加载，不阻断应用启动。

## 阶段六：Graal 上下文扫描

`resolveGraalContext()` 读取 `geelato.graal.scan-package-names`（默认 `cn.geelato`），逐包扫描并初始化 Graal 服务与变量：

- **Graal Service**：扫描带 `@GraalService` 注解的类，反射创建实例、收集 `@GraalFunction` 方法并注册，同时尝试注入 `Dao`（优先 `dynamicDao`，退回普通 `Dao` Bean）。
- **Graal Variable**：扫描带 `@GraalVariable` 注解的类并注册。

该阶段初始化的是脚本执行上下文中的服务对象与变量对象。

## 阶段七：环境配置缓存初始化

`initEnvironment()` 通过 `EnvManager` 设置 `JdbcTemplate` 并执行初始化，从 `platform_sys_config` 表查询启用状态的系统配置放入内存缓存。

应用启动完成后，系统配置可通过 `EnvManager` 直接读取，无需每次重新查库。

## 元数据加载链路

元数据加载与扫描的完整链路：

1. Spring 初始化 `MetaConfiguration`。
2. 读取 `geelato.meta.scan-package-names`。
3. `MetaManager` 扫描指定包下所有 `@Entity`。
4. 将注解实体解析为类元数据并缓存。
5. 使用 `primaryDao` 查询元数据表。
6. `DefaultMetaStore` 装载表、列、视图、校验、外键定义。
7. `MetaManager` 将数据库定义解析为表实体与视图实体。
8. 若存在 `MetaBootstrap`，执行二次增强。

最终进入运行时缓存的元数据，既包含代码里声明的实体，也包含数据库里配置的表模型与视图模型。

## 易混点：Spring Bean 扫描与元数据实体扫描

两者并非同一件事：

| 扫描类型 | 关注注解 | 目标 |
| --- | --- | --- |
| Spring Bean 扫描 | `@Component`、`@Service`、`@Controller`、`@Configuration` | 将对象装入 Spring 容器 |
| 元数据实体扫描 | `@Entity` | 将实体结构解析为 `EntityMeta`、`FieldMeta`、`ColumnMeta`，放入 `MetaManager` |

一个类可以只是 Spring Bean，可以只是元数据实体，也可以两者兼有。

## 实现说明

以上为面向使用者的概念性说明。若需深入内部实现，以下管理器维护了对应的内部缓存与数据结构：

- `MetaManager`：分别维护来自类（`entityMetadataMapFromClass`）与来自数据库（`entityMetadataMapFromDatabase`）的元数据，并在统一的 `entityMetadataMap` 中对外提供访问。
- `DataSourceManager`：动态数据源连接定义缓存在 `lazyDynamicDataSourceMap`。
- `EnvManager`：系统配置缓存在 `sysConfigMap` 与 `sysConfigClassifyMap`。

建议按以下顺序阅读源码以区分"Spring 启动"与"Geelato Runtime 初始化"：`BootApplication` → `MetaConfiguration` → `MetaManager` → `DataSourceManager` → `SqlScriptManager` / `DbScriptManager` → `GraalManager` → `EnvManager`。

## 总结

`BootApplication` 启动完成四件事：装载动态数据源定义、装载资源 SQL 与数据库脚本、扫描 Graal 服务与变量、初始化系统配置缓存。元数据初始化更早发生在 `MetaConfiguration`，先扫类、再读数据库、再执行 `MetaBootstrap`。完整启动顺序可概括为：Spring 扫 Bean → MetaConfiguration 扫元数据 → BootApplication 初始化运行期管理器 → Runtime 完成启动。

## 继续阅读

- [核心模块说明](core-modules.md)
- [MetaStore 扩展](metastore-extension.md)
- [动态数据源能力](../dynamic-datasource/overview.md)
- [系统配置](../system-config/overview.md)
