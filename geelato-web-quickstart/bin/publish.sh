#!/bin/bash
# 已废弃：geelato-web-quickstart 是可执行应用（spring-boot fat jar），且依赖企业版 SNAPSHOT
# （geelato-auth-starter / geelato-actuator / geelato-schedule-starter 等，未发布到 Maven Central），
# 不适合作为库发布到公有仓库。旧 OSSRH 发布脚本也已失效。
#
# 如需发布 community 库到 Central Portal，请用：
#   geelato-community/bin/publish-central.sh <version>
echo "----------------------------------------------------------------"
echo " 本脚本已废弃：geelato-web-quickstart 不发布到 Maven Central。"
echo " 库发布请用： geelato-community/bin/publish-central.sh <version>"
echo "----------------------------------------------------------------"
exit 1
