package cn.geelato.ide.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import cn.geelato.ide.entity.IdeSyncLog;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.UUID;

/**
 * IDE 同步与操作审计日志服务。
 * <p>
 * 所有 pull/push/dry-run/CRUD/publish 都通过此服务落审计，
 * 是文件↔DB 同步可追溯的关键。
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeSyncLogService {

    @Autowired
    private BaseService baseService;

    /**
     * 记录一条审计日志（失败不阻断主流程，只 warn）。
     *
     * @param logEntry 待记录的日志（id/create_at 由本方法填充）
     */
    public void record(IdeSyncLog logEntry) {
        try {
            if (Strings.isBlank(logEntry.getId())) {
                logEntry.setId(generateId());
            }
            if (logEntry.getCreateAt() == null) {
                logEntry.setCreateAt(new Date());
            }
            User user = SecurityContext.getCurrentUser();
            if (Strings.isBlank(logEntry.getOperator()) && user != null) {
                logEntry.setOperator(user.getUserId());
                if (Strings.isBlank(logEntry.getOperatorName())) {
                    logEntry.setOperatorName(user.getUserName());
                }
            }
            baseService.createModel(logEntry);
        } catch (Exception e) {
            // 审计失败不能阻断主业务，只记 warn
            IdeSyncLogService.log.warn("IDE 同步审计记录失败: action={}, scriptCode={}, result={}",
                    logEntry.getAction(), logEntry.getScriptCode(), logEntry.getResult(), e);
        }
    }

    private String generateId() {
        return String.valueOf(System.currentTimeMillis() * 1000 + (UUID.randomUUID().hashCode() & 0x3FF));
    }
}
