# 平台 OpenMetrics 监控能力建设（业界高标准、零配置）

## 已确认的决策
- **目标模块**：`geelato-web-platform`
- **指标范围**：HTTP 服务接口、JVM、数据库连接池（HikariCP）；不含插件指标
- **健康端点**：聚合 `HealthEndpoint` SPI 到 Actuator `/actuator/health`

## 两条新要求
1. **配置极简**：引入依赖即生效，用户侧零配置（通过 `EnvironmentPostProcessor` 内置默认值，遵循 `geelato-orm` 已有的同款机制）。
2. **业界高标准**：遵循 SRE 黄金信号（Latency/Traffic/Errors/Saturation，即 RED + USE）、K8s 健康语义（liveness/readiness 语义分离）、开箱即用的 SLO 与告警规则、标准 Grafana 面板。

## 现状关键事实（已核实）
- `geelato-web-platform/pom.xml` 无 actuator/micrometer；Spring Boot 3.0.0（`spring-boot-dependencies` 托管 micrometer 版本）。
- `geelato-orm` 已用 `spring.factories` + `EnvironmentPostProcessor`（`AtomikosEnvironmentPostProcessor`）注入默认值 → **平台同款机制有先例**。
- primary 数据源 = **HikariCP**（`DataSourceBuilder` 默认，未设 type）；动态数据源 = HikariCP（`DataSourceFactory` 写死）→ micrometer 自动采集 `hikaricp.*`。
- HTTP API 指标（`http.server.requests`）、JVM 指标（`jvm.*`）由 actuator/micrometer 自动产生。
- 平台有 `HealthEndpoint` SPI（geelato-lang），实现：`PlatformHealthEndPoint`、`MetaConflictHealthEndpoint`。
- `SecurityInterceptorProperties.initDefaultExcludes()`（在 geelato-web-common）不含 `/actuator/**`，需放行；Shiro 已 `/**→anon`。
- `spring-configuration-metadata.json` 已存在于 platform，可补充新配置项的元数据（IDE 提示）。

---

## 改动清单

### 1. `geelato-web-platform/pom.xml` —— 新增依赖（2 个）
```xml
<dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-actuator</artifactId></dependency>
<dependency><groupId>io.micrometer</groupId><artifactId>micrometer-registry-prometheus</artifactId></dependency>
```

### 2. 零配置默认值注入 —— `PlatformMetricsEnvironmentPostProcessor`
- 包：`cn.geelato.web.platform.run.monitor.metrics`
- 实现 `EnvironmentPostProcessor`，在 `spring.factories` 注册（`org.springframework.boot.env.EnvironmentPostProcessor=`）。
- **仅在用户未显式配置时注入默认值**（`containsProperty` 判断，绝不覆盖用户已设）。注入的默认值（即"开箱即用"的标准配比）：
  - `management.endpoints.web.exposure.include=health,ready,live,info,prometheus,metrics`（Spring Boot 3 推荐用 `/actuator/health/live|ready` 子路径，K8s 友好）
  - `management.endpoint.health.probes.enabled=true`
  - `management.endpoint.health.show-details=always`
  - `management.endpoint.health.group.liveness.include=livenessState,platformHealth`
  - `management.endpoint.health.group.readiness.include=readinessState,platformReadiness`
  - `management.endpoint.health.status.order=down,out-of-service,degraded,unknown,up`
  - `management.endpoint.health.status.http-mapping.degraded=200`
  - `management.prometheus.metrics.export.enabled=true`
  - `management.metrics.web.server.request.autotime.enabled=true`、`percentiles-histogram=true`（开直方图，支持 PromQL p95/p99）
  - `management.metrics.distribution.slo.http.server.requests=50ms,100ms,250ms,500ms,1s,5s`（**黄金 SLO bucket**）
  - `server.compression.enabled=true`、`mime-types` 追加 `application/openmetrics-text`（若用户已配 compression 仅追加 mime，不覆盖）
- **用户侧零配置**：依赖一加，`/actuator/prometheus`、`/actuator/health/live|ready` 立即可用，全部参数走合理默认。

### 3. 指标与标签治理 —— `PlatformMeterCustomizer`
- 实现 `MeterRegistryCustomizer<MeterRegistry>`，包同上。
- 公共标签 `application`（取 `spring.application.name`）+ `module=geelato-web-platform`。
- **高基数防护 MeterFilter**（业界标准做法）：
  - deny 含 `exception` 全限定类名的高基数序列（仅保留简单类名）。
  - deny `http.server.requests` 中 `uri=/UNKNOWN` 或 `uri=/**`（未识别模板，防止基数爆炸）。
  - `maximumAllowableTags` 对 `http.server.requests` 的 `uri` 限制上限（如 200），超出后折叠为 `none`，防 API 路径失控。
- 这是 message 里 `MessageMeterCustomizer` 同款思路的平台版。

### 4. 健康聚合层 —— `cn.geelato.web.platform.run.monitor.health`
- **`PlatformHealthIndicator`**（bean `platformHealth`，implements `HealthIndicator`）：注入 `List<HealthEndpoint>`，聚合状态映射 `HEALTH/ABNORMAL/UNKNOWN → UP/DEGRADED/UNKNOWN`，任一 ABNORMAL → DEGRADED；details 列每模块。纳入 **liveness**。
- **`PlatformReadinessIndicator`**（bean `platformReadiness`）：基于「至少 1 个 HealthEndpoint 且无 ABNORMAL」给 readiness；细节含是否已扫到元信息/数据源就绪。纳入 **readiness**。
- 语义对齐 K8s：liveness=服务进程健康（不重启我），readiness=业务就绪（可接流量）。

### 5. 装配 —— `PlatformMetricsAutoConfiguration`
- 包 `cn.geelato.web.platform.run.monitor.metrics`，`@Configuration` + `@ConditionalOnClass(MeterRegistry.class)` + `@ConditionalOnProperty("geelato.platform.monitoring.enabled", matchIfMissing=true)` + `@ConditionalOnMissingBean`。
- 不写 spring.factories 的 `EnableAutoConfiguration`（平台靠 `@ComponentScan("cn.geelato")` 接管，与 message 不同；仅 EnvironmentPostProcessor 需走 spring.factories）。

### 6. 放行 `/actuator/**` —— 改 `geelato-web-common`
- `SecurityInterceptorProperties.initDefaultExcludes()` 追加 `/actuator/**`（与 `/monitor/**` 同组「监控页面」）。
- 唯一跨模块改动，必要性：否则 `DefaultSecurityInterceptor` 拦截 Prometheus 抓取。

### 7. 用户侧配置（**仅 1 个开关，默认开**）
- quickstart 的 `monitor.properties` **不新增任何 management.* 配置**（全由 EnvironmentPostProcessor 注入默认）。
- 仅保留一个总开关（已在 AutoConfiguration 默认开，用户可关）：
  ```properties
  geelato.platform.monitoring.enabled=true
  ```
- 需要定制时（如生产收窄暴露面）才在用户配置写覆盖项，默认零配置。
- 补充 `geelato-web-platform/src/main/resources/META-INF/spring-configuration-metadata.json` 的元数据描述，IDE 有提示。

### 8. 部署资产 —— `geelato-web-platform/deploy/observability/`（业界标准交付物）
- **`prometheus.yml`** —— scrape job `geelato-platform`，`/actuator/prometheus`，含 K8s/Consul SD 注释。
- **`alerts.yml`** —— 开箱即用告警规则（SRE 黄金信号）：
  - `PlatformApiHighErrorRate`：5xx 占比 > 5%（5m）
  - `PlatformApiHighLatencyP95`：p95 > 1s（5m）
  - `PlatformDbPoolSaturation`：`hikaricp_connections_active / hikaricp_connections_max` > 0.8（5m）
  - `PlatformJvmHighHeap`：堆使用 > 85%（10m）
  - `PlatformInstanceDown`：scrape 失败 1m
  - `PlatformHealthDegraded`：`geelato_platform_health_status` 非 UP
- **`grafana-dashboard.json`** —— 标准面板（RED：请求速率/错误率/p95；USE：连接池饱和/JVM 堆/GC/线程；实例在线/健康状态）。
- **`README.md`** —— 黄金信号框架说明、零配置默认值清单、指标字典、告警阈值依据、生产收窄建议（`exposure.include=health,prometheus` + 反代鉴权）、Druid 场景说明。

### 9. 测试
- `PlatformHealthIndicatorTest`（聚合 status 映射：全 HEALTH→UP、含 ABNORMAL→DEGRADED、空→UP）。
- `PlatformMetricsEnvironmentPostProcessorTest`（验证默认值仅在缺失时注入、不覆盖用户已设）。

---

## 验证方式
1. `mvn -pl geelato-web-common,geelato-web-platform -am clean install`。
2. 启动 quickstart（**不改任何配置**）。
3. `curl -s localhost:8080/actuator/prometheus` → 见 `http_server_requests_*`（含 SLO bucket）、`hikaricp_connections_*`、`jvm_*`、`geelato_platform_health_status`。
4. `curl -s localhost:8080/actuator/health/live` 与 `/ready` → K8s 探针语义独立返回。
5. Prometheus 加载 `alerts.yml` + `prometheus.yml`，确认 target UP、告警规则加载。

## 业界高标准对照
| 维度 | 实现 |
|---|---|
| 黄金信号 | RED（http_server_requests 速率/错误/延迟）+ USE（hikaricp 饱和/利用率、jvm） |
| SLO | http.server.requests 配直方图 + 标准 SLO bucket（50ms…5s）→ p95/p99 可算 |
| 健康语义 | K8s liveness/readiness 分组、degraded 状态映射 HTTP 200 |
| 基数治理 | MeterFilter deny/折叠 + maximumAllowableTags |
| 零配置 | EnvironmentPostProcessor 注入合理默认，用户仅 1 总开关 |
| 可观测交付物 | scrape + alerts + dashboard 三件套，开箱即用 |

## 不做的事
- 不采集插件指标；不手写 HTTP/JVM/HikariCP 指标桥接（全 actuator 自带）。
- 不实现 Druid 池桥接（仅 workflow 可选场景，README 说明）。
- 不改 Shiro 链（已 anon）；不触碰企业 `geelato-actuator` 模块。

## 风险
- **EnvironmentPostProcessor 执行时机**：须在 `ApplicationEnvironmentPreparedEvent`，通过 spring.factories 注册即可，与 geelato-orm 同机制，无风险。
- **`/actuator/**` 暴露面**：默认值含 info/metrics 便于排查，README 标注生产应收窄为 `health,prometheus` + 反代鉴权。
- **Spring Boot 3.0.0 兼容**：micrometer/prometheus 版本由 spring-boot-dependencies 托管，3.0.0 支持全部所用配置项。
