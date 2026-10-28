---
title: 自定义 Kit 开发
sidebar_label: 自定义 Kit 开发
---

# 自定义 Kit 开发

内置 kit（见[内置 Kit](/docs/script/kits)）之外，集成方可以把自有能力开发为 kit 注入脚本：一个标注注解的 Java 类 + 一次注册。SDK 内置的 `cn.geelato.script.kit.demo.DemoService` 是参考模板。

## 注解模型

kit 的全部元数据来自两个注解（定义于 `cn.geelato.core.graal`）：

```java
@GraalService(name = "demo", built = "true", descrption = "演示 kit（模板）：hello/add")
public class DemoService {

    private final KitContext ctx;

    public DemoService(KitContext ctx) {
        this.ctx = ctx;
    }

    @GraalFunction(example = "$gl.demo.hello('world')", description = "回显问好并携带本次执行 execId")
    public Map<String, Object> hello(String name) {
        return Map.of(
                "message", "hello " + (name == null || name.isBlank() ? "world" : name),
                "execId", String.valueOf(ctx.getExecId()));
    }

    @GraalFunction(example = "$gl.demo.add(1, 2)", description = "两数相加")
    public long add(long a, long b) {
        return a + b;
    }
}
```

| 注解属性 | 说明 |
| :--- | :--- |
| `@GraalService.name` | 脚本侧命名空间，也是注册与档案白名单匹配的 key，全局唯一（冲突启动即报错） |
| `@GraalService.built` | `"true"` 挂到 `$gl.{name}`；`"false"`（缺省）挂顶层变量 `${name}` |
| `@GraalService.descrption` | 描述文本，进 `$gl.help()` 与 catalog 清单 |
| `@GraalFunction.example` | 调用示例，进 help 详情与 catalog，供编辑器补全展示 |
| `@GraalFunction.description` | 方法说明 |

只有标注 `@GraalFunction` 的 public 方法会暴露给脚本，其余方法不可见。

## 方法签名约束

参数与返回类型必须在 interop 兼容白名单内，否则**启动即 fail-fast**（`IllegalStateException`，指明类#方法与违规类型）：

- 参数允许：String、数值（Number 家族与原始类型）、Boolean、Map、List、Date、Object、polyglot Value；
- 返回允许：同上外加 void；
- 泛型集合按原始类型（Map/List）判定；Set 等其他类型不支持。

## 单例与 scoped

| 形态 | 判定方式 | 生命周期 | 适用 |
| :--- | :--- | :--- | :--- |
| 单例 | 无参构造器，或以**实例**注册 | 全引擎复用一个实例 | 无状态 kit |
| scoped | 声明 `(KitContext)` 构造器（唯一 public 构造器参数恰为 KitContext），或经 `registerScoped` 工厂通道注册 | **每次执行实例化**，互不串扰 | 需要执行级上下文的 kit |

scoped kit 经构造器拿到的 `KitContext` 是单次执行的上下文：`execId`、身份快照（`identity`）、注入变量（`bindings`，`$gl.ctx`/`$gl.parameter` 的数据源）、执行级属性袋（`attributes`）、行数计数器（`addRows`）、kit 调用留痕（`record`）以及宿主装配的 SPI（数据源、ORM 供给等）。

带外部依赖（Spring Bean 等）不适合构造器直接创建时，走工厂通道：

```java
builder.registerScoped("demo", DemoService.class, ctx -> new DemoService(depService, ctx));
```

## 注册方式（三选一）

**1. 配置扫描**：把 kit 所在包加入扫描清单，无需写注册代码：

```properties
geelato.script.engine.scan-packages=cn.geelato.example.rulekit
```

**2. 编程注册**：非 Spring 环境或单测中直接在 Builder 上注册（Spring Bean 优先传实例，避免重复实例化）：

```java
ScriptEngine engine = new ScriptEngineBuilder()
        .registerKit(new DemoKit())          // 实例（恒单例）
        .registerKit(OtherKit.class)         // 类（按构造器形状判定 scoped）
        .build();
```

**3. Spring 定制器**：定义 `ScriptEngineCustomizer` Bean，自动装配时按序应用：

```java
@Bean
public ScriptEngineCustomizer demoKitCustomizer() {
    return builder -> builder.registerKit(new DemoKit());
}
```

命名空间冲突（两个 kit 同 `name`）在注册时即抛错，不会静默覆盖。

## 与内置 kit 同名

`build()` 默认注册六个内置 kit，但**仅当注册表中尚无同名 kit 时**——消费方显式注册（`registerKit`/`registerScoped`/扫描）同名者优先，内置版本被跳过。可用它整体替换某个内置 kit 的实现（如换成走专属数据源的 `$gl.sql`）。

## 版本化固化

把自定义 kit 固化到某个引擎版本快照，供脚本按 `engineVersion` 路由（引擎版本语义见[脚本使用手册](/docs/script/usage)）。以基线版本为底声明差异，构建期派生注册：

```java
ScriptEngine engine = new ScriptEngineBuilder()
        .registerKit(new RuleKitV2())          // 先注册（进缺省版本）
        .deriveRevision("v2", "v1", deriver -> deriver
                .scoped("rule", RuleKitV2.class)   // v2 用新实现覆盖 rule 命名空间
                .remove("legacy")                  // 移除不再暴露的 kit
                .esVersion("2023"))                // 语言级别差异（可选）
        .build();
```

`RevisionDeriver` 差异声明项：`singleton(ns, kit)` 覆盖/新增无状态 kit、`scoped(ns, clazz[, factory])` 覆盖/新增 scoped kit、`remove(ns)` 移除、`esVersion(...)` 语言级别、`status(...)` 注册时初始状态（缺省 CURRENT）。派生出的 kit 同样经 interop 白名单 fail-fast 校验；`ns` 须与 `@GraalService.name` 一致。

## 验证

注册完成后三种自检方式：

- 脚本内 `$gl.help()` / `$gl.help('demo')` 打印可见 kit 与方法；
- `GET /api/script/catalog` 拉取结构化清单（开启 `geelato.script.rest.enabled`）；
- `KitDtsGenerator.generate(...)` 生成 `.d.ts` 声明文件，在 IDE 中获得补全提示。

第三方 Java 库无需逐个写 kit 类即可整库引入，见[第三方库引入](/docs/script/host-library)。
