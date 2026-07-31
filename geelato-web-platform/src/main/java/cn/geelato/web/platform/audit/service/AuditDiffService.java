package cn.geelato.web.platform.audit.service;

import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import cn.geelato.web.platform.audit.model.AuditFieldChange;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 审计字段级 diff 构建服务。
 *
 * <p>对比新旧记录，产出 {@link AuditFieldChange} 列表（含中文化的字段标题、状态码翻译、脱敏）。
 * 由 {@link cn.geelato.web.platform.audit.service.AuditBusinessNamer} 提供中文化与翻译，
 * 由 {@link cn.geelato.web.platform.audit.service.AuditMaskService} 提供脱敏。
 */
@Component
public class AuditDiffService {

    private final AuditBusinessNamer namer;
    private final AuditMaskService maskService;
    private final AuditLogProperties properties;

    public AuditDiffService(AuditBusinessNamer namer, AuditMaskService maskService, AuditLogProperties properties) {
        this.namer = namer;
        this.maskService = maskService;
        this.properties = properties;
    }

    /**
     * 计算更新操作的变更明细。
     *
     * @param entityName 实体名
     * @param before     变更前记录（字段名 -> 值）
     * @param after      变更后记录（字段名 -> 值）
     * @return 变化字段列表（按 detailMaxFields 截断）
     */
    public List<AuditFieldChange> diff(String entityName, Map<String, Object> before, Map<String, Object> after) {
        List<AuditFieldChange> changes = new ArrayList<>();
        if (after == null) {
            return changes;
        }
        Collection<String> fields = after.keySet();
        for (String field : fields) {
            // 跳过审计字段（creator/updater/updateAt 等），它们的"变化"无业务意义
            if (isAuditSystemField(field)) {
                continue;
            }
            Object oldVal = before != null ? before.get(field) : null;
            Object newVal = after.get(field);
            if (!equalsValue(oldVal, newVal)) {
                changes.add(buildChange(entityName, field, oldVal, newVal));
            }
            if (changes.size() >= properties.getDetailMaxFields()) {
                break;
            }
        }
        return changes;
    }

    /**
     * 新增操作的明细：所有字段记为"新增"（oldValue=null）。
     */
    public List<AuditFieldChange> insertDetail(String entityName, Map<String, Object> after) {
        List<AuditFieldChange> changes = new ArrayList<>();
        if (after == null) {
            return changes;
        }
        for (String field : after.keySet()) {
            if (isAuditSystemField(field)) {
                continue;
            }
            Object newVal = after.get(field);
            if (newVal == null) {
                continue;
            }
            changes.add(buildChange(entityName, field, null, newVal));
            if (changes.size() >= properties.getDetailMaxFields()) {
                break;
            }
        }
        return changes;
    }

    /**
     * 删除操作的明细：记录被删前的字段快照（newValue=null）。
     */
    public List<AuditFieldChange> deleteDetail(String entityName, Map<String, Object> before) {
        List<AuditFieldChange> changes = new ArrayList<>();
        if (before == null) {
            return changes;
        }
        for (String field : before.keySet()) {
            if (isAuditSystemField(field)) {
                continue;
            }
            Object oldVal = before.get(field);
            if (oldVal == null) {
                continue;
            }
            changes.add(buildChange(entityName, field, oldVal, null));
            if (changes.size() >= properties.getDetailMaxFields()) {
                break;
            }
        }
        return changes;
    }

    private AuditFieldChange buildChange(String entityName, String field, Object oldVal, Object newVal) {
        boolean sensitive = maskService.isSensitive(field) || namer.isEncrypted(entityName, field);
        AuditFieldChange c = new AuditFieldChange();
        c.setField(field);
        c.setTitle(namer.fieldTitle(entityName, field));
        c.setOldValue(sensitive ? maskService.mask(field, oldVal) : oldVal);
        c.setNewValue(sensitive ? maskService.mask(field, newVal) : newVal);
        c.setOldDisplay(StringUtils.hasText(entityName) ? namer.displayValue(entityName, field, oldVal) : oldVal);
        c.setNewDisplay(StringUtils.hasText(entityName) ? namer.displayValue(entityName, field, newVal) : newVal);
        c.setSensitive(sensitive);
        return c;
    }

    /** 判断是否为审计/系统字段（变化不记明细）。 */
    private boolean isAuditSystemField(String field) {
        if (!StringUtils.hasText(field)) {
            return true;
        }
        return switch (field) {
            case "id", "creator", "creatorName", "createAt",
                 "updater", "updaterName", "updateAt", "deleteAt",
                 "delStatus", "tenantCode", "buId", "deptId" -> true;
            default -> false;
        };
    }

    private boolean equalsValue(Object a, Object b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.toString().equals(b.toString());
    }
}
