#!/bin/bash
# 已废弃：旧 OSSRH 发布脚本（指向已停服的 s01.oss.sonatype.org，且 -P sonatype-oss-release profile 不存在）。
# 请改用 geelato-community/bin/publish-central.sh 发布到新的 Sonatype Central Portal。
#
# 例如：
#   cd ../../../geelato-community/bin
#   ./publish-central.sh 1.0.0
echo "----------------------------------------------------------------"
echo " 本脚本已废弃（旧 OSSRH 已于 2025-06-30 停服）。"
echo " 请改用： geelato-community/bin/publish-central.sh <version>"
echo " 详见：   geelato-community/RELEASE.md"
echo "----------------------------------------------------------------"
exit 1
