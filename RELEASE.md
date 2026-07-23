# geelato community 发布到 Maven Central 操作文档

本文档说明如何把 geelato community 核心库发布到 **Sonatype Central Portal**（即 Maven Central 公有仓库）。

> ⚠️ **重要背景**：旧 OSSRH（`s01.oss.sonatype.org`）已于 **2025-06-30 停服**，所有命名空间已迁到新的 [Central Portal](https://central.sonatype.com)。本项目原 `distributionManagement` 指向旧 OSSRH，**已全部失效**，本次改造已迁移到 `central-publishing-maven-plugin`。

---

## 1. 发布范围

| 分组 | 模块 | groupId:artifactId | 是否纳入发布 |
|------|------|--------------------|--------------|
| 父/BOM | 父 POM | `cn.geelato:geelato-parent` | ✅ |
| 父/BOM | 框架 BOM | `cn.geelato:geelato-framework-bom` | ✅ |
| 基础库 | 语言包 | `cn.geelato:geelato-lang` | ✅ |
| 基础库 | 工具包 | `cn.geelato:geelato-utils` | ✅ |
| 基础库 | 安全包 | `cn.geelato:geelato-security` | ✅ |
| 基础库 | 核心 | `cn.geelato:geelato-core` | ✅ |
| 基础库 | 元数据 | `cn.geelato:geelato-meta` | ✅ |
| 基础库 | ORM | `cn.geelato:geelato-orm` | ✅ |
| 基础库 | Web 公共 | `cn.geelato:geelato-web-common` | ✅ |
| 基础库 | 统一 Starter | `cn.geelato:geelato-framework-starter` | ✅ |
| 应用 | web-quickstart | `cn.geelato:geelato-web-quickstart` | ❌ 可执行应用，依赖企业版 SNAPSHOT |
| 应用 | mql-test | `cn.geelato:geelato-mql-test` | ❌ 测试/调试工具链 |

**CI（GitHub Actions）发布**：上述 10 个核心库（全部在本仓库内，依赖均在公网，自洽）。

**上层模块**（`geelato-web-platform` / `geelato-web-runtime` / `geelato-app-scaffold-starter`）依赖不在本仓库的 `geelato-package` 与 `plugin-all`，**不纳入 CI**；如需发布可用本地脚本（见第 5 节）。

---

## 2. 一次性前置准备

下面三件事只需做一次。完成后再也不用重复。

### 2.1 确认命名空间已验证
- 登录 https://central.sonatype.com 。
- 在 *Namespaces* 里确认 `cn.geelato` 状态为 **Verified**。（OSSRH 迁移过来的命名空间会自动 Verified。）
- 若未验证，按页面提示用 DNS TXT 记录或 GitHub 仓库验证。

### 2.2 生成 Central Portal User Token
Maven 不能用网页登录密码，必须用 **User Token**：

1. 登录 https://central.sonatype.com 。
2. 右上角头像 → **Account** → **User Token** → **Generate**（或 Access User Token）。
3. 会得到一对：
   - **用户名**：形如 `usr_xxxxxxxx`
   - **密码**：一长串 token（只显示一次，务必保存）
4. 这对凭据填入 settings.xml 的 `<server id="central">`（本地）或 GitHub Secrets（CI）。

### 2.3 生成并发布 GPG 签名密钥
Maven Central 要求所有产物用 GPG 签名。

```bash
# 1) 生成密钥（主密钥，RSA 4096）
gpg --full-generate-key
#    选择 (1) RSA and RSA；bits 4096；过期时间自定；姓名/邮箱填发布者；设置 passphrase

# 2) 查看密钥 ID（KEY_ID 形如 ABCDEF1234567890 或完整指纹）
gpg --list-secret-keys --keyid-format=long

# 3) 导出私钥（给 CI 用，保存为 GitHub Secret GPG_PRIVATE_KEY）
gpg --export-secret-keys --armor <KEY_ID> > private-key.asc

# 4) 把公钥上传到公钥服务器（Central 校验签名时去这里取公钥）
gpg --keyserver hkps://keyserver.ubuntu.com --send-keys <KEY_ID>
gpg --keyserver hkps://keys.openpgp.org --send-keys <KEY_ID>
#    验证：浏览器打开 https://keyserver.ubuntu.com 搜索你的 KEY_ID 能搜到
```

> 多个公钥服务器都上传一遍更稳妥。上传后等几分钟再发布。

---

## 3. 配置 GitHub Secrets（CI 自动发布用）

在本仓库（`geelato-community-backend`）的 **Settings → Secrets and variables → Actions → New repository secret** 添加以下 5 个：

| Secret 名 | 值 | 来源 |
|-----------|----|----|
| `CENTRAL_USERNAME` | `usr_xxxxxxxx` | 第 2.2 节 User Token 用户名 |
| `CENTRAL_PASSWORD` | token 长串 | 第 2.2 节 User Token 密码 |
| `GPG_PRIVATE_KEY` | `private-key.asc` 的全部内容 | 第 2.3 节导出的私钥 |
| `GPG_PASSPHRASE` | 生成密钥时设的口令 | 第 2.3 节 |
| `GPG_KEYNAME` | 密钥 ID（如 `ABCDEF1234567890`） | 第 2.3 节 `gpg --list-secret-keys` |

> 私钥内容含多行，直接整段粘贴进 Secret 即可。

---

## 4. 自动发布流程（CI，推荐）

工作流文件：`.github/workflows/release-central.yml`

**触发方式**：推送形如 `maven-central-v*` 的 tag。

```bash
# 1) 确认代码已合并到主分支、本地工作区干净
# 2) 打 tag（版本号必须是非 SNAPSHOT 的正式版本）
git tag maven-central-v1.0.0
git push origin maven-central-v1.0.0

# 3) 去 GitHub 仓库 Actions 页面看 release-central 流程
#    成功后登录 https://central.sonatype.com 查看发布状态
```

**tag 命名规则**：`maven-central-v` + 版本号。例如：
- `maven-central-v1.0.0`
- `maven-central-v1.0.1`
- `maven-central-v1.1.0`

> ⚠️ 不要用 `v1.0.0`，那个前缀已被另一个 workflow（Docker release）占用。

**CI 做了什么**：
1. 解析 tag 得到版本号（`maven-central-v1.0.0` → `1.0.0`）
2. 导入 GPG 私钥
3. 注入 Central Portal token 到 settings.xml
4. `versions:set` 把模块版本从 `1.0.0-SNAPSHOT` 改为 `1.0.0`
5. 安装 parent POM 到本地仓库（子模块解析需要）
6. `mvn deploy -P release`：构建 + 签名 + 上传到 Central Portal + 自动发布

---

## 5. 本地发布流程（备用 / 发布上层模块）

工作目录需包含完整的 `geelato-enterprise` 树（因为上层模块依赖 `geelato-package`、`plugin-all` 等兄弟项目）。

### 5.1 配置本地 settings.xml
复制模板并填入 token：
```bash
cd geelato-community/bin
cp release-settings.xml settings.xml
# 编辑 settings.xml，把两个 REPLACE_WITH_* 占位换成第 2.2 节的 User Token
```

### 5.2 确认 GPG 可用
```bash
gpg --list-secret-keys    # 能看到你的签名密钥
```

### 5.3 一键发布
```bash
# Windows
geelato-community\bin\publish-central.bat 1.0.0 ..\bin\settings.xml

# Linux/macOS
geelato-community/bin/publish-central.sh 1.0.0 ../bin/settings.xml
```

脚本会：
1. 把所有相关模块版本临时设为 `1.0.0`
2. 按依赖顺序逐模块 `mvn clean deploy -P release`
3. 结束后自动还原为 SNAPSHOT

> 本地脚本会发布**全部**模块（含 `web-platform` / `web-runtime` / `app-scaffold-starter` / `geelato-package` / `plugin-all`）。CI 只发布 10 个核心库。
> 建议在干净的 git 工作区运行；脚本结束后用 `git checkout -- */pom.xml` 复核版本已还原。

---

## 6. 验证发布

发布成功后（Portal 状态变为 **Published**）：

- **Portal 查看**：https://central.sonatype.com → *Publishing* 或搜索 `cn.geelato`
- **公网检索**（约 10-30 分钟后同步）：
  ```
  https://repo1.maven.org/maven2/cn/geelato/geelato-parent/
  https://repo1.maven.org/maven2/cn/geelato/geelato-core/
  ```
- 每个模块应有：`*.jar`、`*-sources.jar`、`*-javadoc.jar`、以及对应的 `*.asc` 签名文件、`*.pom`。

**消费验证**（新建一个测试项目）：
```xml
<dependency>
    <groupId>cn.geelato</groupId>
    <artifactId>geelato-core</artifactId>
    <version>1.0.0</version>
</dependency>
```
能从 Maven Central 拉取即发布成功。

---

## 7. 常见问题 / 排错

### Q1：CI 报 `Invalid signature` 或 `gpg: no default secret key`
- 确认 `GPG_PRIVATE_KEY` Secret 是 `gpg --export-secret-keys --armor <KEY_ID>` 的完整输出（含 `-----BEGIN PGP PRIVATE KEY BLOCK-----` 头尾）。
- 确认 `GPG_PASSPHRASE` 正确。
- 确认 `GPG_KEYNAME` 与密钥 ID 一致。

### Q2：CI 报 `Central validation failed: missing signature` 或签名校验不过
- 公钥没上传到 keyserver。重新执行第 2.3 节第 4 步，把公钥发到 `keyserver.ubuntu.com` 和 `keys.openpgp.org`，等几分钟再重试。
- 在 Portal 的失败详情里会列出具体哪个公钥找不到。

### Q3：CI 报 `dependencies.dependency.version ... must be a valid version`
- 子模块的 `<parent>` 用了空 `<relativePath/>`，会从本地仓库解析 parent。
- **必须先 install parent**，CI workflow 已含此步骤；本地手动发布时先执行：
  ```bash
  cd geelato-community/geelato-parent && mvn install -DskipTests
  ```

### Q4：CI 报 namespace 未验证 / `Namespace cn.geelato not verified`
- 回到第 2.1 节完成命名空间验证。

### Q5：`versions:set` 后想还原源码
```bash
cd geelato-community
git checkout -- "**/pom.xml"
```

### Q6：上传成功但 Portal 一直 pending
- Portal 校验需要时间，通常几分钟。可在 Portal 手动点 *Publish* 或 *Drop*。
- 若配置了 `<autoPublish>true</autoPublish>`（本仓库已配），会自动发布。

### Q7：Javadoc 构建失败
- 本仓库已配 `failOnError=false` + `doclint=none`，容忍 Javadoc 警告，不应阻断发布。
- 若仍失败，检查 JDK 版本是否为 17（`java.version` 属性）。

### Q8：发布上层模块时报找不到 `plugin-all` / `geelato-package`
- 这两个模块在 `geelato-enterprise` 的兄弟项目里，不在 community 仓库。
- 本地发布需在完整 `geelato-enterprise` 树下用 `publish-central.bat`（见第 5 节），它会处理兄弟项目的版本与依赖。

---

## 8. 安全提示

- 仓库里 `maven/setting.xml` 含**明文**的旧 OSSRH 凭证（`fwrg14`...）与阿里云 token，已随 OSSRH 一并废弃。建议：
  1. 在 [Central Portal](https://central.sonatype.com) 轮换（重新生成）User Token。
  2. 把含明文凭证的 `setting.xml` 从版本库移除，改用环境变量或 CI Secrets 注入。
- GitHub Secrets 本身是加密存储，不会泄露；但本地 `settings.xml` 填好真实 token 后**不要提交到 git**（模板 `release-settings.xml` 仅含占位符，可提交）。

---

## 9. 相关文件清单

| 文件 | 作用 |
|------|------|
| `geelato-parent/pom.xml` | 父 POM：`release` profile（central-publishing / source / javadoc / gpg 插件）、license、scm |
| `.github/workflows/release-central.yml` | CI 自动发布工作流（tag `maven-central-v*` 触发） |
| `bin/publish-central.bat` | 本地一键发布脚本（Windows） |
| `bin/publish-central.sh` | 本地一键发布脚本（Linux/macOS） |
| `bin/release-settings.xml` | 本地 settings.xml 模板（填 token 后用） |
| `geelato-parent/bin/publish.sh` | 已废弃（旧 OSSRH 脚本，保留为提示） |
| `geelato-web-quickstart/bin/publish.sh` | 已废弃（quickstart 不上 Central） |
| `RELEASE.md` | 本文档 |
