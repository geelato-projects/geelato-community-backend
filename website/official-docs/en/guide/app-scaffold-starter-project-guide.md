# Create a Business Project Based on app-scaffold-starter

This guide explains how to create a production-ready backend project based on `cn.geelato:geelato-app-scaffold-starter`, and how to use the built-in baseline capabilities.

## 1. Create a new project

You can either:
- copy the runnable sample `geelato-hello-example/geelato-app-scaffold`, then change Maven coordinates / package name / configs
- or create an empty Spring Boot project and add the starter dependency

Minimal `pom.xml` snippet:

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>cn.geelato</groupId>
      <artifactId>geelato-framework-bom</artifactId>
      <version>${geelato.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>

<dependencies>
  <dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-app-scaffold-starter</artifactId>
  </dependency>
  <dependency>
    <groupId>mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

## 2. Bootstrap class

Use `cn.geelato.web.platform.boot.BootApplication` as the base class and make sure your business package is included in scanning:

```java
@SpringBootApplication(scanBasePackages = {"cn.geelato", "com.acme.order"})
public class AcmeOrderApplication extends BootApplication {}
```

## 3. application.properties (minimal)

```properties
spring.application.name=acme-order-center
server.port=8088

spring.datasource.primary.name=primary
spring.datasource.primary.jdbc-url=${GEELATO_PRIMARY_JDBCURL:jdbc:mysql://localhost:3306/acme_order_center?useUnicode=true&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true}
spring.datasource.primary.username=${GEELATO_PRIMARY_JDBCUSER:acme_user}
spring.datasource.primary.password=${GEELATO_PRIMARY_JDBCPASSWORD:acme@psd}
spring.datasource.primary.driver-class-name=com.mysql.cj.jdbc.Driver

geelato.orm.dao-bean-name=dynamicDao
geelato.datasource.dynamic.enable-jta-transaction=false
geelato.datasource.dynamic.enable-seata-proxy=false

geelato.app.scaffold.enabled=true
geelato.app.scaffold.auto-init-tables=true
geelato.app.scaffold.strict=true
geelato.app.scaffold.capabilities=login,mql,organization,user,dictionary,upload

springdoc.api-docs.enabled=true
springdoc.api-docs.path=/v3/api-docs
geelato.app.scaffold.openapi-enabled=true
geelato.app.scaffold.openapi-expose-in-prod=false

# OSS (optional)
# enable /api/oss/* only when the config is complete
# geelato.oss.accessKeyId=***
# geelato.oss.accessKeySecret=***
# geelato.oss.endPoint=oss-cn-xxx.aliyuncs.com
# geelato.oss.bucketName=your-bucket
# geelato.oss.region=cn-xxx

geelato.meta.scan-package-names=cn.geelato,com.acme.order
geelato.graal.scan-package-names=cn.geelato,com.acme.order
```

## 4. Verify starter readiness

`GET /api/scaffold/ready`

## 5. How to use baseline capabilities

### 5.1 Authentication (JWT + OAuth2)
- JWT login: `POST /api/user/login` (no Authorization required)
  - subsequent calls: `Authorization: JWTBearer {token}`
- OAuth2 login: `POST /api/oauth2/login`
  - refresh: `POST /api/oauth2/refreshToken`
  - subsequent calls: `Authorization: Bearer {accessToken}`

### 5.2 File upload / download (OSS optional)
- Upload: `POST /api/upload/file`
- Download file: `GET /api/resources/file`
- Download JSON config: `GET /api/resources/json?fileName=...`
- OSS management (only when OSS enabled): `/api/oss/*`

### 5.3 MQL (generic CRUD)
- `POST /api/meta/list`
- `POST /api/meta/save/{biz}`
- `POST /api/meta/delete/{biz}/{id}`

### 5.4 ORM Fluent DSL (backend code)
Use `MetaFactory` in your business services. See `ORM / Fluent DSL` docs in this site.

### 5.5 Dictionary
- Maintain dictionaries: `/api/dict/*`, `/api/dict/item/*`
- Fetch by code: `GET /api/dictionary/{code}`

### 5.6 Organization / User / RBAC
- Org: `/api/security/org/*`
- User: `/api/security/user/*`
- Role: `/api/security/role/*`
- Permission: `/api/security/permission/*`

### 5.7 Swagger (OpenAPI)
- `/v3/api-docs`
- `/swagger-ui/index.html`

### 5.8 Build HTTP services quickly
Create your own controllers/services in your business package. Prefer using `@ApiRestController` so the `/api` prefix applies consistently.
