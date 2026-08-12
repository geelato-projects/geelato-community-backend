# 在线文档更新（方法名不改，保留 disableInjectFilter）

探索结果：在线文档是 Docusaurus 站点，ZH 权威源在 `website/official-docs/zh-cn/`，EN 镜像在 `website/i18n/en/docusaurus-plugin-content-docs/current/`。当前该特性（`MetaQuery.disableInjectFilter()` + `FluentQueryFilterInjector.isForceInject()`）在文档中**完全未提及**。

## 改动文件（ZH 2 个 + EN 镜像 2 个）

### 1) `website/official-docs/zh-cn/reference/spi-query-filter-and-save-fill-extension.md`
- **接口签名块**（`FluentQueryFilterInjector`）：补上 `default boolean isForceInject()` 方法及其 Javadoc。
- **统一运行时规则**后新增小节「单次查询跳过注入（仅 Fluent DSL）」：给出 `disableInjectFilter()` 用法、判定规则表（未调用/调用且未强制/调用但强制三态）、`isForceInject()` 说明、安全提示。
- **一步一步排障**：加一条——查询"少了"过滤时，确认是否调用了 `disableInjectFilter()` 或注入器被 `isEnabled()` 关闭。

### 2) `website/official-docs/zh-cn/orm/fluent-dsl.md`
- **高级能力**区（动态数据源示例之后）新增「跳过注入过滤」小节：代码示例 + 一句说明 + 交叉链接到 SPI 文档。

### 3) EN 镜像：上述两文件的 `website/i18n/en/.../current/` 对应版本
- 同步加入 `isForceInject()` 到接口块、运行时规则小节、排障条目、Fluent DSL 高级能力小节（英文）。

## 关键新增内容预览（ZH）

接口块：
```java
public interface FluentQueryFilterInjector {
    boolean isEnabled();
    void inject(QueryCommand command, MetaQuery query);

    /** 是否强制注入；默认 false。返回 true 时即使本次查询调用了 disableInjectFilter() 也照常注入。 */
    default boolean isForceInject() {
        return false;
    }
}
```

运行时规则新增小节判定表：
- 未调用 `disableInjectFilter()` → 正常注入
- 调用且注入器 `isForceInject()=false` → **跳过**
- 调用但注入器 `isForceInject()=true` → **照常注入**（强制覆盖）

Fluent DSL 用法：
```java
MetaFactory.query("platform_user")
        .disableInjectFilter()
        .list();
```

## 不在范围
- 不改方法名（保持 `disableInjectFilter`）。
- 不动代码（已完成并通过编译）。
- 低优先级的 `docs/orm/*` 内部设计文档与 `geelato-orm/README.md` 本次不改（它们不被站点渲染）。