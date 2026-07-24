package cn.geelato.web.platform.srv.excel.entity;

import cn.geelato.web.platform.srv.excel.enums.ExcelColumnTypeRuleEnum;
import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;

/**
 * 复杂导入 - 数据处理规则
 * <p>
 * 对应前端 ComplexRuleData。与普通导入的 {@link BusinessTypeRuleData} 规则语义一致
 * （共用 {@link ExcelColumnTypeRuleEnum}），区别在于复杂模板按位置定位，
 * 规则需指明作用范围 scope（TABLE 固定单元格 / LIST 列表区域），
 * 当 scope=LIST 时用 listFieldName 指定所属列表。
 *
 * @author diabl
 */
@Getter
@Setter
public class ComplexRuleData {
    // 处理范围：TABLE（表格固定单元格）/ LIST（列表区域）
    private String scope;
    // scope=LIST 时的所属列表英文（关联 ComplexListData.fieldName）
    private String listFieldName;
    // 处理列名（单选，值=字段英文）
    private String columnName;
    // 规则类型（ExcelColumnTypeRuleEnum）
    private String type;
    // 规则：正则表达式 / 字典编码 / 表格:字段,字段...
    private String rule;
    // 目标字段 / 替换值 / 对称方式(AB:CN|AB*CD)
    private String goal;
    // 是否优先于全局多值处理（复杂导入按规则次序执行，保留字段兼容前端）
    private boolean priority = false;
    // 保留原值：规则清洗后为空，TRUE:保留清洗前值;FALSE:以清洗结果为准
    private boolean retain = false;
    // 执行次序
    private Integer order;
    // 备注
    private String remark;

    public boolean isScopeTable() {
        return "TABLE".equalsIgnoreCase(this.scope);
    }

    public boolean isScopeList() {
        return "LIST".equalsIgnoreCase(this.scope);
    }

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

    public boolean isRuleTypeTrim() {
        return ExcelColumnTypeRuleEnum.TRIM.name().equalsIgnoreCase(this.type);
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
     * 多值分割规则（SYM/MULTI）
     */
    public boolean isRuleTypeMultiValue() {
        return isRuleTypeSym() || isRuleTypeMulti();
    }

    /**
     * 查询规则（QUERYGOAL/QUERYRULE）所需，表格名称
     */
    public String getQueryRuleTable() {
        if ((isRuleTypeQueryRule() || isRuleTypeQueryGoal()) && Strings.isNotBlank(this.rule)) {
            String[] keys = this.rule.split(":");
            if (keys.length == 2 && Strings.isNotBlank(keys[0])) {
                return keys[0];
            }
        }
        return null;
    }

    /**
     * 查询规则（QUERYGOAL/QUERYRULE）所需，查询字段列表
     */
    public List<String> getQueryRuleColumn() {
        List<String> columns = new ArrayList<>();
        if ((isRuleTypeQueryRule() || isRuleTypeQueryGoal()) && Strings.isNotBlank(this.rule)) {
            String[] keys = this.rule.split(":");
            if (keys.length == 2 && Strings.isNotBlank(keys[1])) {
                for (String key : keys[1].split(",")) {
                    if (Strings.isNotBlank(key) && !columns.contains(key)) {
                        columns.add(key);
                    }
                }
            }
        }
        return columns;
    }
}
