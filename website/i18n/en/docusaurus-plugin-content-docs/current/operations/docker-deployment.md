# Docker Deployment

This page explains the recommended containerization boundary for Geelato Framework.

The repository already separates runtime and design-time responsibilities into independent deliverables:

- `geelato-web-runtime`
- `geelato-web-platform`
- `geelato-web-designer`
- `geelato-app-scaffold`

Docker packaging should keep the same boundaries instead of merging every responsibility into one opaque image.

## Recommended Container Targets

### Runtime Container

Use this shape when you only need business execution capabilities. Package one of:

- `geelato-web-runtime`
- or an application built from `geelato-app-scaffold`

This container should expose runtime-facing APIs only and should not carry design-time management capabilities by default.

### Design-Time Container

Use this shape when you need metadata design, script management, and platform governance capabilities. Package:

- `geelato-web-designer`

It carries design-time capabilities, so it should remain distinct from pure runtime deployment.

## Configuration Recommendations

- inject database URL, username, and password through environment variables
- provide upload and conversion directories through mounted volumes

For example:

```properties
spring.datasource.primary.jdbc-url=${GEELATO_PRIMARY_JDBCURL}
spring.datasource.primary.username=${GEELATO_PRIMARY_JDBCUSER}
spring.datasource.primary.password=${GEELATO_PRIMARY_JDBCPASSWORD}
geelato.upload.root-directory=/data/upload
geelato.upload.convert-directory=/data/upload/convert
geelato.upload.config-directory=/data/upload/config
```

## Current Recommendation

At the current stage of the official repository, it is better to finish these checks first:

1. verify standard local deployment
2. verify database initialization
3. confirm the runtime/design-time boundary

Then externalize the same configuration into Docker images and orchestration.

## Suggested Reading

- [Standard Deployment](runtime-designer-deployment.md)
- [PlatformWebRuntime](../runtime/platform-web-runtime.md)
- [PlatformDesginer](../designer/platform-desginer.md)
- [App Scaffold](../guide/app-scaffold-starter-project-guide.md)
