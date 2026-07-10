## Liquibase 模板（仅示例）

本目录仅提供可复用的目录结构与最小可运行的 changelog 模板，用于后续把测试环境的 schema/基线数据“固化”为可回放的迁移脚本。

### 推荐流程（从 test/staging 生成基线）
1. 在可访问 test/staging 数据库的环境安装 Liquibase CLI
2. 生成 changelog（示例）
   - `liquibase --url=... --username=... --password=... generateChangeLog --changelog-file=db/changelog/db.changelog-master.yaml`
3. 将生成内容拆分到 `db/changelog/changes/` 下，按业务域分文件
4. 在 Testcontainers 启动 MySQL 后，执行该 changelog 初始化容器数据库

### 约定
- 不提交包含敏感数据的 changelog 或 dump 文件
- 如需要基线数据，建议仅保留“必需字典/配置”等最小集合

