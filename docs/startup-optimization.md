# 启动性能优化

目标：缩短应用启动时间，让 HTTP 服务尽早对外可服务；非请求关键路径的初始化允许延迟/异步加载，并保留可回退的配置开关。

## 一、启动链路分析结论

经源码核查，启动耗时主要集中在以下几处（均在启动主线程同步执行）：

| # | 热点 | 位置 | 问题 |
|---|---|---|---|
| 1 | `cn.geelato` 全包被反射扫描 **5 次** | `MetaConfiguration:52`、`OrmAutoConfiguration`（**重复扫 @Entity**）、`GraalManager` 两处、`ScheduledTaskMonitorRegistry` | `ClassScanner` 对包下**每个 .class 都 `Class.forName`**（加载并链接整个依赖树） |
| 2 | 5 条元数据全表查询**串行** + O(T×C) 内存过滤 | `DefaultMetaStore.load()`、`MetaManager.parseDBMeta` | platform_dev_column 等全量拉取后逐表 `stream().filter` |
| 3 | OCR native 模型同步加载 | `InvoiceOcrEngine` @PostConstruct | RapidOCR 加载 PP-OCRv4，数秒级 |
| 4 | 启动即同步 HTTP 健康探测 | `AuxiliarySuiteHealthPoller.init` | 5s 连接 / 10s 读超时，阻塞启动线程 |
| 5 | CommandLineRunner 4 步串行 | `BootApplication.run` | 数据源/SQL脚本/Graal扫描/系统配置互相独立却串行执行 |

## 二、已实施的优化

### P0 扫描去重与提速（无行为变化）

- **`geelato-utils/AnnotatedClassScanner`**：基于 ASM 字节码（Spring `MetadataReader`）按注解全名预过滤，**仅对命中类 `ClassUtils.forName`**，替代旧 `ClassScanner` 对每个 .class 都类加载的实现。支持一次扫描匹配多个注解。
- **`@Entity` 双扫描去重**：`MetaManager` 新增 `scannedPackages` 记录与 `isPackageAlreadyScanned()`；`OrmAutoConfiguration.scanAndParseEntities` 跳过已被 `MetaConfiguration` 覆盖的包，消除重复 classpath 遍历。
- **Graal 两趟扫描合并**：`GraalManager.initGraalContextScan` 单趟扫描同时收集 @GraalService 与 @GraalVariable。
- `MetaSourceLoader`（meta-sync）顺带换用新扫描器。

### P1 元数据 DB 加载提速

- `DefaultMetaStore.load()`：5 条全表查询改 `CompletableFuture` 并行。
- `MetaManager.parseDBMeta`：逐表 `stream().filter`（O(T×C)）改为 `groupingBy` 预索引（O(n)）。

### P2 启动主链路并行 + Graal 延迟化

- `BootApplication.run()`：`parseDataSourceMeta` / `resolveSqlScript` / `initEnvironment` 三步独立任务并行执行。
- `GraalManager` 懒初始化兜底：扫描预热默认放到后台线程（`graal-context-warmer`），运行期任意访问点（`getGraalServiceMap` 等）通过 `ensureInitialized()` 等待完成，**不会读到半初始化数据**；最坏情况首访问者承担等同旧版的耗时，仅时机转移。

### P3 非关键服务延迟加载

- `InvoiceOcrEngine`：启动期不再同步加载模型，改为就绪后异步预热 + 首次识别懒加载。
- `AuxiliarySuiteHealthPoller`：首次健康探测延迟异步（`scheduleWithFixedDelay` 带 initialDelay），不再阻塞启动。
- `ScheduledTaskMonitorRegistry`：全量 .class 扫描改到 `ApplicationReadyEvent` 后异步执行；扫描逻辑同步优化为先用字节码注解元数据过滤 @Component 候选再 `forName`。
- `ApiRestControllerSrvLogRegistry` / `ApiEndpointSnapshotWriter`：@PostConstruct → `ApplicationReadyEvent`，启动期不再遍历 handler 映射与写磁盘。
- `SecurityDataRefreshCoordinator` **保持同步**：`DefaultSecurityInterceptor`（每请求执行）依赖 org 快照，延迟有正确性风险，未改动。

## 三、配置开关（统一前缀 `geelato.startup.*`，默认均为新行为）

| 配置项 | 默认 | 说明 |
|---|---|---|
| `geelato.startup.graal-async-init` | `true` | Graal 上下文后台异步预热；设为 `false` 恢复启动期同步初始化 |
| `geelato.startup.schedule-monitor-async` | `true` | 定时任务监控扫描就绪后异步执行；设为 `false` 恢复 @PostConstruct 同步扫描 |
| `geelato.ocr.invoice.async-init` | `true` | OCR 模型异步预热 + 懒加载；设为 `false` 恢复启动期同步加载 |

> 全部扫描去重、DB 并行化、内存索引改造为纯性能优化，无语义变化，未设开关。

## 四、验证建议

1. 编译：`mvn -DskipTests compile`（已通过）。
2. 启动 `QuickStartApplication`，对比 Spring Boot `Started ... in X seconds` 及 `[start application]...start→finish` 日志时间戳。
3. 验证 OCR（首次发票识别触发懒加载或等异步预热完成）、定时任务监控端点（就绪数秒后可见数据）、辅助套件健康探测（启动数秒后开始）功能正常。
