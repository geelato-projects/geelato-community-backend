package cn.geelato.web.platform.m.excel.enums;

/**
 * @author diabl
 * 取值类型
 */
public enum ExcelEvaluationEnum {
    CONST,// D列，常量取值；不需要变量。
    VARIABLE,// E列，变量取值；必填；对应业务数据列。
    JS_EXPRESSION,// F列。JavaScript计算公式。如：$.20GP+$.40GP | $.是否启用==true?1:0
    CHECKBOX,// G列。查询字典项编码。数据字典输入字典编码，变量为字典项名称，查询字典项编码。
    DICTIONARY,// G列。查询字典项编码。数据字典输入字典编码，变量为字典项名称，查询字典项编码。
    PRIMARY_KEY,// H列。格式：表格:目标字段|查询字段,字段...，查询字段之间关系为“或”。
    SERIAL_NUMBER,// 导入次序生成流水号，UUID 19位。
    PRIMITIVE// 取对应业务数据列未被清洗的值。
}
