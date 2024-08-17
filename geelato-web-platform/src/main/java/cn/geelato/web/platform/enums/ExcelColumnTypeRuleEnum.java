package cn.geelato.web.platform.enums;

/**
 * @author diabl
 */
public enum ExcelColumnTypeRuleEnum {
    DELETES,// 删除匹配的字段(rule)
    REPLACE,// 查找匹配的字段(rule)，替换成指定字段(goal)
    TRIM,// 去除字符串前后空格
    UPPERCASE,// 字符串大写
    LOWERCASE,// 字符串小写
    CHECKBOX,// 多选框，查询字典编码(rule)
    DICTIONARY,// 查询字典编码(rule)，字典项名称与字符串匹配，回写字典项编码。
    QUERYGOAL,// 查询表格(rule)，查询字段与字符串匹配[或]，回写目标字段[不属于查询字段](goal)值。
    QUERYRULE,// 查询表格(rule)，查询字段与字符串匹配[或]，回写目标字段[属于查询字段](goal)值。
    EXPRESSION,// JavaScript计算公式(rule)。例：$.卸货港?$.卸货港:$.目的港。
    SYM,// 多值分割(rule)，对称关系(goal)。特色规则[AB*CD](值不同但长度一致)、[AB:CN](值不同但长度不一致)。
    MULTI;// 多值分割(rule)，相乘关系。
}






