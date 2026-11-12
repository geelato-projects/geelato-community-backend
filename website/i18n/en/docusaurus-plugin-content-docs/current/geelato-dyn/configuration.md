---
title: geelato-dyn Configuration & Logging
sidebar_label: Configuration & Logging
---

# Configuration & Logging

geelato-dyn does not use Spring's `application.yml`. Its configuration is built on **OSGi Config Admin**: a Blueprint `cm:property-placeholder` defines defaults, a `.cfg` file under the Karaf `etc/` directory overrides them by PID, and changes take effect hot.

## Configuration Model

```text
Blueprint cm:default-properties (in-code defaults, works out of the box)
        ▲ overrides
Karaf etc/<PID>.cfg (environment-specific values; Config Admin rebuilds beans on change, no restart)
```

Key points:

- **cfg values take precedence over defaults**, and you only need to override the keys that differ per environment;
- `update-strategy="reload"`: saving a cfg change applies it immediately (e.g. datasource pools are rebuilt automatically);
- cfg files natively support `${env:XXX}` environment-variable references — preferred for credentials so no plaintext lands on disk;
- If Blueprint defaults are blanked and the cfg is missing, the bundle fails fast at startup naming the missing property, preventing silent misrouting to the wrong database.

## PID Inventory

| PID (cfg file name) | Module | Key settings |
|---|---|---|
| `cn.geelato.dyn.datasource` | geelato-dyn-datasource | Connection and pool settings for the 4 logical databases |
| `cn.geelato.dyn.manage` | geelato-dyn-manage | Console access token |
| Business modules | bus-* / ob-integrate etc. | See each module's Blueprint `cm:property-placeholder` |

### `cn.geelato.dyn.datasource`

Key pattern: `<logicalName>.jdbcUrl / username / password / pool.maxSize / pool.minIdle`. The cfg template lives in the repo at `geelato-dyn-datasource/config/`:

```properties
platform.jdbcUrl = jdbc:mysql://<host>:3306/geelato?useSSL=true&serverTimezone=Asia/Shanghai&characterEncoding=UTF-8
platform.username = geelato_user
platform.password = ${env:GEELATO_PLATFORM_DB_PASSWORD}
platform.pool.maxSize = 10
platform.pool.minIdle = 2
# portal / integration / buss follow the same pattern
```

For what the logical names mean and how to reference them, see [Built-in Capabilities / Central Datasources](capabilities.md#central-datasources).

### `cn.geelato.dyn.manage`

```properties
manage.console.token=<access-token>
```

The console exposes sensitive data such as database credentials, so a **non-empty token is mandatory**; when it is unset, `/cxf/dyn/manage/api/*` always responds 503 (the page loads but shows no data). Changes apply hot on save.

## Blueprint bean Configuration

Some settings are not cfg keys but bean properties inside Blueprint XML; changing them requires rebuilding and republishing the bundle:

| Location | Contents |
|---|---|
| `geelato-dyn-common/.../geelato-common-message.xml` | Message outbox `MessageCenterOutbox` (baseUrl / systemCode / apiKey / timeoutMs); in-app direct channel `InAppDirectChannelAdapter` (enabled / baseUrl / communityToken / timeoutMs) |
| `geelato-dyn-unified-api/.../spring-jdbc.xml` | Callback persistence datasource; processor switches (unifiedConsumer=true, db / rabbitMq default false); RabbitMQ connection settings |
| Business module `*.xml` | Business config beans (API URLs, secrets, cron expressions, etc.) |

:::note Choosing where a setting belongs
One codebase, two usages: with no cfg deployed the XML defaults apply (zero ops); dropping the cfg template into `etc/` hands control to operations. Values that change per environment (databases, tokens) should go through cfg first.
:::

## Logging: Per-module Separation

The Karaf (pax-logging-log4j2) baseline config gives each of the 13 modules its own logger by root package plus its own log file, **with no Java code changes**:

- Log files live in Karaf's `data/log/`, named `dyn-<module>.log` (e.g. `dyn-common.log`, `dyn-unified-api.log`);
- All module loggers set `additivity=false`: module logs go only to their own file, and `karaf.log` keeps container plus Camel / CXF / Spring / Blueprint framework logs;
- Rolling policy matches karaf.log: 16MB per file, up to 20 rolled files per module;
- Attribution follows the package the code lives in: classes in `cn.geelato.dyn.common` invoked from other modules still log into `dyn-common.log`;
- Nested packages never cross files (Log4J2 matches the most specific logger).

The baseline file and push script are in the repo under `karaf/`: `karaf/etc/org.ops4j.pax.logging.cfg` + `karaf/scripts/apply-logging-config.sh` (backs up the target's current config before overwriting). If the target instance has customized its logging cfg, do not overwrite the whole file — append the baseline's `geelato-dyn modules` block to the existing file instead.

### Runtime Level Changes

```bash
karaf@root()> log:set DEBUG cn.geelato.dyn.bus.shipping
karaf@root()> log:get cn.geelato.dyn.bus.shipping
karaf@root()> log:set INFO cn.geelato.dyn.bus.shipping    # revert
```

`log:set` writes the change back into the instance's `org.ops4j.pax.logging.cfg`, so the instance file diverging from the repo baseline afterwards is expected; the instance file is authoritative.

### When Adding a Module

Append one module block (appender + logger, three paired renames) to the end of `org.ops4j.pax.logging.cfg` and re-push the baseline; the template is in the repo's `karaf/README-logging.md`.

## Recommended Reading

- [Built-in Capabilities](capabilities.md) — central datasources / health / security / scheduling / console
- [Developing a New Module](develop-module.md) — scaffold and configuration wiring
