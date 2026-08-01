# 启动日志与运行日志分离方案

## 目标
- **启动日志**：完整记录（DEBUG/INFO 全量），单独存文件、保留更久（30 天），便于排查启动失败与初始化问题
- **运行日志**：只记录关键信息（ERROR + WARN），独立文件、保留较短（7 天），降低噪音与磁盘占用
- 仅改造 `geelato-web-quickstart` 模块（范围最小、最安全），其它启动模块（runtime/designer/mcp）保持现状

## 核心机制
用 logback 的 **TurboFilter + ApplicationReadyEvent 监听器** 区分"启动期/运行期"两个阶段：

1. 一个静态标志位 `StartupPhaseManager.isStartupPhase()`，初始为 `true`（启动期）
2. 一个 `ApplicationListener<ApplicationReadyEvent>`：Spring 容器就绪（`ApplicationReadyEvent`，即 `CommandLineRunner` 执行完、HTTP 端口已监听之后）触发时，把标志翻为 `false` → 进入运行期
3. 一个 logback `TurboFilter`：根据标志位 + 日志级别做路由决策
   - **启动期**：所有日志 → `startupLogFile`（同时 console 正常输出，不影响开发体验）
   - **运行期**：WARN/ERROR → `runtimeLogFile`（运行错误日志），同时继续走原有按业务包分流的 appender；运行期的低级别 DEBUG/INFO 不再进运行错误文件（控制台按现有 `CONSOLE_LOG_LEVEL` 不变，避免开发期看不到信息）

> TurboFilter 在 logback 中是全局、在 logger 路由之前生效的过滤器，非常适合做"阶段切换"。

## 改动清单（共 3 个新文件 + 2 个改文件）

### 新增文件 1：`geelato-web-common/src/main/java/cn/geelato/logging/logback/StartupPhaseManager.java`
- 纯静态工具类，持有 `volatile boolean startupPhase = true`
- 提供 `isStartupPhase()` / `markRuntimeStarted()` 方法
- 放在已有的 `cn.geelato.logging` 包下（与 `LogContext` 同包），便于其它模块复用

### 新增文件 2：`geelato-web-common/src/main/java/cn/geelato/logging/logback/StartupRuntimeTurboFilter.java`
- 继承 `ch.qos.logback.classic.turbo.TurboFilter`
- `decide()` 逻辑：
  - 启动期 → 返回 `NEUTRAL`（不阻断，正常进 startupLogFile；通过 MDC 标记或 EvaluatorFilter 辅助路由）
  - 运行期 + level >= WARN → `NEUTRAL`（进 runtimeLogFile）
  - 运行期 + level < WARN → `NEUTRAL`（不进 runtimeLogFile，靠 appender 上的 ThresholdFilter=WARN 拦截）
- 实际"分流到哪个文件"由 appender 上的过滤器 + 一个自定义 `TurboFilter` 配合实现，**不改写 logger 路由**，保持与现有 schedule/auth/message 等分流逻辑兼容

> 说明：经评估，最简洁的实现是 —— TurboFilter 仅负责"运行期抑制低级别日志进入主运行文件"，而"启动日志单独成文件"用一个独立的 `startupLogFile` appender，通过一个轻量的 `onStartup` LoggerListener 或直接在 TurboFilter 里 attach MDC 标记实现。最终采用 **TurboFilter + appender ThresholdFilter** 的组合，零侵入业务代码。

### 新增文件 3：`geelato-web-quickstart/src/main/java/cn/geelato/web/quickstart/logging/RuntimePhaseListener.java`
- 实现 `ApplicationListener<ApplicationReadyEvent>`（Spring Boot 3.0.0 原生支持）
- `onApplicationEvent()` 中调用 `StartupPhaseManager.markRuntimeStarted()`
- 用 `@Component` 注册（quickstart 已是 `@SpringBootApplication(scanBasePackages={"cn.geelato"})`，会被扫描到）

### 改文件 1：`geelato-web-quickstart/src/main/resources/geelato-logback.xml`
新增/调整内容（保持现有 schedule/auth/message 等分流不动）：

1. **新增 appender `startupLogFile`**
   - 路径 `${LOG_DIR}/startup/%d{yyyy-MM-dd}.%i.log`
   - 单文件 100MB，**保留 30 天**，加 `totalSizeCap` 5GB
   - 无级别过滤（记录全量），但通过 TurboFilter 仅在启动期写入

2. **新增 appender `runtimeLogFile`**（运行错误日志）
   - 路径 `${LOG_DIR}/runtime/%d{yyyy-MM-dd}.%i.log`
   - 单文件 100MB，**保留 7 天**，`totalSizeCap` 2GB
   - `ThresholdFilter` level = `${RUNTIME_LOG_LEVEL:-WARN}`（运行期只记 WARN 及以上）

3. **注册 TurboFilter**（放在 `<root>` 之前）
   ```xml
   <turboFilter class="cn.geelato.logging.logback.StartupRuntimeTurboFilter"/>
   ```

4. **调整 root**：把 `infoLogFile` 替换语义，root 同时引用 `console` + `startupLogFile` + `runtimeLogFile`（启动期靠 TurboFilter 让 startupLogFile 生效，运行期靠 ThresholdFilter 让 runtimeLogFile 只收 WARN+）

5. **清理死代码**：删除第 61-62 行注释掉的 `SystemLoggingAppender` appender 和第 180-182 行的注释 logger（该类从未实现）

### 改文件 2：`geelato-web-quickstart/src/main/resources/application.properties`
- 保留现有 `logging.config` / `logging.level.*` 不变
- 新增注释说明可用的环境变量覆盖项：`RUNTIME_LOG_LEVEL`（默认 WARN）

## 行为效果
| 阶段 | startup.log | runtime.log | console | 业务分流文件(schedule/auth/...) |
|---|---|---|---|---|
| 启动期（→ApplicationReadyEvent） | ✅ 全量 DEBUG+ | 不写入 | ✅ 按 CONSOLE_LOG_LEVEL | 正常（启动期也有少量） |
| 运行期（ApplicationReadyEvent→） | 不写入 | ✅ 仅 WARN+ | ✅ 不变 | 正常（WARN+ 才进 runtime，业务包分流不受影响） |

启动失败（容器未到 Ready）→ 全部进 startup.log，保留 30 天，排查无忧。
正常运行期 → runtime.log 只有关键错误，体积极小。

## 兼容性与风险
- ✅ 不改任何业务 Java 代码，零侵入
- ✅ 不影响现有按包名分流的 schedule/auth/message/interceptor/request/resolve 逻辑
- ✅ 控制台行为不变，开发期体验不受影响
- ✅ ES 配置（`geelato-logback-es.xml`）不在本次改造范围，保持原样
- ✅ 所有级别/路径可通过环境变量覆盖，与项目现有"环境变量占位符"风格一致
- ⚠️ 需注意 `TurboFilter` 在 logback 初始化早期生效，`StartupPhaseManager` 作为静态类不依赖 Spring，初始化时序安全

## 验证方式
1. 启动 quickstart 应用，观察 `${LOG_DIR}/startup/` 生成全量启动日志，`runtime/` 目录此时为空或仅有启动期 WARN
2. 等待控制台出现 Spring Boot 启动完成（ApplicationReadyEvent 触发）后，发起业务请求触发一个 WARN/ERROR，确认写入 `runtime/` 而非 `startup/`
3. 模拟启动失败（如改错端口/依赖），确认 startup.log 完整记录失败堆栈