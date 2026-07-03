# System Configuration

This page explains the main configuration entry of `geelato-web-quickstart` and how it splits module-specific settings into independent files under `properties/*.properties`.

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

## Suggested Reading

- [Workflow Module](workflow.md)
- [Auth Module](auth.md)
- [Message Module](message.md)
- [OSS Module](oss.md)
- [Monitor Module](monitor.md)
