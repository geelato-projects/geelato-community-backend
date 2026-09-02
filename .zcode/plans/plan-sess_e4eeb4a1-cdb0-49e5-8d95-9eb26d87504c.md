# SqlExecuteException 错误码细分方案

## 现状与问题

当前所有 SQL 异常（语法错误、连接中断、死锁、唯一键冲突、外键约束…）统一包装为 `SqlExecuteException`（ERROR_CODE=10002）一个码，前端/运维/`platform_exception_log` 落库都无法按类别区分。而分类判定逻辑其实已经存在（`getUserMessage()` 里的连接/锁/重复/约束分支），只是没有体现在错误码上。

代码库已有成熟的"根码 + 子类"模式（FileException 30000 + 30013-30018 子类），照此拆分。已确认无任何代码依赖 10002 码值本身（只引用类），子类化对 `catch (SqlExecuteException)` 完全兼容。

## 改动内容

### 1. 新建 4 个子类（`geelato-core/cn/geelato/core/orm/`）

继承 `SqlExecuteException`，每个覆写 `getUserMessage()` 返回固定文案，继承根类的 docSlug（`sql-execute` 详情页）与默认 HTTP 500：

| 类 | 码 | 用户文案 | 判定（复用现有逻辑） |
|---|---|---|---|
| `SqlConnectionException` | 10021 | 数据库连接中断，系统正在自动恢复，请稍后重试 | `CannotGetJdbcConnectionException` / sqlState `08xxx` / `Communications link failure` 等消息 |
| `SqlLockConflictException` | 10022 | 当前数据正被其他操作占用，请稍后重试 | MySQL 1213/1205、sqlState `40001`、PG `40P01` |
| `SqlDuplicateKeyException` | 10023 | 数据已存在，无法重复提交 | MySQL 1062、PG `23505` |
| `SqlConstraintViolationException` | 10024 | 数据存在关联引用或不符合约束，请检查后重试 | MySQL 1451/1452、sqlState `23xxx` |

码值取 1002x 子段：10xxx 数据段 10001-10007 已占用，子段避开现有码，结构对齐文件段（根码 + 子码）。10002 保留为根码，兜底未分类的 SQL 错误（如语法错误），文案仍为"数据操作失败，请稍后重试"。

### 2. `SqlExecuteException` 增加分类工厂

- 新增 `public static SqlExecuteException of(DataAccessException dae, String sql, Object[] params)`：把现有 `getUserMessage()` 里的判定逻辑移入，按类别实例化对应子类，未命中返回根类自身
- 根类 `getUserMessage()` 简化为返回固定文案；`isConnectionFailure()` 等判定 helper 保留供 `of()` 使用
- 抛出点 `errorMsg`（技术详情多行文本）行为不变

### 3. 抛出点切换（仅 2 个文件）

`Dao.java`、`BaseDao.java` 中全部 `new SqlExecuteException(...)`（约 10 处）改为 `SqlExecuteException.of(...)`。

### 4. 文档同步

- `error-codes.md` 表格：10002 描述改为"根码/未分类"，新增 10021-10024 四行
- `sql-execute.md` 详情页：补充子类码值与判定依据

### 5. 测试

改写 `SqlExecuteExceptionTest`：断言 `of()` 对各根因（连接/死锁/锁等待/唯一键/PG 唯一键/外键/语法错误）返回正确的子类类型、码值与 `getUserMessage()` 文案；保留技术详情只进 errorMsg 不进用户文案的断言。

### 6. 验证

`mvn -pl geelato-core -am test`（全量回归）+ 编译受影响下游模块。

## 兼容性

- 10002 码值与"未分类 SQL 异常"语义保留；前端拿到的 msg 文案不变（分类文案与现状一致）
- 所有 `catch (SqlExecuteException)` 代码不受影响（新增的是子类）
- `platform_exception_log.exceptionClass/exceptionCode` 从此按类别区分，运维检索更精确
- HTTP 状态保持 500（不做 409/503 细分，避免影响前端对状态码的处理）