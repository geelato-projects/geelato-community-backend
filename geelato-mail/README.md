# geelato-mail

Geelato 邮件模块：个人邮箱客户端（IMAP 收信 / SMTP 发信 / 过滤器 / 标签 / 签名 /
假期回复 / 联系人 / 附件 / 草稿 / 定时同步），自 geelato-web-fms 的 mail 服务与
community 的 srv/email 合并而成，作为脚手架（geelato-app-scaffold-starter）的默认组成部分。

## 接入

脚手架应用**无需任何配置**：依赖 `geelato-app-scaffold-starter` 即含本模块，
启动时自动在宿主主库幂等建表（13 张 `mail_*` 表）。

不需要邮件能力的应用，在 pom 中排除：

```xml
<dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-app-scaffold-starter</artifactId>
    <exclusions>
        <exclusion>
            <groupId>cn.geelato</groupId>
            <artifactId>geelato-mail</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

## 配置

| 配置 | 默认 | 说明 |
|---|---|---|
| `geelato.mail.kek` | 空 | 邮箱凭据主密钥（AES-256-GCM），建议经 env `GEELATO_MAIL_KEK` 注入；未配置时应用正常启动，凭据读写（账户创建/同步/发信）fail-fast |
| `geelato.mail.auto-init-tables` | true | 启动时幂等建表 |
| `geelato.mail.sync.enabled` | false | 定时同步总开关（60s 扫描，按账号间隔增量拉取） |
| `geelato.upload.root-directory` | /upload | 附件落盘根目录 |

账户级定时同步：`PATCH /api/mail/accounts/{id}` 设 `syncEnabled` / `syncIntervalMinutes`
（默认间隔 5 分钟），仅在总开关开启后生效。

## 表落位与 catalog

实体声明 `@Entity(catalog="mail")`（逻辑分组），默认表在**宿主主库**；如需独立邮件库：

1. 创建邮件库并注册数据源（platform_dev_db_connect / 动态数据源）；
2. 配置 `geelato.datasource.dynamic.catalog-mapping.mail=<connectId>`；
3. 重启后 initializer 在新库自动建表。

## API

`/api/mail/*`（约 20 组端点），与 fms 原路径完全一致，详见
`geelato-enterprise/document/geelato-mail-邮件模块合并方案-2026-08-31.md`。

## 测试

- 单元/控制器测试：`mvn test`（无需外部依赖）；
- GreenMail 全链路集成测试（真实 IMAP/SMTP 协议）：
  `mvn test -Dtest=MailGreenMailIntegrationTest -Dgeelato.mail.greenmail.it=1`，
  需本机 scratch MySQL（空库即可，表自动创建）。
