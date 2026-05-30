# 测试规范

## Maven 执行约定

- **默认（不加 Profile）**：仅执行 `*Test`，不执行 `*IT`。常用命令：`mvn test`（或在 CI 中使用 `mvn verify`）。
- **-Pit**：执行 `*IT`（集成测试）。建议命令：`mvn verify -Pit`（会先跑 `*Test`，再跑 `*IT`）。
- **-Pcoverage**：开启 JaCoCo（注入 agent、生成报告并执行覆盖率检查）。建议命令：`mvn verify -Pcoverage`；如需包含 IT：`mvn verify -Pit -Pcoverage`。
- **jacoco.check.* 覆写**：通过命令行 `-D...` 或在模块 `pom.xml` 的 `<properties>` 中覆写阈值/阻断开关，例如：

```bash
mvn verify -Pcoverage ^
  -Djacoco.check.line.minimum=0.80 ^
  -Djacoco.check.branch.minimum=0.70 ^
  -Djacoco.check.haltOnFailure=true
```
