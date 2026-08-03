# 插件机制改造完整方案（改进路线 P0/P1/P2 + 多租户治理，免建表）

> 合并「插件机制评估改进路线」与「多租户插件治理」为完整方案。
> 技术基线：共享文件系统 + JSON 配置（平台级 1 个 + 每租户 1 个）、不需要审计、不新增数据库表、复用 pf4j `PluginStatusProvider`、租户治理默认开启。
> 鉴权定调：**平台级开关不鉴权、不改权限体系**（去掉 `isPlatformAdmin()`，租户级仍用现有 `SecurityContext.isAdmin()`）。

---

## 第〇部分：改进项总览（P0+P1+P2 全含）

| 编号 | 改进项 | 维度 | 落地形态 |
|---|---|---|---|
| **P0-A** | 扩展点 API 源码入仓（`libs/plugin-all`） | 可扩展性 | 新增源码模块，废弃手动 install jar |
| **P0-B** | 插件加载容错 | 稳定性 | `FileSpringPluginManager` 重写 load/start（内嵌） |
| **P0-C** | getBean 缓存 + 调用超时 | 可靠性 | `PluginBeanProvider` 改造（内嵌） |
| **P1-多租户** | 平台/租户双开关 | 多租户治理 | `FilePluginStatusProvider`+`TenantPluginGate`+双接口 |
| **P1-D** | 健康检查（扩展可用性） | 可靠性 | 扩展点加 `healthCheck()`，`/pm/list` 增列 |
| **P1-F** | 状态持久化 | 稳定性 | JSON 文件替代内存态（含于多租户） |
| **P2-A** | 日志接入 logback | 可观测 | 废弃自写文件日志 |
| **P2-B** | `switchStatus` 改 POST + 幂等 | 安全/规范 | 接口语义修正（含于 P1 接口改造） |
| **P2-C** | 插件签名校验 | 安全 | pf4j 签名扩展 |
| **P2-D** | 插件调用指标 | 可观测 | Micrometer 计数/耗时 |

---

## 第一部分：P0 地基

### P0-A 扩展点 API 源码入仓
**问题**：`plugin-all`（`OCRService` 等扩展点）是 `libs/` 预编译 jar，靠 `mannal.sh` 手动 `install:install-file`，无源码可追溯。
**做法**：`libs/` 下补建 `plugin-all` 源码模块：
- `libs/plugin-all/pom.xml`：parent 指向 `libs/pom.xml`，依赖 `org.pf4j:pf4j:3.12.0`(provided)。
- 还原现有 jar 内容为源码（`PluginExtensionPoint`、`ocr/OCRService`+`PluginInfo`(PluginId="ocr-plugin")+DTO），保证现有 `OCRController` 调用 100% 兼容。
- 取消 `libs/pom.xml` 注释的 `<module>`，纳入正常 `mvn install`，废弃 `mannal.sh`。

### P0-B 插件加载容错（内嵌 `FileSpringPluginManager`）
**问题**：任一插件启动失败可能阻断整个应用启动（OCR 这类 native 重插件风险尤高）。
**做法**：`FileSpringPluginManager extends SpringPluginManager`，重写 `loadPlugins`/`startPlugin`，单插件失败 catch → 标记 DISABLED + 记日志，**不阻断主程序**。

### P0-C getBean 缓存 + 调用超时（内嵌 `PluginBeanProvider`）
**问题**：每次请求都 `getExtensions` 查找；插件卡死会拖垮调用线程。
**做法**：扩展实例缓存 `(type,pluginId)→实例`；扩展方法调用走带超时代理（独立线程池 + `Future.orTimeout`），超时抛 `PluginInvocationTimeoutException`。

---

## 第二部分：P1 多租户治理（核心）

### 共享文件配置布局
```
共享卷（所有节点挂载同一物理路径，应用层视为普通目录）
plugins-config/
├── plugins-enabled.json              ← 平台级总开关
└── tenants/
    ├── tenant_geelato.json           ← 平台租户（现阶段含 ocr-plugin、invoice-ocr-plugin）
    └── tenant_{code}.json            ← 其他租户
```

**`plugins-enabled.json`**：
```json
{ "enabled": ["ocr-plugin","invoice-ocr-plugin"], "disabled": [], "updatedAt":"...", "updatedBy":"admin" }
```
**`tenant_{code}.json`**：
```json
{ "tenantCode":"geelato", "enabled":["ocr-plugin","invoice-ocr-plugin"], "updatedAt":"...", "updatedBy":"admin" }
```

### 初始化策略（强制门控下零故障保证）
- **平台级 JSON 缺失** → 扫描 `plugins/` 全部已部署插件写入 `plugins-enabled.json`(enabled)。
- **`tenant_geelato.json` 缺失** → 同样写入全量（平台租户默认全可用）。
- **其他租户 JSON 缺失** → 不自动生成，默认无插件（显式开启）。
- 存量 `ocr-plugin` 启动即登记，`OCRController` 不受影响。

### 组件清单（全部在 `geelato-web-platform` 的 `plugin` 包下，不改业务代码）

**1. `PluginConfigurationProperties`（改造，新增）**
```properties
geelato.plugin.config-directory=${GEELATO_PLUGIN_CONFIG_DIR:plugins-config}
```

**2. `FilePluginStatusProvider implements PluginStatusProvider`（P1-多租户 + P1-F）**
- 数据源 `{config-directory}/plugins-enabled.json`，实现 `isEnabled`/`enablePlugin`/`disablePlugin`，回写文件。
- 并发写：`FileChannel.lock()` 文件锁（共享卷多节点互斥）；读：本地缓存 5s + mtime 失效。
- 缺失时触发初始化。

**3. `FileSpringPluginManager extends SpringPluginManager`（P0-B）**
- 重写 `createPluginStatusProvider()` 返回 `FilePluginStatusProvider`。
- 重写 load/start 容错。
- `PluginConfiguration.pluginManager` Bean 改用本子类。

**4. `TenantPluginGate`（P1-多租户）**
- `@Component`，读 `tenants/tenant_{tenantCode}.json`。
- `boolean isAvailable(pluginId)`：取当前租户 → 短路查平台级（关→false）→ 读租户 `enabled`（缓存+mtime）→ 返回。
- 平台租户文件缺失走初始化；其他租户缺失→false。

**5. `PluginBeanProvider`（改造，P0-C + P1-多租户）**
- `getBean(type, pluginId)`：**始终**先 `tenantPluginGate.isAvailable(pluginId)`，未启用抛 `PluginNotEnabledForTenantException`；通过后取缓存实例，调用包装超时。无开关回退。

**6. `PluginManagerController`（改造+新增，P1 + P2-B）**
- `/pm/platform/switch`（POST）：平台级总开关。**不鉴权**（按你的定调）。调 `enablePlugin/disablePlugin`。
- `/pm/tenant/switch`（POST）：租户级开关。校验现有 `SecurityContext.isAdmin()`；目标插件须平台级已启用，写 `tenant_{code}.json`。
- `/pm/list`（改造，P1-D）：平台 admin 看全部+平台开关+各租户概览；租户 admin 看平台已启用+本租户状态；增「扩展可用性」列。
- 现有 `/pm/switchStatus` 改 POST + 标 `@Deprecated`（P2-B），内部转发到 platform/tenant。
- `/pm/refresh`（新增）：手动失效各节点本地缓存。

**7. `healthCheck()` 扩展点默认方法（P1-D）**
- `PluginExtensionPoint` 增 `default boolean healthCheck(){return true;}`，插件可覆写做真实探测（如 OCR 模型就绪）。`/pm/list` 调用反映扩展真实可用性。

**8. 异常与错误码**
- `PluginNotEnabledForTenantException`、`PluginInvocationTimeoutException` `extends CoreException`。
- `PlatformErrorCodes` 增：`PLUGIN_NOT_ENABLED_FOR_TENANT(10010)`、`PLUGIN_PLATFORM_DISABLED(10011)`、`PLUGIN_INVOCATION_TIMEOUT(10012)`、`PLUGIN_LOAD_FAILED(10013)`。

### 并发与一致性
- 多节点读：本地缓存 5s + 读共享文件，变更最长 5s 生效（或 `/pm/refresh` 立即失效）。
- 多节点写：`FileChannel.lock()` 互斥，读-改-写整体加锁。
- 共享卷不可用：读用上次缓存；写返回错误。

---

## 第三部分：P2 运维/安全（本次一并交付）

### P2-A 日志接入 logback
- 废弃 `PluginLogUtil` 自写 `plugins/logs/<id>.log`，插件日志用 `LoggerFactory.getLogger("plugin."+pluginId)` 接入主程序 logback，支持滚动/统一收集。
- `/pm/log` 改为读取该 logger 最近的日志片段（带大小上限，避免 `Files.readAllLines` OOM）。

### P2-B switchStatus 改 POST + 幂等（已并入 P1 接口改造）

### P2-C 插件签名校验
- production 模式下用 pf4j 的 `PluginDescriptor` + 签名机制（自定义 `PluginLoader`/`PluginRepository` 校验 jar 签名），拒绝未签名/篡改插件 jar。
- 提供配置开关 `geelato.plugin.signature-verify=${GEELATO_PLUGIN_SIG_VERIFY:false}`（默认关闭，避免阻塞现有无签名插件；开启后强制校验）。

### P2-D 插件调用指标
- 引入 Micrometer（Spring Boot Actuator 已自带），记录每个 `(pluginId, extension)` 的调用数/耗时/失败率。
- `/pm/list` 或 Actuator 端点可查。

---

## 第四部分：配置与部署

### 配置项（追加到 `geelato-web-quickstart/.../application.properties`）
```properties
geelato.plugin.config-directory=${GEELATO_PLUGIN_CONFIG_DIR:plugins-config}
geelato.plugin.signature-verify=${GEELATO_PLUGIN_SIG_VERIFY:false}
```

### 部署步骤
1. 共享卷建 `plugins-config/` 与 `plugins-config/tenants/`。
2. `GEELATO_PLUGIN_CONFIG_DIR` 指向共享挂载点（所有节点一致）。
3. 启动应用 → 自动初始化 `plugins-enabled.json` + `tenant_geelato.json`（全量存量插件）。
4. 验证 `/api/pm/list`、`OCRController` 调用正常。
5. OCR 落地时，把 `invoice-ocr-plugin` 加入平台级 + `tenant_geelato.json`。

---

## 第五部分：实施顺序

```
阶段1（地基）: P0-A 扩展点入仓 → P0-B 容错(FileSpringPluginManager) → P0-C 缓存/超时
阶段2（治理）: P1 多租户(FilePluginStatusProvider/TenantPluginGate/双接口) + P1-D 健康检查
阶段3（运维）: P2-A logback → P2-C 签名校验 → P2-D 指标
```
本次交付：**阶段1 + 阶段2 + 阶段3（P0+P1+P2 全做）**。
不含：发票 OCR 插件本身实现（待本机制落地后再议）。

---

## 确认事项（已全部确认）
1. 初始化策略：平台级 + `tenant_geelato.json` 缺失自动写全量；其他租户不自动生成。✅
2. 不改权限体系：**平台级开关不鉴权**，去掉 `isPlatformAdmin()`；租户级用现有 `isAdmin()`。✅
3. 交付范围：P0+P1+P2 全含。✅

**请审阅。确认后实施。**