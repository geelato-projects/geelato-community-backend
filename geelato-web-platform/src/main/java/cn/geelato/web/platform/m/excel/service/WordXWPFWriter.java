package cn.geelato.web.platform.m.excel.service;

import cn.geelato.web.platform.m.excel.entity.*;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlToken;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.m.excel.enums.WordTableLoopTypeEnum;
import org.openxmlformats.schemas.drawingml.x2006.main.CTNonVisualDrawingProps;
import org.openxmlformats.schemas.drawingml.x2006.main.CTPositiveSize2D;
import org.openxmlformats.schemas.drawingml.x2006.wordprocessingDrawing.CTInline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author diabl
 */
@Component
public class WordXWPFWriter {
    private static final Pattern paragraphPattern = Pattern.compile("\\$\\{[\\\u4e00-\\\u9fa5,\\w,\\.]+\\}");
    private static final Pattern loopRowPattern = Pattern.compile("\\{\\#[\\\u4e00-\\\u9fa5,\\w,\\.]+\\}");
    private static final Pattern loopCellPattern = Pattern.compile("\\{\\*[\\\u4e00-\\\u9fa5,\\w,\\.]+\\}");
    private static final Pattern loopHeadPattern = Pattern.compile("\\{\\?[\\\u4e00-\\\u9fa5,\\w,\\.]+\\}");
    private static final Pattern loopEndPattern = Pattern.compile("\\{\\/[\\\u4e00-\\\u9fa5,\\w,\\.]+\\}");
    private final Logger logger = LoggerFactory.getLogger(WordXWPFWriter.class);

    /**
     * @param document
     * @param placeholderMetaMap
     * @param valueMapList
     * @param valueMap
     */
    public void writeDocument(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, List<Map> valueMapList, Map valueMap) {
        // mapList 数据解析
        Map<String, List<Map>> valueListMap = analysisValueMapList(valueMapList);
        // loop 内容循环
        List<WordParagraphMeta> paragraphMetas = analysisParagraphLoop(document, valueListMap);
        setParagraphLoop(document, placeholderMetaMap, paragraphMetas);
        // loop 表格循环
        List<WordTableMeta> tableMetas = analysisTableLoop(document, valueListMap);
        setTableLoop(document, placeholderMetaMap, tableMetas);
        // 标签替换：文本、图片 ${}
        setValueMap(document, placeholderMetaMap, valueMap);
    }


    /**
     * 修改水印样式高度的方法,如果不想改高度可以不用方法
     *
     * @param styleStr 之前的水印样式
     * @param height   需要改成的高度
     * @return 返回新修改好的水印样式
     */
    public static String getWaterMarkStyle(String styleStr, double height) {
        //把拿到的样式用";"切割，切割后保存到数组中
        Pattern p = Pattern.compile(";");
        String[] strs = p.split(styleStr);
        //遍历保存的数据，找到高度样式，将高度改为参数传入高度的
        for (String str : strs) {
            if (str.startsWith("height:")) {
                String heightStr = "height:" + height + "pt";
                styleStr = styleStr.replace(str, heightStr);
                break;
            }
        }
        return styleStr;
    }

    /**
     * 对列表数据解析
     *
     * @param valueMapList
     * @return
     */
    private Map<String, List<Map>> analysisValueMapList(List<Map> valueMapList) {
        Map<String, List<Map>> valueListMap = new LinkedHashMap<>();
        if (valueMapList != null && valueMapList.size() > 0) {
            for (int i = 0; i < valueMapList.size(); i++) {
                Map<String, List<Map>> listMap = valueMapList.get(i);
                if (listMap != null && listMap.size() > 0) {
                    for (Map.Entry<String, List<Map>> listEntry : listMap.entrySet()) {
                        valueListMap.put(listEntry.getKey(), listEntry.getValue());
                    }
                }
            }
        }

        return valueListMap;
    }

    /**
     * 表单循环，表循环、行循环、列循环
     *
     * @param document
     * @param valueListMap
     * @return
     */
    private List<WordTableMeta> analysisTableLoop(XWPFDocument document, Map<String, List<Map>> valueListMap) {
        List<WordTableMeta> tableMetas = new ArrayList<>();
        // 表格
        List<XWPFTable> tableList = document.getTables();
        if (tableList != null && tableList.size() > 0) {
            for (int i = 0; i < tableList.size(); i++) {
                if (tableList.get(i) != null) {
                    WordTableMeta meta = new WordTableMeta();
                    meta.setTable(tableList.get(i));
                    List<XWPFTableRow> rowsList = tableList.get(i).getRows();
                    if (rowsList != null && rowsList.size() > 0) {
                        meta.setRowTotal(rowsList.size());
                        for (int r = 0; r < rowsList.size(); r++) {
                            if (rowsList.get(r) != null) {
                                List<XWPFTableCell> cellList = rowsList.get(r).getTableCells();
                                if (cellList != null && cellList.size() > 0) {
                                    meta.setCellTotal(cellList.size());
                                    for (int c = 0; c < cellList.size(); c++) {
                                        if (cellList.get(c) != null) {
                                            List<XWPFParagraph> paragraphList = cellList.get(c).getParagraphs();
                                            if (paragraphList != null && paragraphList.size() > 0) {
                                                for (int p = 0; p < paragraphList.size(); p++) {
                                                    if (paragraphList.get(p) != null) {
                                                        List<String> runList = new ArrayList<>();
                                                        List<XWPFRun> runs = paragraphList.get(p).getRuns();
                                                        if (runs != null) {
                                                            for (int u = 0; u < runs.size(); u++) {
                                                                String runText = runs.get(u).getText(0);
                                                                runList.add(runText);
                                                                if (Strings.isNotBlank(runText)) {
                                                                    if (loopHeadPattern.matcher(runText).find()) {
                                                                        meta.setType(WordTableLoopTypeEnum.TABLE.name());
                                                                        meta.setIdentify(getIdentify(loopHeadPattern, runText, new String[]{"{?", "}"}));
                                                                        tableMetas.add(meta);
                                                                        clearLoopPosition(loopHeadPattern, runs.get(u));
                                                                    } else if (loopRowPattern.matcher(runText).find()) {
                                                                        meta.setType(WordTableLoopTypeEnum.ROW.name());
                                                                        meta.setIdentify(getIdentify(loopRowPattern, runText, new String[]{"{#", "}"}));
                                                                        tableMetas.add(meta);
                                                                        clearLoopPosition(loopRowPattern, runs.get(u));
                                                                    } else if (loopCellPattern.matcher(runText).find()) {
                                                                        meta.setType(WordTableLoopTypeEnum.CELL.name());
                                                                        meta.setIdentify(getIdentify(loopCellPattern, runText, new String[]{"{*", "}"}));
                                                                        tableMetas.add(meta);
                                                                        clearLoopPosition(loopCellPattern, runs.get(u));
                                                                    } else if (paragraphPattern.matcher(runText).find()) {
                                                                        meta.setCellStartPosition(c);
                                                                        meta.setRowStartPosition(r);
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    meta.setValueMapList(valueListMap.get(meta.getIdentify()));
                }
            }
        }

        return tableMetas;
    }

    private void setTableLoop(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, List<WordTableMeta> tableMetas) {
        if (tableMetas != null && tableMetas.size() > 0) {
            for (WordTableMeta meta : tableMetas) {
                // 没有数据不循环
                if (meta.getValueMapList() == null || meta.getValueMapList().size() == 0) {
                    continue;
                }
                // 分类型循环
                if (meta.isLoopTypeTable()) {
                    setTableLoopTypeTable(document, placeholderMetaMap, meta);
                } else if (meta.isLoopTypeCell()) {
                    setTableLoopTypeCell(document, placeholderMetaMap, meta);
                } else if (meta.isLoopTypeRow()) {
                    setTableLoopTypeRow(document, placeholderMetaMap, meta);
                }
            }
        }
    }

    private void setTableLoopTypeTable(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, WordTableMeta meta) {
        Set<XWPFTable> xwpfTables = new LinkedHashSet<>();
        // 循环生成表单
        for (int i = 1; i <= meta.getValueMapList().size(); i++) {
            if (i == meta.getValueMapList().size()) {
                xwpfTables.add(meta.getTable());
                break;
            }
            XmlCursor cursor = meta.getTable().getCTTbl().newCursor();
            XWPFTable targetTable = document.insertNewTbl(cursor);
            copyTable(targetTable, meta.getTable());
            xwpfTables.add(targetTable);
        }
        // 替换数据
        int index = 0;
        for (XWPFTable table : xwpfTables) {
            Map valueMap = meta.getValueMapList().get(index);
            addValueTable(document, table, placeholderMetaMap, valueMap);
            index++;
        }
    }

    private void setTableLoopTypeCell(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, WordTableMeta meta) {
        Set<Set<XWPFTableCell>> xwpfTableCellSet = new LinkedHashSet<>();
        // 表格行
        List<XWPFTableRow> xwpfTableRows = meta.getTable().getRows();
        // 模板列
        Set<XWPFTableCell> templateCells = new LinkedHashSet<>();
        for (XWPFTableRow xwpfTableRow : xwpfTableRows) {
            templateCells.add(xwpfTableRow.getCell(meta.getCellStartPosition()));
        }
        xwpfTableCellSet.add(templateCells);
        // 循环插入段落
        for (int i = 1; i < meta.getValueMapList().size(); i++) {
            Set<XWPFTableCell> xwpfTableCells = new LinkedHashSet<>();
            for (XWPFTableRow xwpfTableRow : xwpfTableRows) {
                XWPFTableCell targetCell = xwpfTableRow.getCell(meta.getCellStartPosition() + i);
                if (targetCell == null) {
                    targetCell = xwpfTableRow.addNewTableCell();
                }
                copyTableCell(targetCell, xwpfTableRow.getCell(meta.getCellStartPosition()));
                xwpfTableCells.add(targetCell);
            }
            xwpfTableCellSet.add(xwpfTableCells);
        }
        // 替换数据
        int index = 0;
        for (Set<XWPFTableCell> xwpfTableCells : xwpfTableCellSet) {
            Map valueMap = meta.getValueMapList().get(index);
            for (XWPFTableCell xwpfTableCell : xwpfTableCells) {
                addValueTableCell(document, xwpfTableCell, placeholderMetaMap, valueMap);
            }
            index++;
        }
    }

    private void setTableLoopTypeRow(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, WordTableMeta meta) {
        Set<XWPFTableRow> xwpfTableRows = new LinkedHashSet<>();
        // 模板行
        XWPFTableRow templateRow = meta.getTable().getRow(meta.getRowStartPosition());
        xwpfTableRows.add(templateRow);
        // 循环插入段落
        for (int i = 1; i < meta.getValueMapList().size(); i++) {
            XWPFTableRow targetRow = meta.getTable().insertNewTableRow(meta.getRowStartPosition() + i);
            copyTableRow(targetRow, templateRow);
            xwpfTableRows.add(targetRow);
        }
        // 替换数据
        int index = 0;
        for (XWPFTableRow row : xwpfTableRows) {
            Map valueMap = meta.getValueMapList().get(index);
            addValueTableRow(document, row, placeholderMetaMap, valueMap);
            index++;
        }
    }

    private List<WordParagraphMeta> analysisParagraphLoop(XWPFDocument document, Map<String, List<Map>> valueListMap) {
        List<WordTagMeta> tagMetas = new ArrayList<>();
        int tagIndex = 1;
        // 段落
        List<XWPFParagraph> paragraphList = document.getParagraphs();
        if (paragraphList != null && paragraphList.size() > 0) {
            for (int i = 0; i < paragraphList.size(); i++) {
                if (paragraphList.get(i) != null) {
                    List<XWPFRun> runs = paragraphList.get(i).getRuns();
                    List<String> runList = new ArrayList<>();
                    if (runs != null) {
                        for (int r = 0; r < runs.size(); r++) {
                            String runText = runs.get(r).getText(0);
                            runList.add(runText);
                            if (Strings.isNotBlank(runText)) {
                                if (loopHeadPattern.matcher(runText).find()) {
                                    WordTagMeta meta = new WordTagMeta();
                                    meta.setIndex(tagIndex++);
                                    meta.setType(WordTagMeta.TYPE_START);
                                    meta.setIdentify(getIdentify(loopHeadPattern, runText, new String[]{"{?", "}"}));
                                    meta.setPosition(i);
                                    tagMetas.add(meta);
                                    clearLoopPosition(loopHeadPattern, runs.get(r));
                                } else if (loopEndPattern.matcher(runText).find()) {
                                    WordTagMeta meta = new WordTagMeta();
                                    meta.setIndex(tagIndex++);
                                    meta.setType(WordTagMeta.TYPE_END);
                                    meta.setIdentify(getIdentify(loopEndPattern, runText, new String[]{"{/", "}"}));
                                    meta.setPosition(i);
                                    tagMetas.add(meta);
                                    clearLoopPosition(loopEndPattern, runs.get(r));
                                }
                            }
                        }
                    }
                }
            }
        }
        List<WordParagraphMeta> paragraphMetas = buildWordParagraphMetas(document, valueListMap, tagMetas);

        return paragraphMetas;
    }

    private List<WordParagraphMeta> buildWordParagraphMetas(XWPFDocument document, Map<String, List<Map>> valueListMap, List<WordTagMeta> tagMetas) {
        List<WordParagraphMeta> paragraphMetas = new ArrayList<>();
        if (tagMetas == null || tagMetas.size() == 0) {
            return paragraphMetas;
        }
        // 排序
        tagMetas.sort(new Comparator<WordTagMeta>() {
            @Override
            public int compare(WordTagMeta o1, WordTagMeta o2) {
                return o1.getIndex() - o2.getIndex();
            }
        });
        // 确定开始结束标签
        Map<Integer, WordTagMeta> tagMetaMap = new LinkedHashMap<>();
        List<WordIndexTagMeta> indexMetas = new ArrayList<>();
        for (int e = 0; e < tagMetas.size(); e++) {
            WordTagMeta end = tagMetas.get(e);
            tagMetaMap.put(end.getIndex(), end);
            if (WordTagMeta.TYPE_END.equalsIgnoreCase(end.getType())) {
                WordIndexTagMeta index = new WordIndexTagMeta();
                index.setEndIndex(end.getIndex());
                for (int s = tagMetas.size() - 1; s >= 0; s--) {
                    WordTagMeta start = tagMetas.get(s);
                    if (start.getIndex() < end.getIndex() && WordTagMeta.TYPE_START.equalsIgnoreCase(start.getType())) {
                        if (end.getIdentify() != null && end.getIdentify().equalsIgnoreCase(start.getIdentify())) {
                            index.setStartIndex(start.getIndex());
                            indexMetas.add(index);
                            break;
                        }
                    }
                }
            }
        }
        // 段落集合
        for (WordIndexTagMeta indexMeta : indexMetas) {
            if (indexMeta.getStartIndex() < 0 || indexMeta.getEndIndex() < 0) {
                throw new RuntimeException("Foreach Loop, Missing start or end tag.");
            }
            WordParagraphMeta meta = new WordParagraphMeta();
            meta.setStartPosition(tagMetaMap.get(indexMeta.getStartIndex()).getPosition());
            meta.setStartPh(document.getParagraphArray(meta.getStartPosition()));
            meta.setEndPosition(tagMetaMap.get(indexMeta.getEndIndex()).getPosition());
            meta.setEndPh(document.getParagraphArray(meta.getEndPosition()));
            meta.setIdentify(tagMetaMap.get(indexMeta.getEndIndex()).getIdentify());
            meta.setValueMapList(valueListMap.get(meta.getIdentify()));
            Set<XWPFParagraph> templateParagraphs = new LinkedHashSet<>();
            for (int p = meta.getStartPosition(); p <= meta.getEndPosition(); p++) {
                XWPFParagraph paragraph = document.getParagraphs().get(p);
                if (paragraph != null) {
                    templateParagraphs.add(paragraph);
                }
            }
            meta.setTemplateParagraphs(templateParagraphs);
            paragraphMetas.add(meta);
        }

        return paragraphMetas;
    }

    private void setParagraphLoop(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, List<WordParagraphMeta> paragraphMetas) {
        if (paragraphMetas != null && paragraphMetas.size() > 0) {
            for (WordParagraphMeta meta : paragraphMetas) {
                if (meta.getValueMapList() == null || meta.getValueMapList().size() == 0) {
                    continue;
                }
                if (meta.getTemplateParagraphs() == null || meta.getTemplateParagraphs().size() == 0) {
                    continue;
                }
                setParagraphLoop(document, placeholderMetaMap, meta);
            }
        }
    }

    private void setParagraphLoop(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, WordParagraphMeta meta) {
        Set<Set<XWPFParagraph>> xwpfParagraphSet = new LinkedHashSet<>();
        // 循环插入段落
        for (int i = 1; i <= meta.getValueMapList().size(); i++) {
            if (i == meta.getValueMapList().size()) {
                xwpfParagraphSet.add(meta.getTemplateParagraphs());
                break;
            }
            Set<XWPFParagraph> xwpfParagraphs = new LinkedHashSet<>();
            for (XWPFParagraph xwpfParagraph : meta.getTemplateParagraphs()) {
                XWPFParagraph targetParagraph = document.insertNewParagraph(meta.getStartPh().getCTP().newCursor());
                copyParagraph(targetParagraph, xwpfParagraph);
                xwpfParagraphs.add(targetParagraph);
            }
            xwpfParagraphSet.add(xwpfParagraphs);
        }
        // 替换数据
        int index = 0;
        for (Set<XWPFParagraph> xwpfParagraphs : xwpfParagraphSet) {
            Map valueMap = meta.getValueMapList().get(index);
            for (XWPFParagraph paragraph : xwpfParagraphs) {
                addValueParagraph(document, paragraph, placeholderMetaMap, valueMap);
            }
            index++;
        }
    }

    private void setValueMap(XWPFDocument document, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        List<IBodyElement> bodyElements = document.getBodyElements();// 所有对象（段落+表格）
        int templateBodySize = bodyElements.size();// 标记模板文件（段落+表格）总个数
        int currentTable = 0;// 当前操作表格对象的索引
        int currentParagraph = 0;// 当前操作段落对象的索引
        for (int i = 0; i < templateBodySize; i++) {
            IBodyElement body = bodyElements.get(i);
            if (BodyElementType.PARAGRAPH.equals(body.getElementType())) {// 段落、图片
                XWPFParagraph paragraph = body.getBody().getParagraphArray(currentParagraph);
                if (paragraph != null) {
                    addValueParagraph(document, paragraph, placeholderMetaMap, valueMap);
                    currentParagraph++;
                }
            } else if (BodyElementType.TABLE.equals(body.getElementType())) {
                XWPFTable table = body.getBody().getTableArray(currentTable);
                if (table != null) {
                    addValueTable(document, table, placeholderMetaMap, valueMap);
                    currentTable++;
                }
            }
        }
    }

    private void addValueTable(XWPFDocument document, XWPFTable table, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        if (table != null) {
            for (XWPFTableRow row : table.getRows()) {
                if (row != null) {
                    addValueTableRow(document, row, placeholderMetaMap, valueMap);
                }
            }
        }
    }

    private void addValueTableRow(XWPFDocument document, XWPFTableRow row, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        if (row != null) {
            for (XWPFTableCell cell : row.getTableCells()) {
                if (cell != null) {
                    addValueTableCell(document, cell, placeholderMetaMap, valueMap);
                }
            }
        }
    }

    private void addValueTableCell(XWPFDocument document, XWPFTableCell cell, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        if (cell != null) {
            for (XWPFParagraph ph : cell.getParagraphs()) {
                if (ph != null) {
                    addValueParagraph(document, ph, placeholderMetaMap, valueMap);
                }
            }
        }
    }

    private void addValueParagraph(XWPFDocument document, XWPFParagraph paragraph, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        if (paragraph != null) {
            String phValue = paragraph.getText();
            List<String> runList = new ArrayList<>();
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs != null) {
                for (int r = 0; r < runs.size(); r++) {
                    String runText = runs.get(r).getText(0);
                    runList.add(runText);
                    if (Strings.isNotBlank(runText)) {
                        Matcher phm = paragraphPattern.matcher(runText);
                        while (phm.find()) {
                            PlaceholderMeta meta = placeholderMetaMap.get(phm.group());
                            boolean isReplace = false;
                            if (meta != null) {
                                Object oValue = valueMap.get(meta.getVar());
                                String value = oValue == null ? "" : String.valueOf(oValue);
                                if (meta.isImage()) {
                                    if (new File(value).exists()) {
                                        CTInline inline = runs.get(r).getCTR().addNewDrawing().addNewInline();
                                        try {
                                            insertPicture(document, value, inline, meta.getImageWidth(), meta.getImageHeight(), XWPFDocument.PICTURE_TYPE_PNG);
                                            document.createParagraph();
                                            isReplace = true;
                                        } catch (Exception e) {
                                            throw new RuntimeException("Image construction failure!", e);
                                        }
                                    }
                                } else {
                                    runText = runText.replace(phm.group(), value);
                                    isReplace = true;
                                }
                            }
                            if (!isReplace) {
                                runText = runText.replace(phm.group(), "");
                            }
                        }
                        runs.get(r).setText(runText, 0);
                    }
                }
            }
        }
    }

    private String getIdentify(Pattern pattern, String runText, String[] replaces) {
        String text = "";
        Matcher mp = pattern.matcher(runText);
        while (mp.find()) {
            text = mp.group();
            break;
        }
        if (replaces != null && replaces.length > 0) {
            for (String re : replaces) {
                text = text.replace(re, "");
            }
        }

        return text;
    }

    private void clearLoopPosition(Pattern pattern, XWPFRun run) {
        String runText = run.getText(0);
        Matcher mp = pattern.matcher(runText);
        while (mp.find()) {
            runText = runText.replace(mp.group(), "");
        }
        run.setText(runText, 0);
    }

    private XWPFTable copyTable(XWPFTable newTable, XWPFTable originalTable) {
        newTable.getCTTbl().setTblPr(originalTable.getCTTbl().getTblPr());
        // 表格行
        List<XWPFTableRow> newRows = newTable.getRows();
        List<XWPFTableRow> rows = originalTable.getRows();
        boolean newIsShort = newRows.size() <= rows.size();
        int maxSize = newIsShort ? rows.size() : newRows.size();
        int minSize = newIsShort ? newRows.size() : rows.size();
        // 相同长度覆写
        for (int r = 0; r < minSize; r++) {
            if (newRows.get(r) != null && rows.get(r) != null) {
                copyTableRow(newRows.get(r), rows.get(r));
            }
        }
        // 不同长度，创建或删除
        for (int r = minSize; r < maxSize; r++) {
            if (newIsShort && rows.get(r) != null) {
                XWPFTableRow newRow = newTable.insertNewTableRow(r);
                copyTableRow(newRow, rows.get(r));
            } else if (newRows.get(r) != null) {
                newTable.removeRow(r);
            }
        }

        return newTable;
    }

    private XWPFTableRow copyTableRow(XWPFTableRow newRow, XWPFTableRow originalRow) {
        // 新行样式
        newRow.setHeight(originalRow.getHeight());
        newRow.setHeightRule(originalRow.getHeightRule());
        newRow.setCantSplitRow(originalRow.isCantSplitRow());
        newRow.setRepeatHeader(originalRow.isRepeatHeader());
        // 行的列
        List<XWPFTableCell> newCells = newRow.getTableCells();
        List<XWPFTableCell> cells = originalRow.getTableCells();
        boolean newIsShort = newCells.size() <= cells.size();
        int maxSize = newIsShort ? cells.size() : newCells.size();
        int minSize = newIsShort ? newCells.size() : cells.size();
        // 相同长度覆写
        for (int c = 0; c < minSize; c++) {
            if (newCells.get(c) != null && cells.get(c) != null) {
                copyTableCell(newCells.get(c), cells.get(c));
            }
        }
        // 不同长度，创建或删除
        for (int c = minSize; c < maxSize; c++) {
            if (newIsShort && cells.get(c) != null) {
                XWPFTableCell newCell = newRow.addNewTableCell();
                copyTableCell(newCell, cells.get(c));
            } else if (newCells.get(c) != null) {
                newRow.removeCell(c);
            }
        }

        return newRow;
    }

    private XWPFTableCell copyTableCell(XWPFTableCell newCell, XWPFTableCell originalCell) {
        // 新列样式
        newCell.setColor(originalCell.getColor());
        newCell.setVerticalAlignment(originalCell.getVerticalAlignment());
        newCell.setWidth(String.valueOf(originalCell.getWidth()));
        newCell.setWidthType(originalCell.getWidthType());
        // 列的段落
        List<XWPFParagraph> newParagraphs = newCell.getParagraphs();
        List<XWPFParagraph> paragraphs = originalCell.getParagraphs();
        boolean newIsShort = newParagraphs.size() <= paragraphs.size();
        int maxSize = newIsShort ? paragraphs.size() : newParagraphs.size();
        int minSize = newIsShort ? newParagraphs.size() : paragraphs.size();
        // 相同长度覆写
        for (int p = 0; p < minSize; p++) {
            if (newParagraphs.get(p) != null && paragraphs.get(p) != null) {
                copyParagraph(newParagraphs.get(p), paragraphs.get(p));
            }
        }
        // 不同长度，创建或删除
        for (int p = minSize; p < maxSize; p++) {
            if (newIsShort && paragraphs.get(p) != null) {
                XWPFParagraph newParagraph = newCell.addParagraph();
                copyParagraph(newParagraph, paragraphs.get(p));
            } else if (newParagraphs.get(p) != null) {
                newCell.removeParagraph(p);
            }
        }

        return newCell;
    }

    private XWPFParagraph copyParagraph(XWPFParagraph newParagraph, XWPFParagraph originalParagraph) {
        newParagraph.getCTP().setPPr(originalParagraph.getCTP().getPPr());
        // 复制原始行的内容到新行
        for (XWPFRun run : originalParagraph.getRuns()) {
            XWPFRun newRun = newParagraph.createRun();
            newRun.setText(run.getText(0));
            newRun.setBold(run.isBold());
            newRun.setColor(run.getColor());
            newRun.setCapitalized(run.isCapitalized());
            newRun.setCharacterSpacing(run.getCharacterSpacing());
            newRun.setDoubleStrikethrough(run.isDoubleStrikeThrough());
            newRun.setEmbossed(run.isEmbossed());
            newRun.setEmphasisMark(run.getEmphasisMark() == null ? null : String.valueOf(run.getEmphasisMark()));
            newRun.setFontFamily(run.getFontFamily());
            newRun.setFontSize(run.getFontSize());
            newRun.setImprinted(run.isImprinted());
            newRun.setItalic(run.isItalic());
            newRun.setKerning(run.getKerning());
            newRun.setLang(run.getLang());
            newRun.setShadow(run.isShadowed());
            newRun.setSmallCaps(run.isSmallCaps());
            newRun.setStrikeThrough(run.isStrikeThrough());
            newRun.setTextHighlightColor(run.getTextHighlightColor() == null ? null : String.valueOf(run.getTextHighlightColor()));
            newRun.setTextPosition(run.getTextPosition());
            newRun.setTextScale(run.getTextScale());
            newRun.setUnderline(run.getUnderline());
            newRun.setUnderlineColor(run.getUnderlineColor());
            newRun.setUnderlineThemeColor(String.valueOf(run.getUnderlineThemeColor()));
            newRun.setVanish(run.isVanish());
            newRun.setVerticalAlignment(run.getVerticalAlignment() == null ? null : String.valueOf(run.getVerticalAlignment()));
        }

        return newParagraph;
    }

    private static void insertPicture(XWPFDocument document, String filePath, CTInline inline, double imageWidth, double imageHeight, int format) throws FileNotFoundException, InvalidFormatException {
        document.addPictureData(new FileInputStream(filePath), XWPFDocument.PICTURE_TYPE_PNG);
        long id = UIDGenerator.generate();
        long width = (long) Math.floor(Units.toEMU(imageWidth) * 1000 / 35);
        long height = (long) Math.floor(Units.toEMU(imageHeight) * 1000 / 35);
        String blipId = document.addPictureData(new FileInputStream(filePath), format);
        String picXml = getPicXml(blipId, width, height);
        XmlToken xmlToken = null;
        try {
            xmlToken = XmlToken.Factory.parse(picXml);
        } catch (XmlException xe) {
            throw new RuntimeException(xe.getMessage());
        }
        inline.set(xmlToken);
        inline.setDistT(0);
        inline.setDistB(0);
        inline.setDistL(0);
        inline.setDistR(0);
        CTPositiveSize2D extent = inline.addNewExtent();
        extent.setCx(width);
        extent.setCy(height);
        CTNonVisualDrawingProps docPr = inline.addNewDocPr();
        docPr.setId(id);
        docPr.setName("IMG_" + id);
        docPr.setDescr("IMG_" + id);
    }

    private static String getPicXml(String blipId, long width, long height) {
        String picXml = "<a:graphic xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\">" + "   <a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" + "      <pic:pic xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" + "         <pic:nvPicPr>" + "            <pic:cNvPr id=\"" + 0 + "\" name=\"Generated\"/>" + "            <pic:cNvPicPr/>" + "         </pic:nvPicPr>" + "         <pic:blipFill>" + "            <a:blip r:embed=\"" + blipId + "\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"/>" + "            <a:stretch>" + "               <a:fillRect/>" + "            </a:stretch>" + "         </pic:blipFill>" + "         <pic:spPr>" + "            <a:xfrm>" + "               <a:off x=\"0\" y=\"0\"/>" + "               <a:ext cx=\"" + width + "\" cy=\"" + height + "\"/>" + "            </a:xfrm>" + "            <a:prstGeom prst=\"rect\">" + "               <a:avLst/>" + "            </a:prstGeom>" + "         </pic:spPr>" + "      </pic:pic>" + "   </a:graphicData>" + "</a:graphic>";
        return picXml;
    }
}













