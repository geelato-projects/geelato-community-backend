# PlatformDesginer

`PlatformDesginer` 是 Geelato Web 平台的设计时应用壳。它是基于共享底座 `geelato-web-platform` 启动的可执行入口，面向设计时与平台管理场景。

## 它是什么

`PlatformDesginer` 是一个 Spring Boot 启动类：

- 包名：`cn.geelato.web.designer`
- 类名：`cn.geelato.web.designer.PlatformDesginer`
- `main` 入口：`cn.geelato.web.designer.PlatformDesginer`
- 所在模块：`geelato-web-designer`

完整源码：

```java
@SpringBootApplication(scanBasePackages = {"cn.geelato"})
@EnableConfigurationProperties
@EnableCaching
@EnableAsync(proxyTargetClass = true)
public class PlatformDesginer extends BootApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformDesginer.class, args);
    }
}
```

它继承自 `cn.geelato.web.platform.boot.BootApplication`，后者是 Geelato 共享的运行时启动协调器。完整启动链路见 [启动过程](../reference/startup-process.md)。

## 模块坐标

| 项目 | 值 |
|---|---|
| Maven `artifactId` | `geelato-web-designer` |
| 模块路径 | `geelato-community/geelato-web-designer` |
| 打包方式 | `jar` |
| `spring-boot-maven-plugin` `mainClass` | `cn.geelato.web.designer.PlatformDesginer` |
| `spring-boot-maven-plugin` `classifier` | `exec` |
| 直接依赖 | `geelato-web-platform`、`spring-boot-starter-test`（test scope） |

该模块刻意只保留一个直接依赖（`geelato-web-platform`），所有平台能力都通过共享底座传递引入。

## 默认配置

`geelato-web-designer/src/main/resources/application.properties`：

```properties
spring.application.name=geelato-web-designer
logging.level.root=INFO
logging.level.cn.geelato=INFO
```

应用壳目前只覆盖应用名与日志级别。其他所有配置（数据源、上传、安全、插件目录等）来自共享底座或消费该壳的业务工程。

## 它带来了什么

启动时，它会拉起共享底座声明的全部内容，包括：

- `geelato-web-platform` 的全部 controller 和 service
- `BootApplication` 的默认启动链路（数据源、SQL/Graal 脚本、环境缓存）
- 框架 starter 装配（`geelato-framework-starter`）
- 元数据加载层（`geelato-meta`）
- 插件运行时（`pf4j` + `geelato-plugins/geelato-plugin-all`）
- 共享底座声明的 OSS、package、安全、Redis 等集成

它启用了 Spring 缓存与异步执行（CGLIB 代理：`@EnableAsync(proxyTargetClass = true)`），并暴露 `@ConfigurationProperties` Bean。

## 与 `BootApplication` 的关系

`PlatformDesginer` 不重复任何启动工作。它只设置 `cn.geelato` 扫描基包，并启用缓存、异步和配置属性。真正的启动工作由 `BootApplication.run(...)` 完成：

1. `DataSourceManager.parseDataSourceMeta(dao)`
2. SQL / DB 脚本加载（开发态或 fat-jar 态）
3. Graal 服务和变量扫描
4. `EnvManager.EnvInit()`

详见 [启动过程](../reference/startup-process.md)。

## 如何运行

```bash
mvn -pl geelato-web-designer spring-boot:run
```

或构建可执行 jar：

```bash
mvn -pl geelato-web-designer clean package
java -jar geelato-web-designer/target/geelato-web-designer-1.0.0-SNAPSHOT-exec.jar
```

## 与 `PlatformWebRuntime` 的关系

`PlatformDesginer` 与 [PlatformWebRuntime](platform-web-runtime.md) 是结构完全一致的两个应用壳。它们：

- 都继承 `BootApplication`
- 都只依赖 `geelato-web-platform`
- 都使用同一组 Spring 注解
- 区别仅在 `spring.application.name` 和 `main` 类的 FQN

代码层面没有设计时 / 运行时的"开关"。这两个 jar 是两个独立的可部署入口。业务工程选其中一个作为 `main` 类即可。共享底座目前未拆分 endpoint 表面，两个壳当前都暴露 `geelato-web-platform` 的完整 endpoint 集合。

类名 `Desginer` 是该模块历史沿用的拼写，现保留不变以保证兼容性。部署侧的说明见 [普通部署](../operations/runtime-designer-deployment.md)。

## 推荐继续阅读

- [PlatformWebRuntime](platform-web-runtime.md)
- [启动过程](../reference/startup-process.md)
- [普通部署](../operations/runtime-designer-deployment.md)
- [BOM 与 Starter](../reference/bom-and-starter.md)
- [SecurityContext 生命周期](security-context-lifecycle.md)
