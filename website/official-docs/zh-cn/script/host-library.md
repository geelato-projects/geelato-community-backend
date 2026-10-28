---
title: 第三方库引入
sidebar_label: 第三方库引入
---

# 第三方库引入

脚本不能 `import` 任意 Java 类；要把第三方库能力提供给脚本，由**宿主声明整库引入**：SDK 按包扫描其中的工具类，把 interop 兼容的公共方法挂载为 `$kit` 顶层对象。库本身以普通 Maven 依赖进入宿主 classpath，脚本侧零声明。

## 脚本侧的类访问限制

沙箱限制了脚本内的宿主类查找：`Java.type(...)` 仅对一份固定白名单放行——三个 kit 注解类与 `HashMap`/`ArrayList`/`LinkedHashMap`/`String`/`Integer`/`Long`/`Double`/`Boolean`/`BigDecimal`/`Date` 等少数 JDK 类。其余类（如 `java.lang.System`）查找即被拒绝，`Class`/`ClassLoader`/`System`/`Runtime`/`Thread` 的访问被显式 deny。

因此引入第三方能力的**唯一受控通道**是宿主侧的整库注入（下述两种声明方式），没有脚本内依赖声明 DSL。

## 声明方式（二选一，可并用）

### 编程声明

```java
ScriptEngine engine = new ScriptEngineBuilder()
        .hostPackage("hutool", "cn.hutool.core.util")     // 库别名 = 根包
        .hostExcludes(List.of("RuntimeUtil"))              // 按类简单名或全限定名剔除（可选）
        .build();
```

### 声明式：classpath 约定文件

在任意 jar（含宿主自身）内放置 `META-INF/gl/script-kits.properties`，自动装配时解析：

```properties
# 别名 = 根包
hutool=cn.hutool.core.util
codec=org.example.commons.codec

# 特殊键：逗号分隔的剔除清单（类简单名或全限定名）
exclude=RuntimeUtil,ObjUtil
```

多份文件（多个 jar 各自带一份）自动合并；同一别名映射到不同包时启动报错，须为其中一个改用其他别名。

## 挂载形态与调用

整库注入统一挂载为顶层 `$kit` 对象，三层结构：`$kit.{库别名}.{类简单名小写}.{方法}`。

```javascript
// hostPackage("hutool", "cn.hutool.core.util") 之后
const uuid = $kit.hutool.idutil.fastSimpleUUID();
const masked = $kit.hutool.strutil.hide($ctx.phone, 3, 7);
return { uuid, masked };
```

## 暴露规则

对根包（含子包）下扫到的每个**具体类**（接口、抽象类、枚举、注解均跳过）：

- **静态方法**：签名 interop 兼容即直接暴露；
- **实例方法**：类有 public 无参构造器时实例化后暴露（无参构造缺失则跳过该类实例方法）；
- **interop 兼容口径**与自定义 kit 相同：参数/返回类型限于 String、数值、Boolean、Map、List、Date、Object、Value 与原始类型；不兼容的方法静默跳过（启动日志 warn 提示跳过数量）。

## 安全约束

- **危险类硬拒绝**：扫到 `System`/`Runtime`/`Class`/`ClassLoader`/`Thread` 直接抛错，而非跳过——声明过宽（根包覆盖到 `java.lang` 等）会立即暴露问题；
- **别名下类名冲突 fail-fast**：同别名内两个类简单名相同（decapitalize 后撞名）启动报错，提示拆分为多个别名子包；
- **空库 fail-fast**：别名对应包没扫到任何可注入类（包名不在 classpath、全被 exclude、无兼容方法）启动报错；
- 暴露面即攻击面：`$kit` 方法不经 `@GraalFunction` 注解收口，**库内所有兼容签名的方法都会暴露**。声明根包时收敛到工具子包（如 `cn.hutool.core.util` 而非 `cn.hutool.core`），并善用 `exclude` 剔除不该进脚本的类；HTTP/IO 类库谨慎引入。

## IDE 提示

`KitDtsGenerator.generate(...)` 同样为 `$kit` 生成三层嵌套的 `.d.ts` 声明，规则作者放进工程即可获得 `$kit.hutool.strutil.*` 的补全。
