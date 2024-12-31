package cn.geelato.web.platform.m.ocr.entity;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.util.Strings;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
public class OcrPdfRule {
    public static final String REG_EXP_ALL = "ALL";
    private String[] name;
    private String[] expression;
    private List<OcrPdfRuleRegExp> regexp;

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
            for (OcrPdfRuleRegExp regExp : this.getRegexp()) {
                if (Strings.isNotBlank(regExp.getLabel()) && Strings.isNotBlank(regExp.getExpression())) {
                    regExpList.add(regExp);
                }
            }
        }
        return regExpList;
    }

    /**
     * 获取为"ALL"的正则表达式规则。
     *
     * @return 返回正则表达式规则，如果没有找到符合条件的规则，则返回null。
     */
    public List<OcrPdfRuleRegExp> regExpIncludeAll() {
        return this.getRegExpList().stream().filter(regExp -> REG_EXP_ALL.equals(regExp.getLabel())).collect(Collectors.toList());
    }

    /**
     * 获取排除所有标签为"ALL"的正则表达式规则列表。
     *
     * @return 返回排除所有标签为"ALL"的正则表达式规则列表。
     */
    public List<OcrPdfRuleRegExp> regExpListExcludeAll() {
        return this.getRegExpList().stream().filter(regExp -> !REG_EXP_ALL.equals(regExp.getLabel())).collect(Collectors.toList());
    }
}
