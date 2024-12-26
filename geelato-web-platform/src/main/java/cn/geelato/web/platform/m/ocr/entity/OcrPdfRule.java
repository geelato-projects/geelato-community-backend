package cn.geelato.web.platform.m.ocr.entity;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
public class OcrPdfRule {
    public static final String REG_EXP_ALL = "ALL";
    public static final String REG_EXP_MATCH = "|+";
    private String[] name;
    private String[] expression;
    private Map<String, String> regexp;

    private static String regExpLabel(String key) {
        if (Strings.isNotBlank(key) && key.endsWith(REG_EXP_MATCH)) {
            int index = key.lastIndexOf(REG_EXP_MATCH);
            return key.substring(0, index);
        }
        return key;
    }

    private static boolean regExpMatch(String key) {
        return Strings.isNotBlank(key) && key.endsWith(REG_EXP_MATCH);
    }

    /**
     * 获取正则表达式规则列表。
     * 从当前对象的regexp属性中获取所有键值对，并为每个键值对创建一个OcrPdfRuleRegExp对象，
     * 然后将这些对象添加到列表中并返回。
     *
     * @return 包含所有正则表达式规则的列表。
     */
    public List<OcrPdfRuleRegExp> getRegExpList() {
        List<OcrPdfRuleRegExp> regExpList = new ArrayList<>();
        if (this.getRegexp() != null) {
            for (Map.Entry<String, String> entry : this.getRegexp().entrySet()) {
                OcrPdfRuleRegExp regExp = new OcrPdfRuleRegExp();
                regExp.setLabel(regExpLabel(entry.getKey()));
                regExp.setExpression(entry.getValue());
                regExp.setMatching(regExpMatch(entry.getKey()));
                if (Strings.isBlank(regExp.getLabel()) && Strings.isBlank(regExp.getExpression())) {
                    continue;
                }
                regExpList.add(regExp);
            }
        }
        return regExpList;
    }

    /**
     * 获取为"ALL"的正则表达式规则。
     *
     * @return 返回正则表达式规则，如果没有找到符合条件的规则，则返回null。
     */
    public OcrPdfRuleRegExp regExpIncludeAll() {
        return this.getRegExpList().stream().filter(regExp -> REG_EXP_ALL.equalsIgnoreCase(regExp.getLabel())).findFirst().orElse(null);
    }

    /**
     * 获取排除所有标签为"ALL"的正则表达式规则列表。
     *
     * @return 返回排除所有标签为"ALL"的正则表达式规则列表。
     */
    public List<OcrPdfRuleRegExp> regExpListExcludeAll() {
        return this.getRegExpList().stream().filter(regExp -> !REG_EXP_ALL.equalsIgnoreCase(regExp.getLabel())).collect(Collectors.toList());
    }
}
