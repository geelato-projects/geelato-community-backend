# Core Modules

This page maps the modules in the geelato-community repository by layer. Framework-layer modules are consumed directly by host projects and backend developers; platform-layer modules form the low-code platform runtime and shells and ship with the platform application.

## Framework Layer

### `geelato-lang`

Shared contracts, API result models, and low-level annotations.

### `geelato-utils`

Shared utility capabilities and cross-module helpers, including the unified local cache `LocalBoundedCache`.

### `geelato-security`

Security contracts and context model (User, Org, Role, Permission, Tenant, etc.).

### `geelato-framework-bom`

Curated BOM for framework consumers, aligning framework module and third-party dependency versions.

### `geelato-framework-starter`

Unified starter for the foundation modules, forming the minimal base. Capabilities with strong implementation preferences ship as optional dependencies.

### `geelato-core`

The framework kernel: metadata model, MQL engine, SQL builder, and ORM runtime management, plus the global context (`GlobalContext`/`SessionCtx`), the datasource registry (`DataSourceManager`), and experiment gates.

### `geelato-orm`

Fluent DSL (`MetaFactory` chained CRUD), command execution, and ORM event hooks for backend developers, with the dynamic datasource capability under `cn.geelato.datasource` (routing datasource, `DynamicDataSourceScope` switching, entity resolution, p6spy SQL logging defaults).

### `geelato-script`

General-purpose script engine SDK: GraalVM Polyglot sandbox, the `@GraalService` kit registry, the six built-in kits (`$gl.sql`/`$gl.mql`/`$gl.http`/`$gl.encrypt`/`$gl.ctx`/`$gl.parameter`), and engine revision snapshots. See [Script Engine](../script/overview.md).

## Platform Layer

Platform-layer modules are not part of the framework minimal base; they ship with the platform application.

### Platform entities and shared base

| Module | Responsibility |
| --- | --- |
| `geelato-meta` | Platform entities and metadata definitions (apps, pages, dictionaries, messages, attachments, etc.) |
| `geelato-web-common` | Web-level shared support: filters and interceptors, Shiro/OAuth2 integration, the token authentication SPI (`TokenAuthenticator`), traffic marking, online users, log routing |
| `geelato-web-oss` | Object storage abstraction and providers (built-in Alibaba Cloud implementation) |

### Application shells and boot host

| Module | Responsibility |
| --- | --- |
| `geelato-web-runtime` | Runtime application shell (PlatformWebRuntime) |
| `geelato-web-designer` | Designer application shell (PlatformDesginer) |
| `geelato-web-platform` | Shared platform base (controllers + services + boot) |
| `geelato-web-platform-extras` | Optional heavy-dependency feature pack: OCR, Office-to-PDF, 7zip, Elasticsearch, JGit, seata, atomikos, JDBC drivers |
| `geelato-app-scaffold-starter` | Starter that assembles runtime base services for scaffold applications |
| `geelato-web-quickstart` | Platform boot host (full feature set); the platform starts from this module by default |

### Capability modules

| Module | Responsibility |
| --- | --- |
| `geelato-search-api` / `geelato-search-lucene` | Search abstraction (`SearchEngine` port) and the embedded Lucene engine (NRT + ngram contains, async event indexing, failure compensation, reconciliation, reindex) |
| `geelato-exception` | Business exception center: rule library, instances, handling steps, trails, notification records, scan records |
| `geelato-alert` | Business alert center: alert rules, notification templates, silence windows, alert records |
| `geelato-mail` | Personal mailbox client: IMAP receive, SMTP send, filters, labels, signatures, vacation replies, contacts |
| `geelato-maintenance` | Built-in maintenance: entity three-way sync validation and compensation, MQL test suites and online debugging toolchain |

### Custom modules and standalone builds

| Module | Responsibility |
| --- | --- |
| `geelato-archive-policy` / `geelato-archive-engine` | Archive policy management and archive engine (two-phase reconciled moving, scheduling, restore); self-contained, introduced per project, not part of the platform standard set |
| `geelato-mcp` | MCP services (common/fms/platform submodules); not part of the aggregate build, install separately |
| Sibling repositories | Workflow (geelato-workflow), dynamic integration (geelato-dyn, Karaf/OSGi), packaging (geelato-package) live in separate repositories; see the dedicated chapters |

## Key Architecture Notes

- `SecurityContext` must be written only after successful authentication in the security chain
- `DynamicDataSourceScope` is the single entry point for datasource switching; `primaryDao` never routes, and `dynamicDao` remains higher priority than `primaryDao` for compatibility fallback
- Upload capabilities with strong implementation preferences stay outside the minimal starter base and ship as optional dependencies guarded by the platform feature pack

## First Consumer References

- [ORM Overview](../orm/overview.md), [ORM Annotations](../orm/annotations.md), [Fluent DSL Guide](../orm/fluent-dsl.md)
- [Dynamic Datasource](../dynamic-datasource/overview.md)
- Sample skeleton: `geelato-sample-quickstart` (in geelato-hello-example)
- Runtime / Designer shells: [PlatformWebRuntime](../runtime/platform-web-runtime.md), [PlatformDesginer](../designer/platform-desginer.md)
