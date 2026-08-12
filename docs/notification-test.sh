#!/usr/bin/env bash
# =============================================================================
# 平台站内信（通知中心）curl 测试脚本
# 用法：
#   1. 修改下方配置区的 BASE_URL / SENDER_TOKEN / USER_A_ID / USER_A_TOKEN / USER_B_ID / USER_B_TOKEN
#   2. bash notification-test.sh           # 跑全部用例
#   3. bash notification-test.sh case1     # 跑指定用例（case1~case10）
#      bash notification-test.sh case1 case2 case5
# =============================================================================
set -uo pipefail

# ============== 配置区（按实际环境修改） ==============
BASE_URL="http://localhost:8082"          # community 后端地址（按实际端口改）

# 发送方（管理员）
SENDER_TOKEN="Bearer <替换为发送方token>"

# 收件方 A
USER_A_ID="3751618121485025315"           # 替换为 A 的 userId
USER_A_TOKEN="Bearer <替换为A的token>"

# 收件方 B（用于多人独立已读测试）
USER_B_ID="<替换为B的userId>"
USER_B_TOKEN="Bearer <替换为B的token>"
# =====================================================

# 颜色输出
G="\033[32m"; R="\033[31m"; Y="\033[33m"; N="\033[0m"
pass() { echo -e "${G}[PASS]${N} $1"; }
fail() { echo -e "${R}[FAIL]${N} $1"; }
info() { echo -e "${Y}[CASE]${N} $1"; }
hr() { echo "────────────────────────────────────────────────"; }

# 检查 token 是否已替换
check_token() {
  if [[ "$1" == *"<"* ]]; then
    fail "Token 未配置，请编辑脚本顶部的配置区"
    exit 1
  fi
}

# 解析 JSON 中的字段（依赖 jq；无 jq 时给出提示）
JQ=$(command -v jq || true)
json_get() {
  local json="$1" key="$2"
  if [[ -n "$JQ" ]]; then echo "$json" | jq -r "$key"; else echo "$json" | grep -o "\"$key\"[^,}]*" | head -1; fi
}

# 保存 send 返回的 notificationId / pageQuery 返回的 notificationUserId，供后续用例使用
LAST_NOTIFICATION_ID=""
LAST_NU_ID_A=""

# -----------------------------------------------------------------------------
# 用例 1：发送单条站内信（核心闭环）
# -----------------------------------------------------------------------------
case1() {
  info "用例1：发送单条站内信"
  check_token "$SENDER_TOKEN"
  hr
  resp=$(curl -s -X POST "$BASE_URL/api/notification/send" \
    -H "Authorization: $SENDER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"recipients\": [\"$USER_A_ID\"],
      \"title\": \"合同 HT-001 待审批\",
      \"content\": \"合同 HT-001 已提交，请尽快审批。\",
      \"bizType\": \"contract\",
      \"bizId\": \"HT-001\",
      \"actionUrl\": \"/contract/approve?id=HT-001\",
      \"senderType\": \"user\"
    }")
  echo "响应：$resp"
  code=$(json_get "$resp" '.code')
  if [[ "$code" == "20000" ]]; then
    LAST_NOTIFICATION_ID=$(json_get "$resp" '.data')
    pass "发送成功，notificationId=$LAST_NOTIFICATION_ID"
    echo "    提示：等待 3 秒后 outbox 调度会 fan-out 到收件箱"
  else
    fail "发送失败（code=$code）"
  fi
}

# -----------------------------------------------------------------------------
# 用例 2：实时推送验证（需配合前端观察）
# -----------------------------------------------------------------------------
case2() {
  info "用例2：实时推送（请先在前端用 A 登录并打开页面，观察铃铛）"
  check_token "$SENDER_TOKEN"
  hr
  resp=$(curl -s -X POST "$BASE_URL/api/notification/send" \
    -H "Authorization: $SENDER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"recipients\": [\"$USER_A_ID\"],
      \"title\": \"实时推送测试 $(date +%H:%M:%S)\",
      \"content\": \"如果你不刷新页面就看到这条，说明 SSE 推送正常。\",
      \"senderType\": \"system\",
      \"senderName\": \"系统\"
    }")
  echo "响应：$resp"
  code=$(json_get "$resp" '.code')
  if [[ "$code" == "20000" ]]; then
    pass "已发送，请到 A 的浏览器观察铃铛角标是否在 3 秒内 +1（无需刷新）"
  else
    fail "发送失败"
  fi
}

# -----------------------------------------------------------------------------
# 用例 3：多人独立已读
# -----------------------------------------------------------------------------
case3() {
  info "用例3：多人独立已读（发给 A 和 B，仅 A 标记已读）"
  check_token "$SENDER_TOKEN"
  hr
  resp=$(curl -s -X POST "$BASE_URL/api/notification/send" \
    -H "Authorization: $SENDER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"recipients\": [\"$USER_A_ID\", \"$USER_B_ID\"],
      \"title\": \"系统维护通知\",
      \"content\": \"系统将于本周六 02:00-04:00 维护。\",
      \"senderType\": \"system\",
      \"senderName\": \"系统\"
    }")
  echo "发送响应：$resp"
  nid=$(json_get "$resp" '.data')
  echo "    等待 3 秒让 fan-out 完成..."; sleep 3
  # 查 A 的收件箱，取 notificationUserId
  inbox=$(curl -s -X POST "$BASE_URL/api/notification/pageQuery" \
    -H "Authorization: $USER_A_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"pageNum\":1,\"pageSize\":10,\"orderBy\":\"create_at DESC\"}")
  nu_id=$(echo "$inbox" | $JQ -r '.data[] | select(.notificationId=="'"$nid"'") | .id' 2>/dev/null || echo "")
  if [[ -z "$nu_id" ]]; then
    fail "未在 A 的收件箱找到该通知，无法继续"
    return
  fi
  # A 标记已读
  rread=$(curl -s -X POST "$BASE_URL/api/notification/read/$nu_id" -H "Authorization: $USER_A_TOKEN")
  echo "A 标记已读响应：$rread"
  ok=$(json_get "$rread" '.data')
  if [[ "$ok" == "true" ]]; then
    pass "A 已标记已读。请用 SQL 验证 B 的 read_status 仍为 0："
    echo "    SELECT user_id, read_status FROM platform_notification_user WHERE notification_id='$nid';"
  else
    fail "A 标记已读失败"
  fi
}

# -----------------------------------------------------------------------------
# 用例 4：收件箱分页查询（含越权校验）
# -----------------------------------------------------------------------------
case4() {
  info "用例4：收件箱分页查询（A 的 token，只查未读）"
  check_token "$USER_A_TOKEN"
  hr
  resp=$(curl -s -X POST "$BASE_URL/api/notification/pageQuery" \
    -H "Authorization: $USER_A_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"pageNum":1,"pageSize":10,"orderBy":"create_at DESC","readStatus":0}')
  echo "响应（截断）：$(echo "$resp" | head -c 400)..."
  total=$(json_get "$resp" '.total')
  if [[ "$total" =~ ^[0-9]+$ ]]; then
    pass "查询成功，未读总数 total=$total"
    LAST_NU_ID_A=$(echo "$resp" | $JQ -r '.data[0].id' 2>/dev/null || echo "")
    echo "    越权校验：下面请求伪造 userId=B，应仍只返回 A 的数据"
    resp2=$(curl -s -X POST "$BASE_URL/api/notification/pageQuery" \
      -H "Authorization: $USER_A_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"pageNum\":1,\"pageSize\":10,\"userId\":\"$USER_B_ID\"}")
    echo "    伪造 userId 响应 total=$(json_get "$resp2" '.total')（应与 A 的一致，未被 B 篡改）"
  else
    fail "查询失败"
  fi
}

# -----------------------------------------------------------------------------
# 用例 5：未读数
# -----------------------------------------------------------------------------
case5() {
  info "用例5：未读数（铃铛角标）"
  check_token "$USER_A_TOKEN"
  hr
  resp=$(curl -s -X GET "$BASE_URL/api/notification/unread-count" -H "Authorization: $USER_A_TOKEN")
  echo "响应：$resp"
  code=$(json_get "$resp" '.code')
  data=$(json_get "$resp" '.data')
  if [[ "$code" == "20000" ]]; then
    pass "未读数 = $data"
  else
    fail "查询失败"
  fi
}

# -----------------------------------------------------------------------------
# 用例 6：全部已读
# -----------------------------------------------------------------------------
case6() {
  info "用例6：全部已读"
  check_token "$USER_A_TOKEN"
  hr
  resp=$(curl -s -X POST "$BASE_URL/api/notification/read-all" -H "Authorization: $USER_A_TOKEN")
  echo "响应：$resp"
  n=$(json_get "$resp" '.data')
  pass "已标记 $n 条为已读"
  echo "    复查未读数："
  r2=$(curl -s -X GET "$BASE_URL/api/notification/unread-count" -H "Authorization: $USER_A_TOKEN")
  echo "    unread-count = $(json_get "$r2" '.data')（应为 0）"
}

# -----------------------------------------------------------------------------
# 用例 7：点击跳转（仅发送，跳转需在前端操作）
# -----------------------------------------------------------------------------
case7() {
  info "用例7：发送带 actionUrl 的通知（跳转需在前端点击验证）"
  check_token "$SENDER_TOKEN"
  hr
  resp=$(curl -s -X POST "$BASE_URL/api/notification/send" \
    -H "Authorization: $SENDER_TOKEN" \
    -H "Content-Type: application/json" \
    -d "{
      \"recipients\": [\"$USER_A_ID\"],
      \"title\": \"工单 WO-002 已分配给您\",
      \"content\": \"请前往处理。\",
      \"bizType\": \"workorder\",
      \"bizId\": \"WO-002\",
      \"actionUrl\": \"/workorder/detail?id=WO-002\"
    }")
  echo "响应：$resp"
  code=$(json_get "$resp" '.code')
  if [[ "$code" == "20000" ]]; then
    pass "已发送，请到前端 A 的收件箱点击该通知，应跳转到 /workorder/detail?id=WO-002"
  else
    fail "发送失败"
  fi
}

# -----------------------------------------------------------------------------
# 用例 8：业务幂等
# -----------------------------------------------------------------------------
case8() {
  info "用例8：业务幂等（同 bizType+bizId 发两次）"
  check_token "$SENDER_TOKEN"
  hr
  body="{\"recipients\":[\"$USER_A_ID\"],\"title\":\"幂等测试\",\"content\":\"x\",\"bizType\":\"idem-test\",\"bizId\":\"IDEM-001\"}"
  r1=$(curl -s -X POST "$BASE_URL/api/notification/send" -H "Authorization: $SENDER_TOKEN" -H "Content-Type: application/json" -d "$body")
  r2=$(curl -s -X POST "$BASE_URL/api/notification/send" -H "Authorization: $SENDER_TOKEN" -H "Content-Type: application/json" -d "$body")
  id1=$(json_get "$r1" '.data'); id2=$(json_get "$r2" '.data')
  echo "第一次 id=$id1；第二次 id=$id2"
  if [[ "$id1" == "$id2" && -n "$id1" ]]; then
    pass "幂等生效：两次返回同一 notificationId"
  else
    fail "幂等可能未生效（两次 id 不同）"
  fi
}

# -----------------------------------------------------------------------------
# 用例 9：撤回通知
# -----------------------------------------------------------------------------
case9() {
  info "用例9：撤回通知"
  check_token "$SENDER_TOKEN"
  hr
  # 先发一条
  resp=$(curl -s -X POST "$BASE_URL/api/notification/send" \
    -H "Authorization: $SENDER_TOKEN" -H "Content-Type: application/json" \
    -d "{\"recipients\":[\"$USER_A_ID\"],\"title\":\"待撤回\",\"content\":\"将被撤回\",\"bizType\":\"recall-test\",\"bizId\":\"RC-001\"}")
  nid=$(json_get "$resp" '.data')
  echo "发送 id=$nid，等待 fan-out..."; sleep 3
  # 撤回
  rr=$(curl -s -X POST "$BASE_URL/api/notification/recall/$nid" -H "Authorization: $SENDER_TOKEN")
  echo "撤回响应：$rr"
  ok=$(json_get "$rr" '.data')
  if [[ "$ok" == "true" ]]; then
    pass "撤回成功。验证 SQL: SELECT del_status FROM platform_notification WHERE id='$nid';（应为 1）"
  else
    fail "撤回失败"
  fi
}

# -----------------------------------------------------------------------------
# 用例 10：归属校验（A 不能标记 B 的通知）
# -----------------------------------------------------------------------------
case10() {
  info "用例10：归属校验（A 的 token 标记 B 的通知，应失败）"
  check_token "$USER_A_TOKEN"; check_token "$SENDER_TOKEN"
  hr
  # 先给 B 发一条
  resp=$(curl -s -X POST "$BASE_URL/api/notification/send" \
    -H "Authorization: $SENDER_TOKEN" -H "Content-Type: application/json" \
    -d "{\"recipients\":[\"$USER_B_ID\"],\"title\":\"给B的\",\"content\":\"x\",\"bizType\":\"own-test\",\"bizId\":\"OWN-001\"}")
  nid=$(json_get "$resp" '.data')
  echo "给 B 发送 id=$nid，等待 fan-out..."; sleep 3
  # 查 B 的 notificationUserId（用 B 的 token）
  binbox=$(curl -s -X POST "$BASE_URL/api/notification/pageQuery" \
    -H "Authorization: $USER_B_TOKEN" -H "Content-Type: application/json" \
    -d '{"pageNum":1,"pageSize":10,"orderBy":"create_at DESC"}')
  b_nu_id=$(echo "$binbox" | $JQ -r '.data[] | select(.notificationId=="'"$nid"'") | .id' 2>/dev/null || echo "")
  if [[ -z "$b_nu_id" ]]; then fail "未找到 B 的通知，跳过"; return; fi
  # 用 A 的 token 试图标记 B 的通知
  rr=$(curl -s -X POST "$BASE_URL/api/notification/read/$b_nu_id" -H "Authorization: $USER_A_TOKEN")
  echo "A 标记 B 的通知响应：$rr"
  ok=$(json_get "$rr" '.data')
  if [[ "$ok" == "false" ]]; then
    pass "归属校验生效：A 无法标记 B 的通知（返回 false）"
  else
    fail "归属校验可能失效（应返回 false）"
  fi
}

# ============== 主流程 ==============
ALL_CASES="case1 case2 case3 case4 case5 case6 case7 case8 case9 case10"
RUN="$ALL_CASES"
if [[ $# -gt 0 ]]; then RUN="$*"; fi

echo -e "\n${Y}====== 站内信 curl 测试开始 ======${N}"
echo "目标：$BASE_URL"
echo "收件方A：$USER_A_ID"
echo "收件方B：$USER_B_ID"
echo

for c in $RUN; do
  if type "$c" &>/dev/null; then
    $c
    echo
  else
    fail "未知用例：$c（可选：$ALL_CASES）"
  fi
done

echo -e "${Y}====== 测试结束 ======${N}"
echo "提示："
echo "  - 涉及 fan-out 的用例已内置 sleep 3 等待调度"
echo "  - 实时推送/跳转类用例需配合前端浏览器观察"
echo "  - 清理测试数据：DELETE FROM platform_notification WHERE biz_type IN ('idem-test','recall-test','own-test')"
