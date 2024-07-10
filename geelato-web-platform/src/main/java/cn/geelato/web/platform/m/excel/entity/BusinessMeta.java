package cn.geelato.web.platform.m.excel.entity;

import org.apache.logging.log4j.util.Strings;
import cn.geelato.web.platform.enums.ExcelEvaluationEnum;

import java.util.ArrayList;
import java.util.List;

/**
 * @author diabl
 * @description: 业务元数据
 * @date 2023/10/12 14:29
 */
public class BusinessMeta {
    //表格名称
    private String tableName;
    //字段名称
    private String columnName;
    //取值计算方式
    private String evaluation;
    //常量
    private String constValue;
    //变量 ${}
    private String variableValue;
    //表达式
    private String expression;
    //数据字典，字典编码；变量对应字典项的label值，求取字典项value值
    private String dictCode;
    //对应主键，【表格名称:字段名称】；变量对应的表格字段，求取表格的主键值。
    private String primaryValue;
    //备注
    private String remark;

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getColumnName() {
        return columnName;
    }

    public void setColumnName(String columnName) {
        this.columnName = columnName;
    }

    public String getEvaluation() {
        return evaluation;
    }

    public void setEvaluation(String evaluation) {
        this.evaluation = evaluation;
    }

    public String getConstValue() {
        return constValue;
    }

    public void setConstValue(String constValue) {
        this.constValue = constValue;
    }

    public String getVariableValue() {
        return variableValue;
    }

    public void setVariableValue(String variableValue) {
        this.variableValue = variableValue;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public String getDictCode() {
        return dictCode;
    }

    public void setDictCode(String dictCode) {
        this.dictCode = dictCode;
    }

    public String getPrimaryValue() {
        return primaryValue;
    }

    public void setPrimaryValue(String primaryValue) {
        this.primaryValue = primaryValue;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    /**
     * 取值方式，常量
     *
     * @return
     */
    public boolean isEvaluationTypeConst() {
        return ExcelEvaluationEnum.CONST.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 取值方式，直接取业务数据值
     *
     * @return
     */
    public boolean isEvaluationTypeVariable() {
        return ExcelEvaluationEnum.VARIABLE.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 取值方式，JavaScript表达式计算
     *
     * @return
     */
    public boolean isEvaluationTypeJsExpression() {
        return ExcelEvaluationEnum.JS_EXPRESSION.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 取值方式，数据字典项value值
     *
     * @return
     */
    public boolean isEvaluationTypeCheckBox() {
        return ExcelEvaluationEnum.CHECKBOX.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 取值方式，数据字典项value值
     *
     * @return
     */
    public boolean isEvaluationTypeDictionary() {
        return ExcelEvaluationEnum.DICTIONARY.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 取值方式，主键id值
     *
     * @return
     */
    public boolean isEvaluationTypePrimaryKey() {
        return ExcelEvaluationEnum.PRIMARY_KEY.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 取值方式，流水号
     *
     * @return
     */
    public boolean isEvaluationTypeSerialNumber() {
        return ExcelEvaluationEnum.SERIAL_NUMBER.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 取值方式，未被清洗过的数值
     *
     * @return
     */
    public boolean isEvaluationTypePrimitive() {
        return ExcelEvaluationEnum.PRIMITIVE.name().equalsIgnoreCase(this.evaluation);
    }

    /**
     * 求取主键值所需，表格名称
     *
     * @return
     */
    public String getPrimaryKeyTable() {
        return getPrimarySplit("table");
    }

    /**
     * 求取主键值所需，字段名称
     *
     * @return
     */
    public String getPrimaryKeyColumn() {
        return getPrimarySplit("column");
    }

    public List<String> getPrimaryKeyColumns() {
        List<String> columnNames = new ArrayList<>();
        String column = getPrimaryKeyColumn();
        if (Strings.isNotBlank(column)) {
            String[] values = column.split(",");
            if (values != null && values.length > 0) {
                for (String columnName : values) {
                    if (!columnNames.contains(columnName)) {
                        columnNames.add(columnName);
                    }
                }
            }
        }
        return columnNames;
    }

    public String getPrimaryKeyGoal() {
        return getPrimarySplit("goal");
    }

    private String getPrimarySplit(String type) {
        if (Strings.isNotBlank(type) && Strings.isNotBlank(this.primaryValue)) {
            String[] keys = this.primaryValue.split(":");
            if (keys != null && keys.length == 2 && Strings.isNotBlank(keys[0]) && Strings.isNotBlank(keys[1])) {
                if ("table".equalsIgnoreCase(type)) {
                    return keys[0];
                } else if ("column".equalsIgnoreCase(type) || "goal".equalsIgnoreCase(type)) {
                    String[] values = keys[1].split("\\|");
                    if (values != null && values.length == 2 && Strings.isNotBlank(values[0]) && Strings.isNotBlank(values[1])) {
                        if ("goal".equalsIgnoreCase(type)) {
                            return values[0];
                        } else if ("column".equalsIgnoreCase(type)) {
                            return values[1];
                        }
                    }
                }
            }
        }

        return null;
    }
}
