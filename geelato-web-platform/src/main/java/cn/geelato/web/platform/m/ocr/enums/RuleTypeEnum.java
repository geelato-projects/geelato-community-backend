package cn.geelato.web.platform.m.ocr.enums;

import cn.geelato.web.platform.m.arco.entity.SelectOptionData;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

@Getter
public enum RuleTypeEnum {
    TRIM("去除前后空白", "去除字符串两端的空白字符（如空格、制表符、换行符等）。", false),
    UPPERCASE("转大写", "将字符串中的所有字符都转换为大写。", false),
    LOWERCASE("转小写", "将字符串中的所有字符都转换为小写。", false),
    DELETES("删除字符", "删除字符串中‘正则表达式’匹配的字符。", false),
    REPLACE("替换字符", "将字符串中‘正则表达式’匹配的字符替换为指定字符。", false),
    EXTRACT("提取字符", "保留字符串中‘正则表达式’匹配的字符。", false),
    TIMECONVERSION("时间转换", "用指定的解析格式解析时间，再用输出格式转换时间。", false),
    TIMECHANGE("时间增减", "以目标时间增加或减少指定时间，返回计算结果。", false),
    PREFIX("添加前缀", "在字符串前面添加指定字符。", false),
    SUFFIX("添加后缀", "在字符串后面添加指定字符。", false),
    CHECKBOX("数据字典，多值匹配", "选择数据字典，匹配多个字典项，返回字典项编码。", false),
    DICTIONARY("数据字典，单值匹配", "选择数据字典，匹配单个字典项，返回字典项编码。", false),
    QUERYGOAL("查询模型某字段值", "查询模型，查询字段与输入值匹配[或]，回写目标字段（不属于查询字段）值。", false),
    QUERYRULE("查询模型某字段值", "查询模型，查询字段与输入值匹配[或]，回写目标字段（属于查询字段）值。", false),
    EXPRESSION("Javascript计算公式", "使用Javascript计算公式，返回计算结果。例：$.A?$.B:$.C。", false);

    private String label;
    private String description;
    private boolean disabled;

    RuleTypeEnum(String label, String description, boolean disabled) {
        this.label = label;
        this.description = description;
        this.disabled = disabled;
    }

    /**
     * 获取选择项列表
     *
     * @return 包含所有规则类型的选择项列表
     */
    public static List<SelectOptionData> getSelectOptions() {
        List<SelectOptionData> options = new ArrayList<>();
        for (RuleTypeEnum rule : RuleTypeEnum.values()) {
            SelectOptionData option = new SelectOptionData();
            option.setLabel(rule.getLabel());
            option.setValue(rule.name());
            option.setOther(rule.getDescription());
            option.setDisabled(rule.isDisabled());
            options.add(option);
        }
        return options;
    }

    /**
     * 根据值获取标签
     * 根据给定的枚举值，获取对应的标签
     *
     * @param value 枚举值
     * @return 如果找到对应的枚举值，则返回其标签；否则返回null
     */
    public static String getLabelByValue(String value) {
        for (RuleTypeEnum rule : RuleTypeEnum.values()) {
            if (rule.name().equals(value)) {
                return rule.getLabel();
            }
        }
        return null;
    }
}
