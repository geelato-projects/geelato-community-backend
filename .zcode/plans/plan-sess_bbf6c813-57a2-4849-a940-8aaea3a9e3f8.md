# 启动性能优化实施计划

## 分析结论（已验证源码）
启动慢的根因：`cn.geelato` 全包被反射扫描 **5 次**（其中 @Entity 被 MetaConfiguration 和 OrmAutoConfiguration **重复扫** 2 次）；`ClassScanner` 对包下每个 .class 都 `Class.forName`（加载+链接整个依赖树）；5 条元数据 SQL 串行 + O(T×C) 内存过滤；OCR native 模型、HTTP 健康探测等非关键服务在启动线程同步执行；`BootApplication.run()` 4 步独立任务串行。

## 改动内容

### P0 扫描去重与提速（收益最大，无行为变化）
1. **新增 ASM 注解预过滤扫描器** `geelato-utils/.../AnnotatedClassScanner.java`（utils 已依赖 spring-core）：
   - 用 `PathMatchingResourcePatternResolver` + `CachingMetadataReaderFactory` 读字节码判断注解（`isAnnotated(注解全名)`），**只对命中类 `ClassUtils.forName`**，替代"每类都加载"的旧路径
   - API：`scan(String basePackage, Class<? extends Annotation>... annotations)` 支持多注解单趟扫描
2. **@Entity 双扫描去重**：
   - `MetaManager.scanAndParse(String)` 改用新扫描器，并记录已扫包（`Set<String> scannedPackages` + `hasScannedPackage()`）
   - `OrmAutoConfiguration.scanAndParseEntities`（geelato-orm:87）：跳过 MetaManager 已扫过的包，消除重复 classpath 遍历
3. **GraalManager 两趟扫描合并为一趟**：新增 `initGraalContext(packageName)` 用多注解扫描一次收集 @GraalService/@GraalVariable；`BootApplication.resolveGraalContext` 改调它（保留旧方法兼容 meta-sync 等处）
4. `MetaSourceLoader`（geelato-meta-sync:127）顺带换用新扫描器

### P1 元数据 DB 加载提速
5. `DefaultMetaStore.load()`：5 条全表查询改 `CompletableFuture` 并行（专用守护小线程池，Hikari 默认池 10 连接足够）
6. `MetaManager.parseDBMeta`（geelato-core:133-159）：逐表 `stream().filter` 改为 `groupingBy` 预建 4 个索引 Map，O(T×C)→O(n)

### P2 启动主链路并行 + Graal 延迟化
7. `BootApplication.run()`：`parseDataSourceMeta`/`resolveSqlScript`/`initEnvironment` 三步独立任务 `CompletableFuture` 并行 + join（异常聚合保持"启动失败即退出"语义）
8. `GraalManager` 懒初始化兜底：内部 `CompletableFuture<Void> initFuture`，`resolveGraalContext` 改为后台守护线程预热；`getGraalServiceMap()/getGraalVariableMap()/getGraalServiceDescriptions()` 等访问点先 `initFuture.join()`（未启动则当场启动）——保证 ScriptExecutionService、GraalServiceController、IdeContextService 任何首次访问都拿到完整数据，最坏情况等同旧耗时，只是时机转移
   - 开关 `geelato.startup.graal-async-init`（默认 true，false 恢复同步初始化）

### P3 非关键服务延迟加载（均带开关，默认新行为，统一前缀 `geelato.startup.*`）
9. `InvoiceOcrEngine`：移除 @PostConstruct 同步 `RapidOCR.create()` → `recognize()` 内懒初始化（方法已 synchronized，天然 DCL）+ ApplicationReadyEvent 后异步预热；`healthCheck()` 未就绪返回 false 的语义保留
10. `AuxiliarySuiteHealthPoller.init()`：删除启动即同步 `refreshNow()`（5s 连接/10s 读超时 HTTP），首次探测改为 `scheduleWithFixedDelay(task, initialDelay≈5s, interval)` 异步执行
11. `ScheduledTaskMonitorRegistry`：@PostConstruct 全量 .class 扫描 → ApplicationReadyEvent 后守护线程执行；扫描逻辑同步优化为先用 MetadataReader 注解元数据过滤 @Component 候选再 forName
12. `ApiRestControllerSrvLogRegistry` / `ApiEndpointSnapshotWriter`：@PostConstruct → ApplicationReadyEvent 异步（仅 srvlog 快照晚数秒生成）
13. `SecurityDataRefreshCoordinator` **保持同步**：`DefaultSecurityInterceptor`（每请求执行）依赖 org 快照，延迟有正确性风险——在文档中说明

### P4 文档
14. 新增 `docs/startup-optimization.md`：启动链路分析、各项开关与回退方式、预期收益

## 涉及文件
- geelato-utils：新增 `AnnotatedClassScanner.java`
- geelato-core：`MetaManager.java`、`GraalManager.java`
- geelato-orm：`OrmAutoConfiguration.java`
- geelato-meta-sync：`MetaSourceLoader.java`
- geelato-web-platform：`BootApplication.java`、`DefaultMetaStore.java`、`InvoiceOcrEngine.java`、`AuxiliarySuiteHealthPoller.java`、`ScheduledTaskMonitorRegistry.java`、`ApiRestControllerSrvLogRegistry.java`、`ApiEndpointSnapshotWriter.java`
- docs：`docs/startup-optimization.md`

## 验证
1. `mvn -q compile` 多模块编译通过
2. 运行 `geelato-mql-test` 现有测试（覆盖 MetaManager 改动）
3. 如本地 DB/Redis 可用：启动 `QuickStartApplication`，对比 Spring Boot "Started in X seconds" 及 `[start application]...start→finish` 日志时间戳；验证 OCR/健康探测/定时任务监控在就绪后正常工作

## 风险与回退
- 所有初始化时机变化项均有独立 `geelato.startup.*` 开关，可逐项回退旧行为
- Graal 懒加载有 Future 兜底锁，不会出现"半初始化"数据
- 并行查询瞬时占 5 个连接，Hikari 默认池（10）足够