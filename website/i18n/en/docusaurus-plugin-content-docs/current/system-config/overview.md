# System Configuration

This page explains the main configuration entry of `geelato-web-quickstart`, the per-module property files, and the key settings of each module.

The main file is:

- `geelato-web-quickstart/src/main/resources/application.properties`

The module directory is:

- `geelato-web-quickstart/src/main/resources/properties/`

## Organization Model

The current model is:

- one main file for global bootstrap and shared runtime settings
- one property file per module capability
- a unified import chain through `spring.config.import`

The current import order is:

1. `workflow.properties`
2. `seata.properties`
3. `oss.properties`
4. `package.properties`
5. `sc.properties`
6. `auth.properties`
7. `market.properties`
8. `message.properties`
9. `weixin_work.properties`
10. `elasticsearch.properties`
11. `monitor.properties`

## What `application.properties` Contains

The main file currently covers:

- server bootstrap such as `server.port`
- top-level feature switches such as `geelato.web` and `geelato.schedule`
- plugin directory and repository settings
- primary datasource configuration
- p6spy SQL logging settings
- multipart upload limits
- logging and metadata scan settings
- OCR, PDF, and AI service integration settings
- Redis and upload directory settings
- module property imports

## Property Style

The configuration heavily uses:

- `${ENV_NAME:defaultValue}`

This means:

- environment variables override first
- fallback defaults are kept for local startup

That fits local development, container deployment, and multi-environment switching.

The modules below are described in import order.

## Seata Module

File:

- `properties/seata.properties`

### Purpose

This file carries reserved distributed transaction settings.

### Key Properties

- `geelato.svcp`
- `seata.enabled`
- `seata.application-id`
- `seata.tx-service-group`

### Notes

- Seata is not enabled by default
- use it only when distributed transaction behavior is explicitly required
- for deeper datasource customization, continue with [ORM / Datasource Extension](../orm/datasource-extension.md)

## OSS Module

File:

- `properties/oss.properties`

### Purpose

This file holds Aliyun OSS access settings.

### Key Properties

- `geelato.oss.accessKeyId`
- `geelato.oss.accessKeySecret`
- `geelato.oss.endPoint`
- `geelato.oss.bucketName`
- `geelato.oss.region`

### Notes

- inject secrets through environment variables
- keep bucket and region aligned with the actual OSS resources
- make sure upload capability has a fallback path if OSS is not configured

## Package Module

File:

- `properties/package.properties`

### Purpose

This file contains package and upload-path related settings.

### Key Properties

- `geelato.package.env`
- `geelato.package.path`
- `geelato.package.uploadFolder`

### Notes

- override paths according to the real deployment machine
- keep temporary upload folders separate from final package output folders

## SC Module

File:

- `properties/sc.properties`

### Purpose

This file carries the SC feature switch and site path settings.

### Key Properties

- `geelato.sc`
- `geelato.sc.folder`
- `geelato.sc.path`

### Notes

- keep the folder aligned with the real deployment filesystem
- prefer environment-variable override instead of hard-coded machine paths

## Auth Module

File:

- `properties/auth.properties`

### Purpose

This file contains:

- OAuth2 client settings
- WeChat-related application settings
- `sa-token` basics

### Key Areas

- `geelato.oauth2.*`
- `geelato.wx.*`
- `sa-token.*`

### Notes

- keep OAuth2 client secrets and WeChat secrets in environment variables
- align the OAuth2 URL with the deployed unified authentication center
- combine this page with [Authentication and Authorization](../authentication/security-authentication.md) to understand how runtime backend auth consumes these credentials

## Market Module

File:

- `properties/market.properties`

### Purpose

This file contains the Market module datasource and debug logging settings.

### Key Areas

- `spring.datasource.market.*`
- `spring.datasource.market.hikari.*`
- `logging.level.cn.geelato.market`
- `logging.level.cn.geelato.orm`
- `logging.level.org.springframework.jdbc`

### Notes

- set `GEELATO_MARKET_JDBCURL` explicitly when Market uses a dedicated database
- avoid keeping DEBUG logging enabled in production for long periods

## Message Module

File:

- `properties/message.properties`

### Purpose

This file contains:

- message scheduling settings
- message datasource settings
- RabbitMQ connection settings

### Key Areas

- `geelato.message.schedule.*`
- `spring.datasource.message.*`
- `spring.datasource.message.hikari.*`
- `spring.rabbitmq.*`

### Notes

- set `GEELATO_MESSAGE_JDBCURL` explicitly when the message module uses its own database
- replace the default RabbitMQ guest credentials in production
- tune processing interval and batch size according to throughput needs

## Weixin Work Module

File:

- `properties/weixin_work.properties`

### Purpose

This file contains server-side signature validation settings for Weixin Work.

### Key Properties

- `wework.validate.token`
- `wework.validate.aeskey`
- `wework.validate.corpid`

### Notes

- keep these values aligned with the Weixin Work admin console
- inject them through environment variables in production
- do not confuse them with the generic WeChat settings in `auth.properties`

## Elasticsearch Module

File:

- `properties/elasticsearch.properties`

### Purpose

This file contains Elasticsearch connection settings.

### Key Properties

- `geelato.es.url`
- `geelato.es.username`
- `geelato.es.password`

### Notes

- keep credentials out of source defaults in production
- review debug-related properties in the main config before enabling verbose logs
- avoid accidental extra spaces in URLs

## Monitor Module

File:

- `properties/monitor.properties`

### Purpose

This file contains auxiliary suite monitoring settings.

### Key Areas

- `geelato.monitor.auxiliary-suites.enabled`
- `geelato.monitor.auxiliary-suites.poll-interval-seconds`
- `geelato.monitor.auxiliary-suites.connect-timeout-seconds`
- `geelato.monitor.auxiliary-suites.read-timeout-seconds`
- `geelato.monitor.auxiliary-suites.suites-json`

### Notes

- tune polling frequency according to the number of monitored targets
- keep `suites-json` readable and maintainable
- use stable health endpoints for external targets

## How To Read Module Property Files

Each file under `properties/` represents one focused capability area.

The practical reading rule is:

- use `application.properties` to understand the global runtime entry
- use one module file to understand one dedicated subsystem

Examples:

- `auth.properties` for authentication-related settings
- `message.properties` for message scheduling and RabbitMQ
- `workflow.properties` for workflow datasource and engine settings
- `oss.properties` for object storage

## Suggested Practice

- keep `application.properties` focused on global bootstrap and shared runtime settings
- keep module-specific settings inside `properties/*.properties`
- prefer environment variables for environment differences
- keep secrets out of source defaults whenever possible
