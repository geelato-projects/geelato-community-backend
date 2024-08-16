package cn.geelato.web.platform.m.excel.entity;

import lombok.Getter;
import lombok.Setter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author diabl
 */
@Getter
@Setter
public class WordParagraphMeta {
    private XWPFParagraph startPh;
    private int startPosition = -1;
    private XWPFParagraph endPh;
    private int endPosition = -1;
    private String identify;
    private List<Map> valueMapList;
    private Set<XWPFParagraph> templateParagraphs = new LinkedHashSet<>();
}
