# geelato 可观测性统一接入说明

本目录是 geelato 的**最终统一监控入口**。一份 `prometheus.yml` 同时抓取：
- **平台服务** `geelato-platform`：`manage.geelato.cn`（HTTPS）
- **消息中心** `geelato-message`：`message.ocean-bridges.com`（HTTP）

`alerts.yml` 合并了两者的告警规则（平台 SRE 黄金信号 + 消息中心业务健康）。

平台（`geelato-web-platform`）内置 Spring Boot Actuator + Micrometer + Prometheus，
引入依赖即对外暴露 OpenMetrics 指标（`/actuator/prometheus`）与健康探针（`/actuator/health/live|ready`），
**无需任何额外配置**。

## 1. 零配置默认值

所有 actuator/micrometer 参数由 `PlatformMetricsEnvironmentPostProcessor` 在启动早期注入合理默认（仅在用户未显式配置时生效，绝不覆盖）。引入依赖后立即可用：

| 能力 | 默认行为 |
|---|---|
| `/actuator/prometheus` | 已暴露，输出 OpenMetrics 文本 |
| `/actuator/health/live`、`/ready` | K8s liveness/readiness 探针子路径，独立返回 |
| liveness 分组 | 包含 `livenessState, platformHealth` |
| readiness 分组 | 包含 `readinessState, platformReadiness` |
| HTTP 请求指标 | `http.server.requests` 自动开启直方图 + SLO bucket（50ms/100ms/250ms/500ms/1s/5s），可算 p95/p99 |
| gzip 压缩 | 启用，mime 默认含 `application/openmetrics-text`，抓取流量降 5 倍（用户自定义 mime 列表时需自行包含该项） |
| DEGRADED 状态 | 映射为 HTTP 200，避免 K8s 因非致命降级触发重启 |

如需定制（如生产收窄暴露面），在 `application.properties` 显式写覆盖项即可：
```properties
management.endpoints.web.exposure.include=health,prometheus
```

## 2. 端点验证

应用启动后（默认 `localhost:8080`）：

```bash
# 指标端点
curl -s localhost:8080/actuator/prometheus | head
# 序列数量
curl -s localhost:8080/actuator/prometheus | wc -l

# 健康探针（K8s 用）
curl -s localhost:8080/actuator/health/live
curl -s localhost:8080/actuator/health/ready
# 完整健康（含各模块详情）
curl -s localhost:8080/actuator/health
```

抽查关键指标：
```bash
curl -s localhost:8080/actuator/prometheus | grep -E "http_server_requests|hikaricp_connections|jvm_memory_used|process_cpu"
```

## 3. 指标字典（平台核心）

遵循 SRE **黄金信号**（Latency / Traffic / Errors / Saturation）与 **USE 方法**（Utilization/Saturation/Errors）。

### RED（HTTP 服务接口）
| 指标 | 含义 |
|---|---|
| `http_server_requests_seconds_count` | 请求计数（按 method/uri/status/outcome 分组）→ Rate |
| `http_server_requests_seconds_bucket` | 耗时分布（SLO bucket）→ p95/p99/SLO 计算 |
| 标签 `status=5xx` | 服务端错误 → Errors |

### USE（数据库连接池 HikariCP）
| 指标 | 含义 |
|---|---|
| `hikaricp_connections_active` | 活跃连接数 |
| `hikaricp_connections_idle` | 空闲连接数 |
| `hikaricp_connections_pending` | 等待获取连接的请求数（>0 即饱和） |
| `hikaricp_connections_max` | 池上限 |
| 饱和度 | `active / max`，>80% 告警 |

### JVM / 进程
| 指标 | 含义 |
|---|---|
| `jvm_memory_used_bytes{area="heap"}` | 堆内存使用 |
| `jvm_memory_max_bytes{area="heap"}` | 堆内存上限 |
| `jvm_gc_live_data_size_bytes` | 老年代大小 |
| `jvm_gc_pause_seconds_sum` | GC 累计耗时 |
| `process_cpu_usage` | 进程 CPU 使用率 |
| `jvm_threads_live_threads` | 存活线程数 |

### 平台健康
| 指标 | 含义 |
|---|---|
| `health{health="platformHealth"}` | platformHealth 聚合状态码（1=UP，非 1=降级/异常） |
| `health{health="platformReadiness"}` | platformReadiness 就绪状态码 |

公共标签：`application`（= spring.application.name）、`module=geelato-web-platform`，便于多实例区分。

## 4. 高基数防护

`PlatformMeterCustomizer` 已治理：
- 拒绝 `http.server.requests` 中 `uri=/UNKNOWN` 或以 `/**` 结尾的未识别模板序列（防路由未归一化导致序列爆炸）。
- 对 `http.server.requests` 的 `uri` 标签设上限（200），超出后新序列被拒绝（兜底）。
- `exception` 标签的全限定类名替换为简单类名（降基数）。

## 5. 启动 Prometheus + Alertmanager

本目录的 `prometheus.yml` 与 `alerts.yml` 即 geelato 统一监控配置（平台 + 消息中心）。

```bash
prometheus --config.file=geelato-web-platform/deploy/observability/prometheus.yml --web.enable-lifecycle
```

该配置含两个 job：`geelato-platform`（manage.geelato.cn, HTTPS）与 `geelato-message`（message.ocean-bridges.com, HTTP）。
打开 `http://<prometheus>:9090/targets` 确认两者的 **State = UP**。
打开 `http://<prometheus>:9090/alerts` 确认告警规则已加载（`alerts.yml` 由 `prometheus.yml` 的 `rule_files` 引用，含 platform 黄金信号告警 + message 业务告警）。

## 6. 导入 Grafana 面板

1. Grafana → Dashboards → Import → Upload `grafana-dashboard.json`
2. 选择 Prometheus 数据源
3. 面板覆盖（黄金信号分组）：
   - **RED**：请求速率、错误率（5xx 占比）、p95/p99 延迟
   - **USE / 资源**：HikariCP 连接池饱和度、JVM 堆/非堆、GC、线程、CPU
   - **健康**：platformHealth/platformReadiness 状态、实例在线

## 7. 常用 PromQL 示例

```promql
# API 请求速率（QPS）
sum(rate(http_server_requests_seconds_count[5m])) by (application)

# 5xx 错误率
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
  / sum(rate(http_server_requests_seconds_count[5m]))

# API p95 延迟
histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))

# 连接池饱和度
hikaricp_connections_active / hikaricp_connections_max

# 堆内存使用率
sum(jvm_memory_used_bytes{area="heap"}) / sum(jvm_memory_max_bytes{area="heap"})
```

## 8. 生产环境收窄建议

- **暴露面**：`management.endpoints.web.exposure.include=health,prometheus`（去掉 info/metrics）。
- **鉴权**：`/actuator/**` 默认对内匿名放行（见 `SecurityInterceptorProperties`）；
  对外应在反向代理（Nginx/网关）层加 IP 白名单或 Bearer 鉴权，不要把 actuator 直接暴露公网。
- **抓取间隔**：30s 足够；流量与序列数成线性关系，见下方测算。

## 9. 流量测算

每日抓取流量 ≈ 单次响应大小 × (86400 / scrape_interval)。

平台默认指标（HTTP + HikariCP + JVM）单次响应约 800~1200 序列，gzip 后约 15~25 KB：

| scrape_interval | 每日抓取次数 | 每日流量（gzip，估算） |
|---|---|---|
| 15s | 5,760 | ~100–150 MB |
| 30s | 2,880 | ~50–75 MB |
| 60s | 1,440 | ~25–40 MB |

> Prometheus TSDB 磁盘占用约 1.5~3 字节/样本，远小于传输流量。

## 10. Druid 连接池场景说明

平台 primary 与动态数据源默认使用 **HikariCP**（Spring Boot 3 默认池），指标由 micrometer 自动采集。

若启用了 **workflow** 数据源（`spring.datasource.workflow.type=com.alibaba.druid.pool.DruidDataSource`），
Druid 的池指标**不会**进入 micrometer/Prometheus，需另行通过 Druid 自带的 `/druid/sql.html`
或 `spring.datasource.druid.stat-view-servlet` 观察。主线不实现 Druid 桥接。

## 11. 健康聚合语义

`PlatformHealthIndicator`（bean `platformHealth`）聚合所有 `HealthEndpoint` SPI 实现：
- 任一模块 `ABNORMAL` → 整体 `DEGRADED`（自定义状态，映射 HTTP 200，不触发 K8s 重启）
- 全 `HEALTH` → `UP`
- 仅 `UNKNOWN` → `UNKNOWN`

`PlatformReadinessIndicator`（bean `platformReadiness`）：
- 无 `HealthEndpoint` 或任一 `ABNORMAL` → `OUT_OF_SERVICE`（摘流量）
- 否则 `UP`

二者分离符合 K8s 语义：liveness 决定「是否重启」，readiness 决定「是否接流量」。
