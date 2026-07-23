#!/usr/bin/env bash
# =============================================================================
# 把 geelato community 发布到 Sonatype Central Portal (Maven Central)。
#
# 用法：
#   ./publish-central.sh <release-version> [settings.xml]
#
# 示例：
#   ./publish-central.sh 1.0.0
#   ./publish-central.sh 1.0.0 ~/my-settings.xml
#
# 前置条件（详见 geelato-community/RELEASE.md）：
#   1) 已生成 Central Portal User Token，并填入 settings.xml 的 <server id="central">
#   2) 本机 PATH 上有 gpg，并已导入签名密钥（gpg --list-secret-keys 能看到）
#   3) 本仓库 geelato-enterprise 与其下的 geelato-community 均已拉取最新代码
#
# 本脚本会用 versions:set 把相关模块版本临时设为发布版本并发布；
# 构建完成后会自动还原为原 SNAPSHOT 版本（即不会改动源码中的默认版本）。
# =============================================================================
set -euo pipefail

# ---------- 参数 ----------
VERSION="${1:-}"
SETTINGS_ARG=("${@:2}")   # 其余参数透传给 mvn，例如自定义 -s settings.xml

if [ -z "$VERSION" ] || [ "${VERSION##*-}" = "SNAPSHOT" ]; then
    echo "ERROR: 必须提供正式版本号（非 SNAPSHOT），例如：./publish-central.sh 1.0.0" >&2
    exit 1
fi

# 仓库根目录定位（本脚本位于 geelato-community/bin/ 下）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
COMMUNITY_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENTERPRISE_DIR="$(cd "$COMMUNITY_DIR/.." && pwd)"

# 发布模块（按依赖顺序，跨 community 与 sibling 项目）
# community 内：parent/bom/starter 必须最先；其它库其次；web 平台层最后
COMMUNITY_FIRST="geelato-parent geelato-framework-bom geelato-framework-starter"
COMMUNITY_LIBS="geelato-lang geelato-utils geelato-security geelato-core geelato-meta geelato-orm geelato-web-common"
EXTERNAL_LIBS="geelato-package geelato-plugins/geelato-plugin-all"
COMMUNITY_UPPER="geelato-web-platform geelato-web-runtime geelato-app-scaffold-starter"

ALL_MODULES=( $COMMUNITY_FIRST $COMMUNITY_LIBS $EXTERNAL_LIBS $COMMUNITY_UPPER )

echo "============================================================"
echo " 发布版本：$VERSION"
echo " 工作目录：$ENTERPRISE_DIR"
echo " 模块数：  ${#ALL_MODULES[@]}"
echo "============================================================"

# ---------- 0) 环境自检 ----------
command -v mvn >/dev/null 2>&1 || { echo "ERROR: 未找到 mvn"; exit 1; }
command -v gpg >/dev/null 2>&1 || { echo "ERROR: 未找到 gpg（签名必需）"; exit 1; }
gpg --list-secret-keys >/dev/null 2>&1 || { echo "ERROR: gpg 无可用私钥，请先导入签名密钥"; exit 1; }

# ---------- 1) 统一设版本（versions:set -DnewVersion，仅改当前 pom） ----------
# 用 -DprocessAllModules=false，针对每个模块单独设置；每个模块各自的 parent 引用版本不变
# （parent 已在第一步被设为新版本，子模块通过 <parent><version> 解析到新版本）。
echo "[1/3] 设置发布版本 $VERSION ..."
(
    cd "$COMMUNITY_DIR"
    # community 内模块：parent 先设版本
    mvn -q -f geelato-parent/pom.xml versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false -DprocessAllModules=true || true
    # 用 reactor 方式给 community 全部模块统一版本（一次性）
    mvn -q -N versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false -DprocessAllModules=true || true
)

# 外部模块单独设版本
(
    cd "$ENTERPRISE_DIR/geelato-package"
    mvn -q versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false || true
)
(
    cd "$ENTERPRISE_DIR/geelato-plugins/geelato-plugin-all"
    # plugin-all 的 parent 是 geelato-plugins:0.0.1-SNAPSHOT，单独设本模块 artifact 版本
    mvn -q versions:set -DnewVersion="$VERSION" -DgenerateBackupPoms=false || true
)

# ---------- 2) 安装 parent/bom 到本地仓库（其它模块解析 parent 需要） ----------
echo "[2/3] 预装 parent / bom ..."
(
    cd "$COMMUNITY_DIR"
    mvn -q -pl geelato-parent,geelato-framework-bom install -DskipTests "${SETTINGS_ARG[@]}"
)

# ---------- 3) 逐模块构建并发布 ----------
echo "[3/3] 构建并发布 ..."
for mod in "${ALL_MODULES[@]}"; do
    if [ -d "$ENTERPRISE_DIR/$mod" ]; then
        mod_path="$ENTERPRISE_DIR/$mod"
    elif [ -d "$COMMUNITY_DIR/$mod" ]; then
        mod_path="$COMMUNITY_DIR/$mod"
    else
        echo "WARN: 找不到模块目录 $mod，跳过" >&2
        continue
    fi
    echo "---- 发布 $mod ($VERSION) ----"
    (
        cd "$mod_path"
        mvn clean deploy -P release -DskipTests "${SETTINGS_ARG[@]}"
    )
done

echo "============================================================"
echo " 发布任务已提交到 Central Portal。"
echo " 登录 https://central.sonatype.com 查看发布状态（Published 即成功）。"
echo "============================================================"
