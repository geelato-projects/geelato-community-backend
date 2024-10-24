package cn.geelato.web.platform.m.excel.service;

import cn.geelato.core.script.js.JsProvider;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.excel.entity.CellMeta;
import cn.geelato.web.platform.m.excel.entity.ExportColumn;
import cn.geelato.web.platform.m.excel.entity.PlaceholderMeta;
import cn.geelato.web.platform.m.excel.entity.RowMeta;
import cn.geelato.web.platform.m.excel.enums.ExcelAlignmentEnum;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
public class ExcelXSSFWriter {
    private final Logger logger = LoggerFactory.getLogger(ExcelXSSFWriter.class);

    /**
     * 按多组值写入一个sheet
     * 边解析sheet，边依据字典信息及值信息进行写入
     * 按行扫描处理
     * 支持单个单元格的写入
     * 支持列表按行动态添加写入，支持多个不同的列表并行动态写入
     *
     * @param sheet
     * @param placeholderMetaMap
     * @param valueMapList
     */
    public void writeSheet(XSSFSheet sheet, Map<String, PlaceholderMeta> placeholderMetaMap, List<Map> valueMapList, Map valueMap) {
        int lastRowIndex = sheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRowIndex; rowIndex++) {
            // 按行扫描处理
            XSSFRow row = sheet.getRow(rowIndex);
            if (row == null) {
                break;
            }
            RowMeta rowMeta = parseTemplateRow(row, placeholderMetaMap);
            int newRowCount = 0;
            if (rowMeta.isMultiGroupRow()) {
                int listIndex = 0;
                for (Map vm : valueMapList) {
                    newRowCount = setRowValue(sheet, rowIndex, rowMeta, vm);
                    // 完成列表的设置后，若创建了新行，则需要同步设置整个sheet当前的row索引值、最后一行的索引值
                    rowIndex += newRowCount;
                    lastRowIndex += newRowCount;
                    if (listIndex + 1 < valueMapList.size()) {
                        rowIndex += 1;
                        lastRowIndex += 1;
                        createRowWithPreRowStyle(sheet, rowIndex);
                    }
                    listIndex++;
                }
            } else if (rowMeta.isDeleteGroupRow()) {
                sheet.shiftRows(rowIndex + 1, lastRowIndex, -1, true, false);
                rowIndex -= 1;
                lastRowIndex -= 1;
            } else {
                newRowCount = setRowValue(sheet, rowIndex, rowMeta, valueMap);
                // 完成列表的设置后，若创建了新行，则需要同步设置整个sheet当前的row索引值、最后一行的索引值
                rowIndex += newRowCount;
                lastRowIndex += newRowCount;
            }

        }
    }

    /**
     * 按一组值valueMap写入sheet
     *
     * @param sheet
     * @param placeholderMetaMap
     * @param valueMap
     */
    public void writeSheet(XSSFSheet sheet, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        int lastRowIndex = sheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRowIndex; rowIndex++) {
            // 按行扫描处理
            XSSFRow row = sheet.getRow(rowIndex);
            if (row == null) {
                break;
            }
            RowMeta rowMeta = parseTemplateRow(row, placeholderMetaMap);
            int newRowCount = setRowValue(sheet, rowIndex, rowMeta, valueMap);
            // 完成列表的设置后，若创建了新行，则需要同步设置整个sheet当前的row索引值、最后一行的索引值
            rowIndex += newRowCount;
            lastRowIndex += newRowCount;
        }
    }


    private RowMeta parseTemplateRow(XSSFRow row, Map<String, PlaceholderMeta> placeholderMetaMap) {
        RowMeta rowMeta = new RowMeta();
        // 将列表与非列表cellMeta分组，存在不同的ArrayList中
        // 列表类占位符单元格索引位置Map，列表变量名为key，列表为value
        Map<String, List<CellMeta>> listCellMetaMap = new HashMap<>();
        // 非列表类占位符单元格索引位置list
        List<CellMeta> notListCellIndexes = new LinkedList<>();
        short lastCellNum = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            XSSFCell cell = row.getCell(cellIndex);
            if (cell != null && cell.getCellType().equals(CellType.STRING)) {
                String cellValue = cell.getStringCellValue();
                // 找到占位符
                if (ExcelCommonUtils.CELL_META_PATTERN.matcher(cellValue).find()) {
                    // 如果是一个行属性标识，如cellValue为${rowMeta.xxx}
                    if (ExcelCommonUtils.ROW_META_PATTERN.matcher(cellValue).find()) {
                        if ("${rowMeta.isMultiGroupRow}".equalsIgnoreCase(cellValue)) {
                            rowMeta.setMultiGroupRow(true);
                        } else if ("${rowMeta.deleteOnFinished}".equalsIgnoreCase(cellValue)) {
                            rowMeta.setDeleteGroupRow(true);
                        }
                        // 清除该行标识信息
                        cell.setBlank();
                        continue;
                    }
                    // 否则为单元格占位符标识
                    // 从字典中取该占位符对应的元数据
                    PlaceholderMeta meta = placeholderMetaMap.get(cellValue);
                    if (meta == null) {
                        continue;
                    }
                    if (meta.isIsList()) {
                        if (!StringUtils.isEmpty(meta.getListVar())) {
                            List<CellMeta> cellMetaList = listCellMetaMap.get(meta.getListVar());
                            if (cellMetaList == null) {
                                cellMetaList = new ArrayList<CellMeta>();
                                listCellMetaMap.put(meta.getListVar(), cellMetaList);
                            }
                            CellMeta cellMeta = new CellMeta();
                            cellMeta.setIndex(cellIndex);
                            cellMeta.setPlaceholderMeta(meta);
                            cellMetaList.add(cellMeta);
                        }
                    } else {
                        CellMeta cellMeta = new CellMeta();
                        cellMeta.setIndex(cellIndex);
                        cellMeta.setPlaceholderMeta(meta);
                        notListCellIndexes.add(cellMeta);
                    }
                }
            }
        }

        rowMeta.setNotListCellIndexes(notListCellIndexes);
        rowMeta.setListCellMetaMap(listCellMetaMap);
        return rowMeta;
    }

    /**
     * 设置行值，对于列表行，动态创建行之后再设置行值
     *
     * @param sheet
     * @param rowIndex
     * @param rowMeta
     * @param valueMap
     * @return 返回创建的新行数
     */
    private int setRowValue(XSSFSheet sheet, int rowIndex, RowMeta rowMeta, Map valueMap) {
        XSSFRow row = sheet.getRow(rowIndex);
        // ----1 设置类型为非list的cell
        for (CellMeta cellMeta : rowMeta.getNotListCellIndexes()) {
            setCellValue(row.getCell(cellMeta.getIndex()), cellMeta.getPlaceholderMeta(), valueMap, null);
        }
        // ----2 设置类型为list的cell
        // ----2.1 动态创建空行
        // listOfCellIndexMap的多个list中，找出list的最大值，作为需要创建的行数依据
        int listMaxRowCount = 0;
        for (String key : rowMeta.getListCellMetaMap().keySet()) {
            List valueList = (List) valueMap.get(key);
            if (valueList == null) {
                valueList = new ArrayList<>();
            }
            listMaxRowCount = Math.max(valueList.size(), listMaxRowCount);
        }
        // 先创建空行
        int newRowCount = createRowWithPreRowStyles(sheet, rowIndex, listMaxRowCount);

        // ----2.2 为模板行及动态创建的行设置值
        // 设置类型为list的cell
        for (String key : rowMeta.getListCellMetaMap().keySet()) {
            List<Map> valueList = (List) valueMap.get(key);
            List<CellMeta> cellMetaList = rowMeta.getListCellMetaMap().get(key);
            int currentRowIndexInList = 0;
            // 设置列表每行数据的值
            if (valueList != null) {
                for (Map listValueMap : valueList) {
                    XSSFRow newRow = sheet.getRow(rowIndex + currentRowIndexInList);
                    currentRowIndexInList++;
                    if (cellMetaList != null) {
                        for (CellMeta cellMeta : cellMetaList) {
                            XSSFCell cell = newRow.getCell(cellMeta.getIndex());
                            setCellValue(cell, cellMeta.getPlaceholderMeta(), valueMap, listValueMap);
                        }
                    }
                }
            }
        }
        // ----3 为类型为list，且需合并单元格的cell，按列合并
        // 创建了新行，才需要进行单元格合并
        if (newRowCount > 0) {
            for (String key : rowMeta.getListCellMetaMap().keySet()) {
                List<Map> valueList = (List) valueMap.get(key);
                List<CellMeta> cellMetaList = rowMeta.getListCellMetaMap().get(key);
                // 合并唯一约束 - 行范围
                List<List<Integer>> mergeScope = ExcelCommonUtils.getMergeUniqueScope(cellMetaList, valueMap, valueList);
                setMergeScope(sheet, rowIndex, cellMetaList, valueMap, valueList, mergeScope);
            }
        }

        return newRowCount;
    }

    public int createRowWithPreRowStyles(XSSFSheet sheet, Integer rowIndex, Integer listMaxRowCount) {
        if (listMaxRowCount <= 0) {
            return 0;
        }
        // 模板行,模板单元格
        XSSFRow sourceRow = sheet.getRow(rowIndex);
        List<XSSFCell> sourceCellList = new ArrayList<>();
        for (int cellIndex = 0; cellIndex < sourceRow.getLastCellNum(); cellIndex++) {
            sourceCellList.add(sourceRow.getCell(cellIndex));
        }
        // 向下移动
        if (listMaxRowCount > 1) {
            int lastRowNo = sheet.getLastRowNum();
            if (rowIndex + 1 <= lastRowNo) {
                sheet.shiftRows(rowIndex + 1, lastRowNo, listMaxRowCount - 1);
            }
        }
        // 复制模板
        int newRowCount = 0;
        while (newRowCount < listMaxRowCount - 1) {
            newRowCount++;
            XSSFRow newRow = sheet.createRow(rowIndex + newRowCount);
            newRow.setHeight(sourceRow.getHeight());
            for (int cellIndex = 0; cellIndex < sourceRow.getLastCellNum(); cellIndex++) {
                XSSFCell templateCell = sourceCellList.get(cellIndex);
                XSSFCell newCell = newRow.createCell(cellIndex);
                newCell.setCellStyle(templateCell.getCellStyle());
            }
        }

        return newRowCount;
    }

    /**
     * 合并相同数据的行
     *
     * @param sheet
     * @param rowIndex
     * @param cellMetaList
     * @param valueMap
     * @param valueList
     * @param mergeScope
     */
    public void setMergeScope(XSSFSheet sheet, int rowIndex, List<CellMeta> cellMetaList, Map valueMap, List<Map> valueList, List<List<Integer>> mergeScope) {
        for (CellMeta cellMeta : cellMetaList) {
            if (cellMeta.getPlaceholderMeta().isIsMerge()) {
                // 获取数据相同的行
                List<List<Integer>> integerSet = ExcelCommonUtils.getIntegerSet(cellMeta, valueMap, valueList);
                if (integerSet.size() > 0) {
                    // 集合交集
                    List<List<Integer>> ranges = integerSet;
                    if (mergeScope != null) {
                        ranges = ExcelCommonUtils.listRetain(integerSet, mergeScope);
                    }
                    // 合并单元格
                    if (ranges != null && ranges.size() > 0) {
                        for (List<Integer> range : ranges) {
                            Collections.sort(range);
                            CellRangeAddress region = new CellRangeAddress(rowIndex + range.get(0), rowIndex + range.get(range.size() - 1), cellMeta.getIndex(), cellMeta.getIndex());
                            sheet.addMergedRegion(region);
                        }
                    }
                }
            }
        }
    }


    private PlaceholderMeta getPlaceholderMeta(XSSFCell cell, Map<String, PlaceholderMeta> placeholderMetaMap) {
        if (cell == null) {
            return null;
        }
        CellType cellType = cell.getCellType();
        String cellValue = cellType.equals(cellType) ? cell.getStringCellValue() : "";
        return placeholderMetaMap.get(cellValue);
    }


    private void setCellValue(XSSFCell cell, PlaceholderMeta meta, Map valueMap, Map listValueMap) {
        if (Strings.isBlank(meta.getFormatExport())) {
            meta.setFormatExport("");
        }
        if (Strings.isBlank(meta.getFormatImport())) {
            meta.setFormatImport("");
        }
        // 不是列表，且是变更
        if (meta.isValueComputeModeVar()) {
            if (meta.isIsList()) {
                Object v = listValueMap.get(meta.getVar());
                setCellValueByValueType(cell, meta, v);
            } else {
                if (meta.getVar() != null && meta.getVar().trim().length() > 0) {
                    Object v = valueMap.get(meta.getVar());
                    setCellValueByValueType(cell, meta, v);
                }
            }
        } else if (meta.isValueComputeModeConst()) {
            setCellValueByValueType(cell, meta, meta.getConstValue());
        } else if (meta.isValueComputeModeExpression()) {
            Object v = JsProvider.executeExpression(meta.getExpression(), meta.isIsList() ? listValueMap : valueMap);
            setCellValueByValueType(cell, meta, v);
        }
    }

    private String formatDate(Object value, String in, String out) {
        try {
            if (Strings.isBlank(in) || Strings.isBlank(out)) {
                return value.toString();
            }
            Date date = null;
            String valueStr = value.toString();
            if (NumberUtils.isNumber(valueStr) && "timestamp".equalsIgnoreCase(in)) {
                date = new Date(Long.parseLong(valueStr));
            } else {
                date = new SimpleDateFormat(in).parse(valueStr);
            }
            return new SimpleDateFormat(out).format(date);
        } catch (Exception e) {
            return "";
        }
    }

    private void setCellValueByValueType(XSSFCell cell, PlaceholderMeta meta, Object value) {
        if (value != null) {
            if (meta.isValueTypeNumber()) {
                if (value.toString().indexOf(".") == -1) {
                    cell.setCellValue(String.format("%s%s%s", meta.getFormatImport(), Long.parseLong(value.toString()), meta.getFormatExport()));
                } else {
                    cell.setCellValue(String.format("%s%s%s", meta.getFormatImport(), new BigDecimal(value.toString()).doubleValue(), meta.getFormatExport()));
                }
            } else if (meta.isValueTypeDate()) {
                cell.setCellValue(formatDate(value, meta.getFormatImport(), meta.getFormatExport()));
            } else if (meta.isValueTypeDateTime()) {
            } else {
                cell.setCellValue(String.format("%s%s%s", meta.getFormatImport(), value.toString(), meta.getFormatExport()));
            }
        } else {
            if (meta.isValueTypeNumber()) {
                cell.setCellValue(0);
            } else {
                cell.setCellValue("");
            }
        }
    }

    /**
     * 找到需要插入的行数，并新建一个POI的row对象
     *
     * @param sheet
     * @param rowIndex
     * @return
     */
    private XSSFRow createRow(XSSFSheet sheet, int rowIndex) {
        XSSFRow row = null;
        if (sheet.getRow(rowIndex) != null) {
            int lastRowNo = sheet.getLastRowNum();
            sheet.shiftRows(rowIndex, lastRowNo, 1);
        }
        row = sheet.createRow(rowIndex);
        return row;
    }

    public XSSFRow createRowWithPreRowStyle(XSSFSheet sheet, Integer rowIndex) {
        XSSFRow preRow = sheet.getRow(rowIndex - 1);
        XSSFRow newRow = createRow(sheet, rowIndex);
        newRow.setHeight(preRow.getHeight());
        for (int cellIndex = 0; cellIndex < preRow.getLastCellNum(); cellIndex++) {
            XSSFCell templateCell = preRow.getCell(cellIndex);
            // 这里里要createCell，否则getCell返回空值
            XSSFCell newCell = newRow.createCell(cellIndex);
            newCell.setCellStyle(templateCell.getCellStyle());
        }
        return newRow;
    }

    /**
     * 生成导出模板文件
     *
     * @param workbook
     * @param sheetName
     * @param exportColumns
     */
    public void generateTemplateFile(XSSFWorkbook workbook, String sheetName, List<ExportColumn> exportColumns) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        int profundity = 1;// 计算最大深度
        List<ExportColumn> bottomExportColumns = new LinkedList<>();// 最底层数据
        List<ExportColumn> headerExportColumns = new LinkedList<>();// 所有表头数据，平铺
        // 计算，树节点的层级、子节点
        List<Integer> profundityList = new ArrayList<>();
        for (ExportColumn exportColumn : exportColumns) {
            // 层级、宽度（多少列）
            exportColumn.calculateLevelAndBreadth(0);
            profundityList.add(exportColumn.findMaxValueInTree());
        }
        // 计算最大深度
        Collections.sort(profundityList);
        profundity = profundityList.get(profundityList.size() - 1) + 1;
        // 计算最底层数据
        ExcelCommonUtils.bottomLayerOfTree(exportColumns, bottomExportColumns);
        // 计算每行宽度
        for (ExportColumn exportColumn : exportColumns) {
            exportColumn.calculateDepth(profundity);
        }
        // 计算合并数据
        ExcelCommonUtils.cellRangeAddress(0, 0, exportColumns, headerExportColumns);
        XSSFCellStyle headerStyle = getHeaderCellStyle(workbook);
        for (int i = 0; i < profundity; i++) {
            XSSFRow row = sheet.createRow(i);
            // 初始化单元格
            for (int j = 0; j < bottomExportColumns.size(); j++) {
                ExcelXSSFUtils.getCell(row, j);
            }
            // 插入表头
            for (ExportColumn column : headerExportColumns) {
                if (column.getLevel() == i) {
                    // align
                    headerStyle.setAlignment(ExcelAlignmentEnum.getLabel(column.getAlign()));
                    // title
                    XSSFCell cell = ExcelXSSFUtils.setCell(row, column.getFirstCol(), headerStyle, column.getTitle());
                    // description
                    ExcelXSSFUtils.setCellComment(sheet, cell, column.getDescription());
                    // 合并单元格
                    if (column.getLastCol() > column.getFirstCol() || column.getLastRow() > column.getFirstRow()) {
                        CellRangeAddress mergedRegion = new CellRangeAddress(column.getFirstRow(), column.getLastRow(), column.getFirstCol(), column.getLastCol());
                        sheet.addMergedRegion(mergedRegion);
                    }
                }
            }
        }
        // 插入替换行
        XSSFRow row = sheet.createRow(profundity);
        XSSFCellStyle style = getCellStyle(workbook);
        for (int i = 0; i <= bottomExportColumns.size(); i++) {
            // 特殊标记，行开始
            if (i == bottomExportColumns.size()) {
                ExcelXSSFUtils.setCell(row, i, null, "${rowMeta.isMultiGroupRow}");
                break;
            }
            // 插入替换树 ${}
            ExportColumn column = bottomExportColumns.get(i);
            // align
            style.setAlignment(ExcelAlignmentEnum.getLabel(column.getAlign()));
            // title
            String cellValue = String.format("${%s}", column.getTitle());
            ExcelXSSFUtils.setCell(row, column.getFirstCol(), style, cellValue);
            // width
            ExcelXSSFUtils.setColumnWidth(sheet, i, column.getWidth());
        }
        // 表头锁定
        sheet.createFreezePane(0, profundity);
    }

    /**
     * 表头样式
     * 字体：仿宋、12、加粗
     * 背景色：浅灰色
     * 边框：上下左右
     * 方向：水平居中、垂直居中
     *
     * @param workbook
     * @return
     */
    private XSSFCellStyle getHeaderCellStyle(XSSFWorkbook workbook) {
        // 创建单元格样式，并将字体样式应用到单元格样式中
        XSSFCellStyle style = workbook.createCellStyle();
        // 创建字体样式
        XSSFFont font = ExcelXSSFUtils.getCellFont(workbook, ExcelXSSFUtils.FONT_NAME_FANGSONG_GB2312, (short) 12);
        // 设置字体加粗
        font.setBold(true);
        style.setFont(font);
        // 其他样式
        ExcelXSSFUtils.setTableHeaderGeneralStyle(style);

        return style;
    }

    /**
     * 表头样式
     * 字体：仿宋、11
     * 边框：上下左右
     * 方向：水平居中、垂直居中
     *
     * @param workbook
     * @return
     */
    private XSSFCellStyle getCellStyle(XSSFWorkbook workbook) {
        // 创建单元格样式，并将字体样式应用到单元格样式中
        XSSFCellStyle style = workbook.createCellStyle();
        // 创建字体样式
        XSSFFont font = ExcelXSSFUtils.getCellFont(workbook, ExcelXSSFUtils.FONT_NAME_FANGSONG_GB2312, (short) 11);
        style.setFont(font);
        // 其他样式
        ExcelXSSFUtils.setTableGeneralStyle(style);

        return style;
    }

    /**
     * 从excel中读取占位符、变量定义
     *
     * @param sheet
     * @return
     */
    public Map<String, PlaceholderMeta> readPlaceholderMeta(XSSFSheet sheet) {
        int lastRowIndex = sheet.getLastRowNum();
        Map<String, PlaceholderMeta> map = new HashMap<String, PlaceholderMeta>(lastRowIndex);
        // 跳过第一行，标题行
        for (int i = 1; i <= lastRowIndex; i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            PlaceholderMeta placeholderMeta = new PlaceholderMeta();
            placeholderMeta.setPlaceholder(row.getCell(0).getStringCellValue());
            placeholderMeta.setVar(row.getCell(1).getStringCellValue());
            placeholderMeta.setListVar(row.getCell(2).getStringCellValue());
            placeholderMeta.setConstValue(row.getCell(3).getStringCellValue());
            placeholderMeta.setExpression(row.getCell(4).getStringCellValue());
            placeholderMeta.setValueType(row.getCell(5).getStringCellValue());
            placeholderMeta.setValueComputeMode(row.getCell(6).getStringCellValue());
            placeholderMeta.setIsList(getBoolean(row.getCell(7)));
            placeholderMeta.setIsMerge(getBoolean(row.getCell(8)));
            placeholderMeta.setIsUnique(getBoolean(row.getCell(9)));
            placeholderMeta.setIsImage(getBoolean(row.getCell(10)));
            placeholderMeta.setImageWidth(row.getCell(11).getNumericCellValue());
            placeholderMeta.setImageHeight(row.getCell(12).getNumericCellValue());
            placeholderMeta.setImageSource(row.getCell(13).getStringCellValue());
            placeholderMeta.setBarcodeCode(row.getCell(14).getStringCellValue());
            placeholderMeta.setFormatImport(row.getCell(15).getStringCellValue());
            placeholderMeta.setFormatExport(row.getCell(16).getStringCellValue());
            placeholderMeta.setDescription(row.getCell(17).getStringCellValue());
            placeholderMeta.setBarcode(null);
            // 校验占位符元数据
            if (validatePlaceholderMeta(placeholderMeta)) {
                map.put(placeholderMeta.getPlaceholder(), placeholderMeta);
            }
        }
        return map;
    }

    public boolean validatePlaceholderMeta(PlaceholderMeta placeholderMeta) {
        return true;
    }

    private boolean getBoolean(XSSFCell cell) {
        if (cell == null) {
            return false;
        }
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        } else if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue() > 0;
        } else {
            return "是".equalsIgnoreCase(cell.getStringCellValue()) || "TRUE".equalsIgnoreCase(cell.getStringCellValue()) || "1".equalsIgnoreCase(cell.getStringCellValue());
        }
    }


}
