package cn.geelato.web.platform.m.excel.entity;

import cn.geelato.web.platform.m.excel.enums.ExcelColumnTypeRuleEnum;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author diabl
 * @description: 解析规则
 */
@Getter
@Setter
public class BusinessTypeRuleData {
    // 列名
    private String columnName;
    // 类型
    private String type;
    // 规则，正则表达式；字典编码；表格:字段,字段...
    private String rule;
    // 目标字段；替换值
    private String goal;
    // 是否优先于 全局多值处理
    private boolean priority = false;
    // 保留值，规则清洗后为空，TRUE:保留清洗前值;FALSE:已清洗结果为准。默认：FALSE
    private boolean retain = false;
    // 执行次序
    private Integer order;

    private String remark;

    public boolean isRuleTypeDeletes() {
        return ExcelColumnTypeRuleEnum.DELETES.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeReplace() {
        return ExcelColumnTypeRuleEnum.REPLACE.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeUpperCase() {
        return ExcelColumnTypeRuleEnum.UPPERCASE.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeLowerCase() {
        return ExcelColumnTypeRuleEnum.LOWERCASE.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeCheckBox() {
        return ExcelColumnTypeRuleEnum.CHECKBOX.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeDictionary() {
        return ExcelColumnTypeRuleEnum.DICTIONARY.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeQueryGoal() {
        return ExcelColumnTypeRuleEnum.QUERYGOAL.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeQueryRule() {
        return ExcelColumnTypeRuleEnum.QUERYRULE.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeTrim() {
        return ExcelColumnTypeRuleEnum.TRIM.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeExpression() {
        return ExcelColumnTypeRuleEnum.EXPRESSION.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeSym() {
        return ExcelColumnTypeRuleEnum.SYM.name().equalsIgnoreCase(this.type);
    }

    public boolean isRuleTypeMulti() {
        return ExcelColumnTypeRuleEnum.MULTI.name().equalsIgnoreCase(this.type);
    }

    /**
     * 求取主键值所需，表格名称
     */
    public String getQueryRuleTable() {
        if (this.isRuleTypeQueryRule() || this.isRuleTypeQueryGoal()) {
            if (Strings.isNotBlank(this.rule)) {
                String[] keys = this.rule.split(":");
                if (keys.length == 2 && Strings.isNotBlank(keys[0]) && Strings.isNotBlank(keys[1])) {
                    return keys[0];
                }
            }
        }
        return null;
    }

    /**
     * 求取主键值所需，字段名称
     */
    public List<String> getQueryRuleColumn() {
        List<String> columns = new ArrayList<>();
        if (this.isRuleTypeQueryRule() || this.isRuleTypeQueryGoal()) {
            if (Strings.isNotBlank(this.rule)) {
                String[] keys = this.rule.split(":");
                if (keys.length == 2 && Strings.isNotBlank(keys[0]) && Strings.isNotBlank(keys[1])) {
                    String[] keys1 = keys[1].split(",");
                    for (String key : keys1) {
                        if (Strings.isNotBlank(key) && !columns.contains(key)) {
                            columns.add(key);
                        }
                    }
                }
            }
        }
        return columns;
    }

    public Set<String> getColumnNames() {
        Set<String> columnNames = new LinkedHashSet<>();
        if (Strings.isNotBlank(this.columnName)) {
            String[] columns = this.columnName.split(",");
            for (String str : columns) {
                if (Strings.isNotBlank(str)) {
                    columnNames.add(str);
                }
            }
        }
        return columnNames;
    }
}
