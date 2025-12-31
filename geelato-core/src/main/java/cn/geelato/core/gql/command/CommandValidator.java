package cn.geelato.core.gql.command;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.entity.TableForeign;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author geemeta
 */
public class CommandValidator {
    private final MetaManager metaManager = MetaManager.singleInstance();
    private EntityMeta entityMeta = null;
    private String validateEntityName = null;
    private final StringBuilder message = new StringBuilder();

    public boolean validateEntity(String entityName) {
        this.validateEntityName = entityName;
        if (!metaManager.containsEntity(entityName)) {
            message.append("不存在该实体。");
            return false;
        }
        this.entityMeta = metaManager.getByEntityName(entityName);
        return true;
    }

    public void validateField(String field, String fieldDescription) {
        Assert.notNull(entityMeta, "需先validateEntity，确保已有实体信息，才能进一步验证字段。");
        Matcher m = Pattern.compile("^ref\\(\\s*([^)]+)\\s*\\)$").matcher(field);
        if (m.matches()) {
            String inner = m.group(1).trim();
            String main = inner;
            int idx = inner.indexOf("->");
            if (idx >= 0) {
                main = inner.substring(0, idx).trim();
            }
            if (!entityMeta.containsField(main)) {
                message.append("[");
                message.append(fieldDescription);
                message.append("]");
                message.append("不存在");
                message.append(main);
                message.append("；");
                return;
            }
            String col = entityMeta.getColumnName(main);
            boolean isForeign = false;
            if (entityMeta.getTableForeigns() != null) {
                for (TableForeign tf : entityMeta.getTableForeigns()) {
                    if (tf.getEnableStatus() == 1 && col.equalsIgnoreCase(tf.getMainTableCol())) {
                        isForeign = true;
                        break;
                    }
                }
            }
            if (!isForeign) {
                message.append("[");
                message.append(fieldDescription);
                message.append("]");
                message.append(main);
                message.append("不是外键；");
                return;
            }
            return;
        }
        if (!entityMeta.containsField(field)&&!"*".equals(field)&&!"forceId".equals(field)) {
            message.append("[");
            message.append(fieldDescription);
            message.append("]");
            message.append("不存在");
            message.append(field);
            message.append("；");
        }
    }

    public void validateField(String[] fields, String fieldDescription) {
        Assert.notNull(fields, "待验证的字段数组不能为空。");
        for (String field : fields) {
            validateField(field, fieldDescription);
        }
    }

    public boolean isSuccess() {
        return message.isEmpty();
    }

    public String getMessage() {
        if (!isSuccess() && validateEntityName != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("验证实体");
            sb.append(validateEntityName);
            sb.append("：");
            sb.append(message.toString());
            return sb.toString();
        }
        return message.toString();
    }

    public void appendMessage(String message) {
        this.message.append(message);
    }

    public String getPK() {
        return entityMeta.getId().getFieldName();
    }

    public String getColumnName(String fieldName) {
        if (entityMeta.containsField(fieldName)) {
            return entityMeta.getFieldMeta(fieldName).getColumnName();
        } else {
            return fieldName;
        }
    }

    /**
     * 从fields中查询主健
     *
     */
    public boolean hasPK(String[] fields) {
        String name = entityMeta.getId().getFieldName();
        return Arrays.asList(fields).contains(name);
    }

    /**
     * 实体是否有公共字段，如update_at
     *
     */
    public boolean hasKeyField(String field) {
        return entityMeta.containsField(field)&&!"*".equals(field);
    }
}
