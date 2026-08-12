# =============================================================================
# 平台站内信（通知中心）curl 测试脚本（PowerShell 版）
# 用法：
#   1. 修改下方配置区的 $BaseUrl / 各 $Token / 各 $UserId
#   2. pwsh -File notification-test.ps1              # 跑全部用例
#   3. pwsh -File notification-test.ps1 case1 case5  # 跑指定用例
# =============================================================================
$ErrorActionPreference = "Continue"

# ============== 配置区（按实际环境修改） ==============
$BaseUrl = "http://localhost:8082"          # community 后端地址

# 发送方（管理员）
$SenderToken = "Bearer <替换为发送方token>"

# 收件方 A
$UserAId = "3751618121485025315"            # 替换为 A 的 userId
$UserAToken = "Bearer <替换为A的token>"

# 收件方 B
$UserBId = "<替换为B的userId>"
$UserBToken = "Bearer <替换为B的token>"
# =====================================================

function Write-Pass($m) { Write-Host "[PASS] $m" -ForegroundColor Green }
function Write-Fail($m) { Write-Host "[FAIL] $m" -ForegroundColor Red }
function Write-Case($m) { Write-Host "[CASE] $m" -ForegroundColor Yellow }
function Write-Hr { Write-Host "────────────────────────────────────────────────" }

# 通用 POST
function Api-Post($path, $token, $body) {
  $headers = @{ "Authorization" = $token; "Content-Type" = "application/json" }
  try {
    return Invoke-RestMethod -Uri "$BaseUrl/api$path" -Method Post -Headers $headers -Body $body
  } catch {
    return @{ code = -1; msg = $_.Exception.Message }
  }
}
function Api-Get($path, $token) {
  $headers = @{ "Authorization" = $token }
  try {
    return Invoke-RestMethod -Uri "$BaseUrl/api$path" -Method Get -Headers $headers
  } catch {
    return @{ code = -1; msg = $_.Exception.Message }
  }
}

# 用例 1：发送单条站内信
function case1 {
  Write-Case "用例1：发送单条站内信"
  Write-Hr
  $body = @{
    recipients = @($UserAId)
    title = "合同 HT-001 待审批"
    content = "合同 HT-001 已提交，请尽快审批。"
    bizType = "contract"; bizId = "HT-001"
    actionUrl = "/contract/approve?id=HT-001"
    senderType = "user"
  } | ConvertTo-Json -Compress
  $resp = Api-Post "/notification/send" $SenderToken $body
  Write-Host "响应：$($resp | ConvertTo-Json -Compress)"
  if ($resp.code -eq 20000) { Write-Pass "发送成功，notificationId=$($resp.data)" }
  else { Write-Fail "发送失败" }
}

# 用例 2：实时推送
function case2 {
  Write-Case "用例2：实时推送（请先在前端用 A 登录观察铃铛）"
  Write-Hr
  $body = @{
    recipients = @($UserAId)
    title = "实时推送测试 $(Get-Date -Format 'HH:mm:ss')"
    content = "不刷新页面就看到，说明 SSE 正常。"
    senderType = "system"; senderName = "系统"
  } | ConvertTo-Json -Compress
  $resp = Api-Post "/notification/send" $SenderToken $body
  if ($resp.code -eq 20000) { Write-Pass "已发送，去 A 的浏览器看铃铛角标 3 秒内是否 +1" }
  else { Write-Fail "发送失败" }
}

# 用例 3：多人独立已读
function case3 {
  Write-Case "用例3：多人独立已读（发给 A、B，仅 A 标已读）"
  Write-Hr
  $body = @{
    recipients = @($UserAId, $UserBId)
    title = "系统维护通知"; content = "周六 02:00-04:00 维护"
    senderType = "system"; senderName = "系统"
  } | ConvertTo-Json -Compress
  $resp = Api-Post "/notification/send" $SenderToken $body
  $nid = $resp.data
  Write-Host "发送 id=$nid，等 3 秒..."; Start-Sleep 3
  # 查 A 收件箱取 nuId
  $inbox = Api-Post "/notification/pageQuery" $UserAToken (@{pageNum=1;pageSize=10;orderBy="create_at DESC"} | ConvertTo-Json -Compress)
  $nuId = ($inbox.data | Where-Object { $_.notificationId -eq $nid } | Select-Object -First 1).id
  if (-not $nuId) { Write-Fail "未在 A 收件箱找到该通知"; return }
  $rr = Api-Post "/notification/read/$nuId" $UserAToken ""
  Write-Host "A 标记已读：$($rr | ConvertTo-Json -Compress)"
  if ($rr.data -eq $true) { Write-Pass "A 已读。SQL 验证 B 仍为 0：SELECT user_id,read_status FROM platform_notification_user WHERE notification_id='$nid';" }
  else { Write-Fail "标记失败" }
}

# 用例 4：收件箱分页 + 越权校验
function case4 {
  Write-Case "用例4：收件箱分页查询（A 的未读）"
  Write-Hr
  $body = @{pageNum=1;pageSize=10;orderBy="create_at DESC";readStatus=0} | ConvertTo-Json -Compress
  $resp = Api-Post "/notification/pageQuery" $UserAToken $body
  Write-Host "未读总数 total=$($resp.total)"
  if ($resp.code -eq 20000) {
    Write-Pass "查询成功"
    # 越权校验
    $body2 = @{pageNum=1;pageSize=10;userId=$UserBId} | ConvertTo-Json -Compress
    $resp2 = Api-Post "/notification/pageQuery" $UserAToken $body2
    Write-Host "伪造 userId=B 的 total=$($resp2.total)（应与 A 一致，未被篡改）"
  } else { Write-Fail "查询失败" }
}

# 用例 5：未读数
function case5 {
  Write-Case "用例5：未读数"
  Write-Hr
  $resp = Api-Get "/notification/unread-count" $UserAToken
  Write-Host "响应：$($resp | ConvertTo-Json -Compress)"
  if ($resp.code -eq 20000) { Write-Pass "未读数 = $($resp.data)" } else { Write-Fail "失败" }
}

# 用例 6：全部已读
function case6 {
  Write-Case "用例6：全部已读"
  Write-Hr
  $resp = Api-Post "/notification/read-all" $UserAToken ""
  Write-Host "标记条数：$($resp.data)"
  $r2 = Api-Get "/notification/unread-count" $UserAToken
  Write-Host "复查未读数 = $($r2.data)（应为 0）"
  Write-Pass "完成"
}

# 用例 7：带 actionUrl 跳转
function case7 {
  Write-Case "用例7：发送带 actionUrl 的通知（前端点击验证跳转）"
  Write-Hr
  $body = @{
    recipients = @($UserAId)
    title = "工单 WO-002 已分配给您"; content = "请前往处理"
    bizType = "workorder"; bizId = "WO-002"
    actionUrl = "/workorder/detail?id=WO-002"
  } | ConvertTo-Json -Compress
  $resp = Api-Post "/notification/send" $SenderToken $body
  if ($resp.code -eq 20000) { Write-Pass "已发送，前端点击应跳 /workorder/detail?id=WO-002" } else { Write-Fail "失败" }
}

# 用例 8：业务幂等
function case8 {
  Write-Case "用例8：业务幂等（同 bizType+bizId 发两次）"
  Write-Hr
  $body = @{recipients=@($UserAId);title="幂等测试";content="x";bizType="idem-test";bizId="IDEM-001"} | ConvertTo-Json -Compress
  $r1 = Api-Post "/notification/send" $SenderToken $body
  $r2 = Api-Post "/notification/send" $SenderToken $body
  Write-Host "第一次 id=$($r1.data)；第二次 id=$($r2.data)"
  if ($r1.data -eq $r2.data -and $r1.data) { Write-Pass "幂等生效" } else { Write-Fail "幂等可能未生效" }
}

# 用例 9：撤回
function case9 {
  Write-Case "用例9：撤回通知"
  Write-Hr
  $resp = Api-Post "/notification/send" $SenderToken (@{recipients=@($UserAId);title="待撤回";content="x";bizType="recall-test";bizId="RC-001"} | ConvertTo-Json -Compress)
  $nid = $resp.data
  Write-Host "发送 id=$nid，等 3 秒..."; Start-Sleep 3
  $rr = Api-Post "/notification/recall/$nid" $SenderToken ""
  Write-Host "撤回：$($rr | ConvertTo-Json -Compress)"
  if ($rr.data -eq $true) { Write-Pass "撤回成功，SQL: SELECT del_status FROM platform_notification WHERE id='$nid';（应为1）" } else { Write-Fail "失败" }
}

# 用例 10：归属校验
function case10 {
  Write-Case "用例10：归属校验（A 标记 B 的通知应失败）"
  Write-Hr
  $resp = Api-Post "/notification/send" $SenderToken (@{recipients=@($UserBId);title="给B的";content="x";bizType="own-test";bizId="OWN-001"} | ConvertTo-Json -Compress)
  $nid = $resp.data
  Write-Host "给 B 发送 id=$nid，等 3 秒..."; Start-Sleep 3
  $binbox = Api-Post "/notification/pageQuery" $UserBToken (@{pageNum=1;pageSize=10;orderBy="create_at DESC"} | ConvertTo-Json -Compress)
  $bNuId = ($binbox.data | Where-Object { $_.notificationId -eq $nid } | Select-Object -First 1).id
  if (-not $bNuId) { Write-Fail "未找到 B 的通知"; return }
  $rr = Api-Post "/notification/read/$bNuId" $UserAToken ""
  Write-Host "A 标记 B 通知：$($rr | ConvertTo-Json -Compress)"
  if ($rr.data -eq $false) { Write-Pass "归属校验生效（返回 false）" } else { Write-Fail "校验可能失效" }
}

# ============== 主流程 ==============
$allCases = "case1","case2","case3","case4","case5","case6","case7","case8","case9","case10"
$run = if ($args.Count -gt 0) { $args } else { $allCases }

Write-Host "====== 站内信 curl 测试开始 ======" -ForegroundColor Yellow
Write-Host "目标：$BaseUrl"
Write-Host "收件方A：$UserAId   收件方B：$UserBId"
Write-Host ""

foreach ($c in $run) {
  if (Get-Command $c -ErrorAction SilentlyContinue) {
    & $c
    Write-Host ""
  } else {
    Write-Fail "未知用例：$c（可选：$($allCases -join ' ')）"
  }
}
Write-Host "====== 测试结束 ======" -ForegroundColor Yellow
Write-Host "清理：DELETE FROM platform_notification WHERE biz_type IN ('idem-test','recall-test','own-test')"
