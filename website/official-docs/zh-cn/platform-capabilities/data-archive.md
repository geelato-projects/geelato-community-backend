# 平台数据归档（geelato-archive）

系统运行数据（审计日志、发号流水、通知、邮件正文、页面版本历史、低代码业务表）随使用持续膨胀，
拖累查询性能、备份与打包体积。数据归档特性提供**策略驱动、可观测、可恢复**的通用归档引擎：
按策略把过期数据从源表搬移到归档目标（一期 MySQL 归档库/归档表），
两阶段对账保证**任意时刻崩溃源表最多多数据、绝不丢数据**。

## 模块组成

| 模块 | 职责 |
|---|---|
| `geelato-archive-policy` | 归档策略管理：策略实体（platform_archive_policy）、启用门禁校验（硬失败）、预置模板装载、实体管理"开启归档"集成入口、策略 REST 接口 |
| `geelato-archive-engine` | 归档引擎：目标表结构核对、两阶段对账搬移（SERVER/LOCAL_FILE/JDBC 三模式）、每日调度、运行记录（platform_archive_run）、手动触发/中止/回迁、运行 REST 接口 |

两个模块完全自包含（不改动 geelato-web-platform），属于**定制模块**（不进脚手架与标准件版本目录，
当前无任何宿主引入）。引入 `geelato-archive-engine` 一条依赖即全部生效：自动装配启动、幂等建表、
装载预置模板、Controller 随宿主 `@ComponentScan("cn.geelato")` 注册。

## 策略模型

- **策略基于表**（与实体解耦）：`table_name` + 策略条件。通常由实体管理"开启归档"生成
  （`POST /archive/entity/enable {entityName}` → 解析物理表名 → 插入默认停用策略），
  也可直接 `POST /archive/policy/createOrUpdate` 手工创建。
- **DEFAULT 默认策略**：条件固定为 `create_at < 当前时间 - retention_days`，
  水位在 run 开始时一次性锁定（防长时间运行边界漂移）。
- **CUSTOM 自定义策略**：`where_condition` 任意 WHERE 片段（如 `batch < 'B2024-001'`），
  引擎执行时原样拼入选数查询。安全模型：**条件仅参与"选 id"，搬移与删除始终按
  `id IN (...)` 精确执行**——条件写错最多少搬数据，绝不会丢数据。
  启用时做静态安全校验（禁分号/注释/DML/DDL/文件读写类关键词）+ 试跑（语法错误硬失败 70001）。
- **目标**：`target_type`（一期 MYSQL，MONGODB/ELASTICSEARCH 二期）+ `connect_id`
  （dev_db_connect 体系，空=主库自身同库归档）+ `target_table_name`
  （空则推导：跨库同名；同库 `{源表}_archive`）。
- **执行参数**：`execution_mode`（AUTO/SERVER/LOCAL_FILE/JDBC）、`batch_size`（默认 200）、
  `batch_interval_ms`（默认 200，批间限流）、`max_rows_per_run`（默认 100 万，渐进式归档：
  首次归档大表时分多天完成，到量即收次日继续）、`include_deleted`（默认 true，软删行一并归档）。
- **生命周期**：策略默认停用——**无启用策略绝不动任何数据**；启用需过门禁（见下）；
  run 开始时参数快照（执行中修改策略下轮生效）；同策略同时仅一个 run。

## 执行模式（MYSQL 目标）

| 模式 | 数据路径 | 适用与性能 |
|---|---|---|
| SERVER | `INSERT INTO 目标 SELECT … WHERE id IN` + DELETE 服务端完成 | 同库/同实例；**数据零出库零 JVM，最高** |
| LOCAL_FILE | 源库流式导出临时文件 → 归档库 `LOAD DATA LOCAL INFILE` | 跨实例推荐；接近原生 LOAD DATA（比 INSERT 快一个量级）；localInfile 被禁或含二进制列自动降级 JDBC |
| JDBC | 流式拉行 + 批量插入（`rewriteBatchedStatements`） | 通用兜底 |
| AUTO（默认） | 运行时探测：同实例→SERVER；否则 LOCAL_FILE（失败降级 JDBC） | 总是当前拓扑最快路径 |

## 不误删保障（两阶段对账）

```
每批（id 游标升序，LIMIT batch_size）：
  阶段1 写归档库：清残留 DELETE → 写入（按模式）→ 对账（载入行数==期望 且 目标 count==期望）
        不符 → 70003 中止（源库未动，零丢失）
  阶段2 删源库（事务）：DELETE WHERE id IN → 对账（删除数==期望）
        不符 → 70004 中止并整批回滚（归档库已完整，重跑安全）
  更新 run 进度（行数/批次/游标）→ 检查中止标志 → sleep(batch_interval_ms)
```

- 崩溃安全：任意时刻崩溃，源表最多多数据、绝不丢数据；重跑批首清残留后重插
  （不用 INSERT IGNORE，避免掩盖错误）。
- 方向性原则：**宁可重复（归档库有、源库也有），绝不丢失（源库删了、归档库没有）**。
- 运行记录：`platform_archive_run` 完整记录状态/行数/批次/游标/水位/实际模式/耗时，
  失败时 `error_json` 含表名/批次序号/游标/期望与实际行数/异常堆栈（精确诊断）。

## 启用门禁（硬失败，70001/70002）

1. 源表名格式合法（防注入）且不在保护名单（PROTECTED_TABLES：archive 自身、user/role/org/tenant、dev_db_connect 等）；
2. 策略类型合法：DEFAULT 需 retention_days ≥ 平台最小保留期（默认 30 天）；CUSTOM 需条件通过安全校验；
3. 源表物理存在；DEFAULT 策略需 create_at 列存在且为日期类型；
4. 归档库连接（connect_id）可达；
5. 同库归档目标表必须与源表不同名（防自残）；
6. 一表一启用策略；
7. CUSTOM 条件试跑（语法错误拦截）；
8. 目标表结构核对：不存在则按源表建表语句自动创建（SHOW CREATE TABLE 替换表名），
   存在则列结构比对——源表有而目标缺列 → 70002 中止（绝不静默跳列）。

## REST API（引入模块后即生效，登录鉴权走平台拦截器）

| 端点 | 说明 |
|---|---|
| `POST /archive/policy/pageQuery` | 策略分页查询（tableName/policyType/enableStatus/code/connectId 过滤） |
| `GET /archive/policy/get/{id}` | 策略详情 |
| `POST /archive/policy/createOrUpdate` | 创建/更新（新建一律停用） |
| `DELETE /archive/policy/isDelete/{id}` | 软删（运行中拒绝） |
| `POST /archive/policy/validate/{id}` | 启用前校验（只校验不启用） |
| `POST /archive/policy/enable/{id}` · `/disable/{id}` | 启用（过门禁）/停用 |
| `POST /archive/entity/enable` | 实体管理"开启归档"（入参 entityName） |
| `POST /archive/run/pageQuery` · `GET /archive/run/get/{id}` | 运行历史 |
| `POST /archive/run/trigger/{policyId}` | 手动触发单策略（同步执行） |
| `POST /archive/run/triggerAll` | 手动一次性执行全部启用策略（跑完即静默） |
| `POST /archive/run/cancel/{policyId}` | 优雅中止（完成当前批后停止） |
| `POST /archive/scheduler/start` · `/stop` · `GET /archive/scheduler/status` | 定时调度运行时启停与状态（内存态，重启后回到配置默认） |
| `POST /archive/run/archivedData` | 归档数据在线只读分页查询 |
| `POST /archive/run/restore` | 回迁（body：policyId + ids 列表，反向两阶段对账） |

错误码 70xxx 段见 [错误码参考](../reference/error-codes.md#70xxx-数据归档类)。

## 运行形态：日常静默，需要时再 run 起来

引擎**默认完全不运行**——不创建任何调度线程、不自动执行任何归档（`geelato.archive.scheduler-enabled`
默认 `false`）。需要归档时三种方式拉起：

| 方式 | 端点/配置 | 说明 |
|---|---|---|
| 手动触发单策略 | `POST /archive/run/trigger/{policyId}` | 同步执行该策略一次 |
| 手动触发全部一次 | `POST /archive/run/triggerAll` | 串行执行所有启用策略一次，跑完即静默（无常驻线程） |
| 临时启动每日定时 | `POST /archive/scheduler/start` | 按 schedule-time（默认 02:00）每日执行；`/stop` 随时停、`/status` 看状态；内存态，重启后回到默认静默 |

需要长期定时归档时配置 `geelato.archive.scheduler-enabled=true` 随应用自动启动。
所有策略按 seq_no 串行执行，单策略失败不影响后续。调度器为自管理守护线程
（照 NotificationOutboxScheduler 惯例，不依赖 @EnableScheduling）。

## 全局配置（geelato.archive.*）

| 配置 | 默认 | 说明 |
|---|---|---|
| `scheduler-enabled` | false | 定时调度是否随应用启动（false=引擎日常静默，仅手动触发可用） |
| `schedule-time` | 02:00 | 每日执行时刻 |
| `min-retention-days` | 30 | 最小保留期门禁 |
| `protected-tables` | 核心表清单 | 保护名单（防呆） |
| `default-batch-size` / `default-batch-interval-ms` | 200 / 200 | 新建策略默认批参数 |
| `default-max-rows-per-run` | 1000000 | 单次运行上限默认值 |
| `temp-dir` | `${java.io.tmpdir}/geelato-archive` | LOCAL_FILE 临时目录 |
| `auto-init-tables` | true | 启动幂等建表 |
| `auto-load-presets` | true | 启动装载预置模板（幂等） |

## 预置模板（默认停用，核阅后启用）

| code | 表 / 保留期 |
|---|---|
| tpl-audit-log | platform_audit_log / 730（对齐 AuditLogProperties.retentionDays 语义） |
| tpl-encoding-log | platform_encoding_log / 365 |
| tpl-notification · tpl-notification-user | platform_notification(_user) / 180 |
| tpl-mail-message | mail_message / 365（正文 longtext，体积收益最大） |
| tpl-app-page-log | platform_app_page_log / 180 |
| tpl-schedule-log | platform_schedule_log / 90 |

## 引入指南（定制模块，按项目显式引入）

数据归档是**定制模块**，不属于平台标准件：不进 geelato-app-scaffold-starter（脚手架）、
不进 geelato-framework-starter / geelato-framework-bom（标准件版本目录）、
不注册到根聚合 pom（不参与标准构建流水线，照 geelato-mail 被注释的先例）。

**构建方式**（脱离聚合，需 `-f` 独立构建，先 policy 后 engine）：

```bash
mvn clean install -f geelato-archive-policy/pom.xml -DskipTests
mvn clean install -f geelato-archive-engine/pom.xml -DskipTests
```

需要归档能力的项目在自身 pom 中显式引入（含版本号）：

```xml
<dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-archive-engine</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

（geelato-archive-policy 随之传递引入。）

随后建议在项目 logback 配置中增加日志路由（照 pack 模块惯例）：

```xml
<appender name="archiveLogFile" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <Prudent>true</Prudent>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <FileNamePattern>${LOG_DIR}/archive/%d{yyyy-MM-dd}.%i.log</FileNamePattern>
        <maxFileSize>1000MB</maxFileSize>
        <maxHistory>2</maxHistory>
    </rollingPolicy>
    <layout class="ch.qos.logback.classic.PatternLayout">
        <pattern>${LOG_PATTERN}</pattern>
    </layout>
</appender>
<logger name="cn.geelato.archive" additivity="false">
    <appender-ref ref="archiveLogFile"/>
    <appender-ref ref="console"/>
</logger>
```

启动即自动：幂等建表（platform_archive_policy / platform_archive_run）→ 装载预置模板（停用态）→
启动每日调度。之后在策略管理中核阅并启用所需策略即可。

## 二期演进（预留）

- MongoDB / Elasticsearch 归档通道（Channel SPI：bulkWrite / _bulk；策略模型 target_type 已预留）；
- SeaTunnel 外部执行器（数据面移交专业同步引擎，平台只做策略/编排/对账；executor 字段已预留）；
- 前端管理页（geelato-front：实体管理"开启归档"按钮 + 策略管理页）；
- 软删行独立窗口（按 delete_at）、租户差异化保留期。
