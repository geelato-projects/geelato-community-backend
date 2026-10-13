# Script Engine

geelato-script is the general-purpose script execution engine SDK of Geelato Framework. Built on the GraalVM Polyglot sandbox, it provides safe embedded scripting for scenarios such as exception-rule judgment pipelines and alert template rendering.

## Execution Model

Each execution is an isolated loop:

1. The consumer assembles a `ScriptExecution` (content, language, timeout, injected variables, attributes) and submits it to `ScriptEngine.execute`;
2. The engine resolves the engine version (`EngineRevision` snapshot) and the scenario profile (`ScriptProfile`), activating whitelisted kits;
3. A fresh Polyglot Context is populated with `console`, built-in kits (`$gl.*`), injected variables (`$ctx`/`$params`, etc.) and the execution environment (`$env`);
4. The script runs on a worker thread; the calling thread enforces the timeout with a hard kill (`context.close(true)`);
5. A `ScriptResult` is returned: status (SUCCESS/ERROR/TIMEOUT), value, console logs, cost, SQL row count, a kit-call trace and a structured error (type + line/column + stack).

## Sandbox Constraints

- **No file or network primitives**: IO, process creation, native access and environment variables are denied (outbound HTTP only via the `$gl.http` allowlist channel);
- **Class-access allowlist**: only methods annotated with `@GraalService`/`@GraalFunction` are callable; `Class`/`ClassLoader`/`System`/`Runtime`/`Thread` are rejected;
- **Resource limits**: a per-execution statement limit (`statement-limit`) plus an execution timeout;
- **Controlled kit signatures**: parameter and return types are validated fail-fast at registration.

## Engine Versions and Profiles

- **EngineRevision**: a frozen snapshot of the kit set; breaking changes ship as a new version with parallel routing, and old versions can be marked DEPRECATED/RETIRED. Defaults to `v1`.
- **ScriptProfile**: declares the kit namespaces (`namespaces`) and injectable variables (`variables`) visible to a scenario, plus a default timeout.

## Built-in Kits

`ScriptEngineBuilder.build()` registers six built-in kits by default (an explicit same-name registration from the consumer takes precedence): `$gl.sql`, `$gl.mql`, `$gl.http`, `$gl.encrypt`, `$gl.ctx`, `$gl.parameter`. See the [script authoring guide](/docs/script/usage) for APIs and conventions.

## Variables

| Variable | Source | Notes |
| :--- | :--- | :--- |
| `$ctx` / `$gl.ctx` | caller bindings | business context, dual-entry same source |
| `$params` / `$gl.parameter` | caller params | script parameter input, dual-entry same source |
| `$input` | caller bindings | previous step output in pipelines |
| `$env` | engine | execution metadata (execId, engine version, tenant, user, read-only) |
| `console` | engine | logging, returned with ScriptResult |

Injected variables are filtered by the `ScriptProfile.variables` whitelist; `$gl.ctx`/`$gl.parameter` are kit-form entries governed by the `namespaces` whitelist instead.

## Host Wiring

In Spring environments the engine is auto-configured via `geelato.script.engine.*`; consumers add profiles, revisions, data sources (`dataSourceSupplier`) and ORM supplies (`daoSupplier`/`ormWrapper`) through a `ScriptEngineCustomizer` bean. The SDK does not bind how data sources are located.
