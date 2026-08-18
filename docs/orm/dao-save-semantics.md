# Dao 实体保存语义：save / insert / update

## 背景

`dao.save(entity)` 原先只有一条判定规则：实体 id 非空即构造 UPDATE，id 为空即构造 INSERT（并自动生成主键）。这在"load → 修改 → save"的开发惯例下很方便，但作为框架能力有局限——**无法插入一条指定主键的数据**（手工 setId 后调 save 会生成一条影响 0 行的 UPDATE）。

为此在 `Dao` 上新增了两个显式方法，`save` 的默认语义保持不变：

| 方法 | 语句类型 | id 非空 | id 为空 |
| --- | --- | --- | --- |
| `save(entity)` | 自动判定 | UPDATE（`where id = ?`） | INSERT，自动生成主键 |
| `insert(entity)` | 始终 INSERT | INSERT，保留指定 id | INSERT，自动生成主键 |
| `update(entity)` | 始终 UPDATE | UPDATE（`where id = ?`） | 抛 `IllegalArgumentException` |

`update` 对空 id 采取 fail-fast 而非静默回退为插入：显式 API 应有显式契约，避免掩盖调用方缺陷。

## 用法

```java
// 指定主键插入（save 做不到的场景）
entity.setId("custom-pk-001");
dao.insert(entity);

// 显式更新，id 为空直接抛异常
entity.setId("custom-pk-001");
entity.setName("new-name");
dao.update(entity);
```

## 判定链路

```
Dao.save / insert / update
  └─ EntityManager.generateSaveSql(entity, sessionCtx, forcedType)
       └─ EntitySaveParser.parse(entity, sessionCtx, forcedType)   // 判定发生地
            └─ 按 commandType 路由 MetaInsertSqlProvider / MetaUpdateSqlProvider
```

- `Dao.save(entity)` 即 `doSave(entity, null)`，三者的执行、事件（统一由 `OrmEventOperations` 模板编排）、字段填充 SPI 逻辑完全一致，仅 `forcedType` 不同。
- `forcedType == null`：按 id 是否非空自动判定（原 save 行为）。
- `forcedType == Insert`：始终 INSERT；仅在 id 为空时生成主键。
- `forcedType == Update`：始终 UPDATE；id 为空时抛 `IllegalArgumentException`。
- 字段填充 SPI（`EntitySaveFieldValueFiller`）按实际命令类型触发，显式 insert 会走 Insert 型填充（如 createAt/creator）。

## 与其他保存链路的关系

- MQL Save（`JsonTextSaveParser`）与 Fluent DSL（`MetaFactory.insert(...)/update(...)`）本就是显式指定语句类型的，不受本次改动影响。
- `dao.save(BoundSql)` 接收的是已构造完成的命令，同样不受影响。

## 平台层约定

`BaseService` 已迁移到显式语义：`createModel` → `dao.insert`，`updateModel` / `isDeleteModel` → `dao.update`。新代码建议：

- 语义明确的场景优先使用 `insert` / `update`；
- `save` 保留给"不确定是插入还是更新"的兼容场景与存量 load-modify-save 惯例。
