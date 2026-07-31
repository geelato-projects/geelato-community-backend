package cn.geelato.web.platform.audit.service;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.DictDataSource;
import cn.geelato.web.platform.audit.boot.AuditLogProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 审计中文化引擎（可读性核心）。
 *
 * <p>集中产出审计记录所需的所有中文信息：
 * <ul>
 *   <li>{@link #entityTitle} 实体中文名（类级 @Title → EntityMeta.entityTitle）</li>
 *   <li>{@link #fieldTitle} 字段中文名（字段级 @Title → ColumnMeta.title）</li>
 *   <li>{@link #bizNameValue} 业务对象名称（按候选列从记录中取值）</li>
 *   <li>{@link #displayValue} 状态/枚举值的中文翻译（字典 → @Title(description) → 原值）</li>
 * </ul>
 *
 * <p>保证「即使数据完全来自 MQL/低代码，审计记录也是中文可读的」。
 */
@Slf4j
@Component
public class AuditBusinessNamer {

    private final AuditLogProperties properties;
    private final DictDisplayResolver dictResolver;

    public AuditBusinessNamer(AuditLogProperties properties, DictDisplayResolver dictResolver) {
        this.properties = properties;
        this.dictResolver = dictResolver;
    }

    /** 取实体中文名；取不到则回退实体类名。 */
    public String entityTitle(String entityName) {
        EntityMeta em = getEntityMeta(entityName);
        if (em != null && StringUtils.hasText(em.getEntityTitle())) {
            return em.getEntityTitle();
        }
        return entityName;
    }

    /** 取实体对应的表名。 */
    public String tableName(String entityName) {
        EntityMeta em = getEntityMeta(entityName);
        return em != null ? em.getTableName() : null;
    }

    /** 取字段中文名（按 Java 字段名）；取不到则回退字段名。 */
    public String fieldTitle(String entityName, String fieldName) {
        FieldMeta fm = getFieldMeta(entityName, fieldName);
        if (fm != null && fm.getColumnMeta() != null && StringUtils.hasText(fm.getColumnMeta().getTitle())) {
            return fm.getColumnMeta().getTitle();
        }
        return fieldName;
    }

    /** 字段是否加密（ColumnMeta.encrypted）。 */
    public boolean isEncrypted(String entityName, String fieldName) {
        FieldMeta fm = getFieldMeta(entityName, fieldName);
        return fm != null && fm.getColumnMeta() != null && fm.getColumnMeta().isEncrypted();
    }

    /**
     * 从记录 Map 中提取业务对象名称（业务编号）。
     * 按 {@code bizNameColumns} 候选列顺序匹配，取第一个非空值；均无则返回 null。
     *
     * @param record        记录数据（字段名 -> 值）
     * @param bizNameColumn 显式指定的业务名列（优先于候选列）
     */
    public String bizNameValue(Map<String, Object> record, String bizNameColumn) {
        if (record == null || record.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(bizNameColumn) && record.containsKey(bizNameColumn)) {
            Object v = record.get(bizNameColumn);
            if (v != null && StringUtils.hasText(v.toString())) {
                return v.toString();
            }
        }
        for (String col : properties.getBizNameColumns()) {
            if (record.containsKey(col)) {
                Object v = record.get(col);
                if (v != null && StringUtils.hasText(v.toString())) {
                    return v.toString();
                }
            }
        }
        return null;
    }

    /**
     * 状态/枚举值翻译为中文展示值。
     * 优先级：① 字典（@DictDataSrc 绑定的 dictCode）② @Title(description="code名称,code名称") 解析 ③ 原值兜底。
     *
     * @param entityName 实体名
     * @param fieldName  字段名
     * @param code       字段原始值（如 "pending"）
     * @return 翻译后的中文；无法翻译则返回 code 本身（code 为空返回空）
     */
    public String displayValue(String entityName, String fieldName, Object code) {
        if (code == null) {
            return null;
        }
        String codeStr = code.toString();
        if (!StringUtils.hasText(codeStr)) {
            return codeStr;
        }
        EntityMeta em = getEntityMeta(entityName);
        if (em != null) {
            // ① 字典翻译
            String dictCode = resolveDictCode(em, fieldName);
            if (StringUtils.hasText(dictCode)) {
                String itemName = dictResolver.resolve(dictCode, codeStr);
                if (StringUtils.hasText(itemName)) {
                    return itemName;
                }
            }
            // ② @Title(description="code名称,code名称") 解析
            String fromDesc = parseDescription(em, fieldName, codeStr);
            if (fromDesc != null) {
                return fromDesc;
            }
        }
        // ③ 原值兜底
        return codeStr;
    }

    /** 从 EntityMeta.dictDataSourceMap 解析字段绑定的 dictCode（group）。 */
    private String resolveDictCode(EntityMeta em, String fieldName) {
        Map<String, DictDataSource> map = em.getDictDataSourceMap();
        if (map == null || map.isEmpty()) {
            return null;
        }
        DictDataSource ds = map.get(fieldName);
        return ds != null ? ds.getGroup() : null;
    }

    /** 解析 @Title(description="pending待处理,confirmed已确认") 格式，返回 code 对应的中文。 */
    private String parseDescription(EntityMeta em, String fieldName, String codeStr) {
        FieldMeta fm = getFieldMeta(em, fieldName);
        if (fm == null || fm.getColumnMeta() == null) {
            return null;
        }
        String desc = fm.getColumnMeta().getDescription();
        if (!StringUtils.hasText(desc)) {
            return null;
        }
        // 格式：code1名称1,code2名称2  或  code1 名称1,code2 名称2（支持空格分隔）
        for (String pair : desc.split(",")) {
            String p = pair.trim();
            if (!StringUtils.hasText(p)) {
                continue;
            }
            int sep = indexOfSeparator(p);
            if (sep <= 0) {
                continue;
            }
            String code = p.substring(0, sep).trim();
            String name = p.substring(sep).trim();
            // 去掉可能的空格或分隔符
            if (name.isEmpty()) {
                continue;
            }
            if (code.equalsIgnoreCase(codeStr)) {
                return name;
            }
        }
        return null;
    }

    /** 找到 code 与名称之间的分隔点（第一个非字母数字下划线字符）。 */
    private int indexOfSeparator(String p) {
        for (int i = 0; i < p.length(); i++) {
            char c = p.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-') {
                return i;
            }
        }
        return -1;
    }

    private EntityMeta getEntityMeta(String entityName) {
        if (!StringUtils.hasText(entityName)) {
            return null;
        }
        try {
            return MetaManager.singleInstance().getByEntityName(entityName);
        } catch (Exception e) {
            log.debug("审计取实体元数据失败 entityName={}", entityName, e);
            return null;
        }
    }

    private FieldMeta getFieldMeta(String entityName, String fieldName) {
        EntityMeta em = getEntityMeta(entityName);
        return getFieldMeta(em, fieldName);
    }

    private FieldMeta getFieldMeta(EntityMeta em, String fieldName) {
        if (em == null || !StringUtils.hasText(fieldName)) {
            return null;
        }
        try {
            return em.getFieldMeta(fieldName);
        } catch (Exception e) {
            return null;
        }
    }

    /** 收集实体所有字段名（用于 diff 遍历）。 */
    public Collection<String> fieldNames(String entityName) {
        EntityMeta em = getEntityMeta(entityName);
        if (em == null || em.getFieldMetas() == null) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (FieldMeta fm : em.getFieldMetas()) {
            names.add(fm.getFieldName());
        }
        return names;
    }
}
