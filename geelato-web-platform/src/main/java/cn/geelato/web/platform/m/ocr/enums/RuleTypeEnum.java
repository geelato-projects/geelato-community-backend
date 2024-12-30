package cn.geelato.web.platform.m.ocr.enums;

import cn.geelato.core.script.js.JsProvider;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.m.arco.entity.SelectOptionData;
import cn.geelato.web.platform.m.ocr.entity.OcrPdfMetaRule;
import cn.geelato.web.platform.m.ocr.service.OcrUtils;
import lombok.Getter;
import org.apache.logging.log4j.util.Strings;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Getter
public enum RuleTypeEnum {
    TRIM("去除前后空白", "去除字符串两端的空白字符（如空格、制表符、换行符等）。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            return content.trim();
        }
    },
    UPPERCASE("转大写", "将字符串中的所有字符都转换为大写。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            return content.toUpperCase(Locale.ENGLISH);
        }
    },
    LOWERCASE("转小写", "将字符串中的所有字符都转换为小写。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            return content.toLowerCase(Locale.ENGLISH);
        }
    },
    DELETES("删除字符", "删除字符串中‘正则表达式’匹配的字符。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                return content.replaceAll(rule.getRule(), "");
            } else {
                throw new RuntimeException("Regular expression is empty");
            }
        }
    },
    REPLACE("替换字符", "将字符串中‘正则表达式’匹配的字符替换为指定字符。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                return content.replaceAll(rule.getRule(), rule.getGoal() == null ? "" : rule.getGoal());
            } else {
                throw new RuntimeException("Regular expression is empty");
            }
        }
    },
    EXTRACT("提取字符", "保留字符串中‘正则表达式’匹配的字符。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                return OcrUtils.extract(content, rule.getRule());
            } else {
                throw new RuntimeException("Regular expression is empty");
            }
        }
    },
    CONSTANT("常量", "直接返回指定字符。", false, false) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            return rule.getRule() == null ? "" : rule.getRule();
        }
    },
    TIMECONVERSION("时间转换", "用指定的解析格式解析时间，再用输出格式转换时间。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) throws ParseException {
            if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal()) && Strings.isNotBlank(rule.getLocale())) {
                return DateUtils.convertTime(content, rule.getRule(), rule.getGoal(), rule.getTimeZone(), rule.getLocale());
            } else {
                throw new RuntimeException("TimeFormat or TimeParse or locale is empty");
            }
        }
    },
    TIMECHANGE("时间增减", "以目标时间增加或减少指定时间，返回计算结果。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) throws ParseException {
            if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal()) && Strings.isNotBlank(rule.getExtra())) {
                return DateUtils.calculateTime(content, rule.getExtra(), rule.getRule(), rule.getGoal());
            } else {
                throw new RuntimeException("Amount or unit or timeParse is empty");
            }
        }
    },
    PREFIX("添加前缀", "在字符串前面添加指定字符。", false, false) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                return String.format("%s%s", rule.getRule(), content);
            } else {
                throw new RuntimeException("Prefix is empty");
            }
        }
    },
    SUFFIX("添加后缀", "在字符串后面添加指定字符。", false, false) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                return String.format("%s%s", content, rule.getRule());
            } else {
                throw new RuntimeException("Suffix is empty");
            }
        }
    },
    CHECKBOX("数据字典，多值匹配替换", "选择数据字典，匹配多个字典项，返回字典项编码。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                String result = OcrUtils.calculateItemCodes(content, rule.getRule(), rule.getGoal());
                return rule.isRetain() && Strings.isBlank(result) ? content : result;
            } else {
                throw new RuntimeException("Dictionary encoding is empty");
            }
        }
    },
    DICTIONARY("数据字典，单值匹配替换", "选择数据字典，匹配单个字典项，返回字典项编码。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                String result = OcrUtils.calculateItemCode(content, rule.getRule(), rule.getGoal());
                return rule.isRetain() && Strings.isBlank(result) ? content : result;
            } else {
                throw new RuntimeException("Dictionary encoding is empty");
            }
        }
    },
    RADIO("数据字典，单值匹配替换（静态）", "创建数据字典[key:value]，匹配key值，返回value值。", false, false) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                String result = OcrUtils.calculateRadio(content, rule.getRule(), rule.getGoal());
                return rule.isRetain() && Strings.isBlank(result) ? content : result;
            } else {
                throw new RuntimeException("Dictionary encoding is empty");
            }
        }
    },
    QUERYGOAL("查询模型某字段值", "查询模型，查询字段与输入值匹配[或]，回写目标字段（不属于查询字段）值。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                String result = OcrUtils.calculateTables(content, rule.getRule(), rule.getGoal());
                return rule.isRetain() && Strings.isBlank(result) ? content : result;
            } else {
                throw new RuntimeException("Entity or column is empty");
            }
        }
    },
    QUERYRULE("查询模型某字段值", "查询模型，查询字段与输入值匹配[或]，回写目标字段（属于查询字段）值。", false, true) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule()) && Strings.isNotBlank(rule.getGoal())) {
                String result = OcrUtils.calculateTables(content, rule.getRule(), rule.getGoal());
                return rule.isRetain() && Strings.isBlank(result) ? content : result;
            } else {
                throw new RuntimeException("Entity or column is empty");
            }
        }
    },
    EXPRESSION("Javascript计算公式", "使用Javascript计算公式，返回计算结果。例：$.A?$.B:$.C。", false, false) {
        @Override
        public String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) {
            if (Strings.isNotBlank(rule.getRule())) {
                Object jsResult = JsProvider.executeExpression(rule.getRule(), opcMap);
                return jsResult == null ? "" : jsResult.toString();
            } else {
                throw new RuntimeException("Javascript expression is empty");
            }
        }
    };

    private final String label;
    private final String description;
    private final boolean disabled;
    private final boolean notNull;

    RuleTypeEnum(String label, String description, boolean disabled, boolean notNull) {
        this.label = label;
        this.description = description;
        this.disabled = disabled;
        this.notNull = notNull;
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
        RuleTypeEnum ruleTypeEnum = RuleTypeEnum.lookUp(value);
        return ruleTypeEnum == null ? null : ruleTypeEnum.getLabel();
    }

    /**
     * 根据名称查找对应的枚举值
     *
     * @param name 枚举名称
     * @return 对应的枚举值，如果未找到则返回null
     */
    public static RuleTypeEnum lookUp(String name) {
        for (RuleTypeEnum rule : RuleTypeEnum.values()) {
            if (rule.name().equalsIgnoreCase(name)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 处理给定内容的方法。
     *
     * @param content 待处理的内容
     * @param rule    OCR PDF元数据规则
     * @param opcMap  OPC（Open Packaging Conventions）映射，包含其他相关的处理信息
     * @return 处理后的字符串结果
     * @throws ParseException 如果在解析过程中发生错误，则抛出此异常
     */
    public abstract String handle(String content, OcrPdfMetaRule rule, Map<String, Object> opcMap) throws ParseException;
}
