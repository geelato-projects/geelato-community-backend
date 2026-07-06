# PlatformDesginer

`PlatformDesginer` is the designer-oriented application shell of the Geelato web platform. It is the runnable entry that boots the shared `geelato-web-platform` base for design-time and platform-administration scenarios.

## What It Is

`PlatformDesginer` is a Spring Boot main class:

- package: `cn.geelato.web.designer`
- class: `cn.geelato.web.designer.PlatformDesginer`
- main class: `cn.geelato.web.designer.PlatformDesginer`
- declared in module: `geelato-web-designer`

The full source is:

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

It extends `cn.geelato.web.platform.boot.BootApplication`, the shared Geelato runtime bootstrap coordinator. See [Startup Process](../reference/startup-process.md) for the full chain.

## Module Coordinates

| Item | Value |
|---|---|
| Maven `artifactId` | `geelato-web-designer` |
| Module path | `geelato-community/geelato-web-designer` |
| Packaging | `jar` |
| `spring-boot-maven-plugin` `mainClass` | `cn.geelato.web.designer.PlatformDesginer` |
| `spring-boot-maven-plugin` `classifier` | `exec` |
| Direct dependencies | `geelato-web-platform`, `spring-boot-starter-test` (test scope) |

The module deliberately has only one direct dependency (`geelato-web-platform`). All platform capabilities come transitively from the shared base.

## Default Configuration

`geelato-web-designer/src/main/resources/application.properties`:

```properties
spring.application.name=geelato-web-designer
logging.level.root=INFO
logging.level.cn.geelato=INFO
```

The shell currently overrides only the application name and log levels. All other settings (datasource, upload, security, plugin directory, etc.) come from the shared base or the host project that consumes this shell.

## What It Provides

When the shell starts, it brings up everything the shared base declares, including:

- the `geelato-web-platform` controllers and services
- the default `BootApplication` startup chain (datasource, SQL/Graal scripts, environment cache)
- the framework starter wiring (`geelato-framework-starter`)
- the metadata loading layer (`geelato-meta`)
- the plugin runtime (`pf4j` + `geelato-plugins/geelato-plugin-all`)
- the OSS, package, security, and Redis integrations declared by the shared base

It enables Spring caching and async execution with CGLIB proxying (`@EnableAsync(proxyTargetClass = true)`) and exposes `@ConfigurationProperties` beans.

## Relationship with `BootApplication`

`PlatformDesginer` does not duplicate any startup work. It only sets the `cn.geelato` scan base and enables caching, async, and configuration properties. The actual startup work happens in `BootApplication.run(...)`:

1. `DataSourceManager.parseDataSourceMeta(dao)`
2. SQL / DB script loading (exploded mode or fat-jar mode)
3. Graal service and variable scanning
4. `EnvManager.EnvInit()`

See [Startup Process](../reference/startup-process.md) for details.

## How to Run

```bash
mvn -pl geelato-web-designer spring-boot:run
```

or build the runnable jar:

```bash
mvn -pl geelato-web-designer clean package
java -jar geelato-web-designer/target/geelato-web-designer-1.0.0-SNAPSHOT-exec.jar
```

## Relationship with `PlatformWebRuntime`

`PlatformDesginer` and [PlatformWebRuntime](platform-web-runtime.md) are structurally identical shells. They:

- both extend `BootApplication`
- both depend only on `geelato-web-platform`
- both have the same Spring annotations
- differ only in `spring.application.name` and the main class FQN

There is no code-level "switch" between designer and runtime mode. The two jars are two independent deployable entry points. A host project picks one of them as its `main` class. Splitting endpoint surface between the two is not yet part of the shared base; both shells currently expose the full endpoint set from `geelato-web-platform`.

The shell naming `Desginer` is a long-standing historical name in this module; the class is preserved as-is for compatibility. See [Runtime / Designer Deployment and Dependencies](../operations/runtime-designer-deployment.md) for the deployment-side explanation.

## Suggested Reading

- [PlatformWebRuntime](platform-web-runtime.md)
- [Startup Process](../reference/startup-process.md)
- [Runtime / Designer Deployment and Dependencies](../operations/runtime-designer-deployment.md)
- [BOM and Starter](../reference/bom-and-starter.md)
- [SecurityContext Lifecycle](security-context-lifecycle.md)
