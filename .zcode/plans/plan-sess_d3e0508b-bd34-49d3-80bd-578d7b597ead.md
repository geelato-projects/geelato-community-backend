# 诊断结论

**这不是依赖缺失问题,而是残留的坏字节码。** 完整因果链:

1. `IdEntity` 在 community 中始终存在,今晨 09:54 安装到 .m2 的 `geelato-core-1.0.0-SNAPSHOT.jar` 也包含它——当前 classpath 是健康的。
2. **8月28日 11:36**,geelato-auth-login 曾由 Eclipse/ECJ 系编译器在 `IdEntity` 解析失败的类路径下编译(当时 community 正处于 ORM 大重构中)。ECJ 不让构建失败,而是把编译不过的方法体直接写成 `throw new Error("Unresolved compilation problem: ...")` 烧进字节码。
3. 受污染类共 2 个(auth-core 干净):
   - `geelato-auth-login/target/classes/cn/geelato/auth/service/JsonOAuthClientReader.class`
   - `geelato-auth-login/target/classes/cn/geelato/auth/config/app/AppConfigStoreImpl.class`
4. 11:57 `mvn install` 时这些坏 class 比源码新,被原样打进了 .m2 的 `geelato-auth-login-0.0.1-SNAPSHOT.jar`。
5. 今天 quickstart 启动时(IDE 类路径直挂 authorization 模块的 target/classes),Spring 实例化 `jsonOAuthClientReader`,其构造器无条件抛出内嵌 Error → 启动失败。**只要这两个坏类不重编译,无论怎么修 classpath 都会继续崩。**

预检:authorization 源码不引用 community 已删除的 `CoreErrorCodes`/`ErrorCode`/`LangErrorCodes`,重编译不会被重构卡住。

# 修复步骤

1. **前置(稳妥起见)**:community 全量安装,确保 .m2 中 lang/orm/core 等快照与工作区一致(.m2 的 geelato-lang 还是 8/24 的,落后于未提交的重构):
   ```
   mvn -f D:\geelato\geelato-enterprise\geelato-community\pom.xml install -DskipTests
   ```
2. **核心修复**:clean 重建 authorization,清掉坏 class 并重装 jar:
   ```
   mvn -f D:\geelato\geelato-enterprise\geelato-authorization\pom.xml clean install -DskipTests
   ```
   javac 是硬失败策略——若 authorization 与新快照有真实源码不兼容,构建会显式报错,届时按报错修引用(预检表明大概率直接通过)。
3. **验证产物**:确认两个类不再含内嵌错误:
   ```
   grep -rc "Unresolved compilation" D:\geelato\geelato-enterprise\geelato-authorization\geelato-auth-login\target\classes\cn\geelato\auth\service\JsonOAuthClientReader.class
   grep -rc "Unresolved compilation" D:\geelato\geelato-enterprise\geelato-authorization\geelato-auth-login\target\classes\cn\geelato\auth\config\app\AppConfigStoreImpl.class
   ```
   预期:文件不存在(clean 后重新生成)或计数为 0。
4. **IDE 侧**:刷新/reimport Maven 项目(避免 IDE 继续用旧产物),重启 quickstart,确认 `oidcProtocolController`/`jsonOAuthClientReader` 正常装配、应用完整启动。

不涉及任何源码修改,纯构建产物治理。