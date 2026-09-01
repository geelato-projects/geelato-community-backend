package cn.geelato.mail.service;

import cn.geelato.mail.entity.MailAccount;
import cn.geelato.mail.entity.MailMessage;
import jakarta.mail.MessagingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 邮件同步核心服务：IMAP 收件箱拉取 → UID 去重 → 落库 → 收信过滤器钩子 → 回写同步状态。
 *
 * 手动同步（POST /api/mail/sync）与定时同步（{@code MailSyncScheduleTask}）共用本流程，
 * 区别仅在触发方式与错误呈现（控制器转 ApiResult，定时任务记日志）。
 *
 * 会话上下文：落库审计字段取 SessionCtx 当前用户，定时任务场景由
 * {@code MailSyncScheduleTask} 以账号归属人身份（SecurityContextRunnable）包裹调用。
 */
@Slf4j
@Service
public class MailSyncService {

    @Autowired
    private MailAccountService accountService;

    @Autowired
    private MailProtocolService protocolService;

    @Autowired
    private MailMessageService messageService;

    @Autowired
    private MailFilterService filterService;

    /**
     * 同步单账户收件箱。
     *
     * @param account 已解密前的账户实体（须为归属人上下文）
     * @param password 解密后的邮箱密码/授权码
     * @return synced=新落库封数，total=本次 IMAP 拉取封数
     * @throws MessagingException IMAP 连接/收信失败（调用方负责回写 failed 状态）
     */
    public SyncResult syncAccount(MailAccount account, String password) throws MessagingException {
        List<MailProtocolService.ParsedMail> fetched = protocolService.fetchInbox(account, password);
        Set<String> existingUids = messageService.listExistingUids(account.getId());
        int synced = 0;
        List<MailMessage> incoming = new ArrayList<>();
        for (MailProtocolService.ParsedMail parsed : fetched) {
            if (parsed.getImapUid() != null && existingUids.contains(parsed.getImapUid())) {
                continue;
            }
            incoming.add(messageService.saveIncoming(account, parsed));
            synced++;
        }
        // P3-V79 收信过滤器钩子：对本次新落库的收件箱邮件按 sortOrder 升序执行全部启用的过滤器
        // （含 autoReply 动作/假期自动回复真实发送；单条失败仅日志不影响同步主流程）
        filterService.applyToIncoming(incoming, account, password);
        return new SyncResult(synced, fetched.size());
    }

    /** 同步结果（synced=新落库封数，total=本次拉取封数） */
    public record SyncResult(int synced, int total) {
    }
}
