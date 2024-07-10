package cn.geelato.web.platform.m.excel.entity;

import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author diabl
 * @date 2024/1/9 19:57
 */
public class WordParagraphMeta {
    private XWPFParagraph startPh;
    private int startPosition = -1;
    private XWPFParagraph endPh;
    private int endPosition = -1;
    private String identify;
    private List<Map> valueMapList;
    private Set<XWPFParagraph> templateParagraphs = new LinkedHashSet<>();

    public XWPFParagraph getStartPh() {
        return startPh;
    }

    public void setStartPh(XWPFParagraph startPh) {
        this.startPh = startPh;
    }

    public XWPFParagraph getEndPh() {
        return endPh;
    }

    public void setEndPh(XWPFParagraph endPh) {
        this.endPh = endPh;
    }

    public String getIdentify() {
        return identify;
    }

    public void setIdentify(String identify) {
        this.identify = identify;
    }

    public List<Map> getValueMapList() {
        return valueMapList;
    }

    public void setValueMapList(List<Map> valueMapList) {
        this.valueMapList = valueMapList;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    public Set<XWPFParagraph> getTemplateParagraphs() {
        return templateParagraphs;
    }

    public void setTemplateParagraphs(Set<XWPFParagraph> templateParagraphs) {
        this.templateParagraphs = templateParagraphs;
    }
}
