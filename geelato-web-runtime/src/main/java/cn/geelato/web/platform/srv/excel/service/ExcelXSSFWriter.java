package cn.geelato.web.platform.srv.excel.service;

import cn.geelato.core.script.js.JsProvider;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.srv.excel.entity.CellMeta;
import cn.geelato.web.platform.srv.excel.entity.ExportColumn;
import cn.geelato.web.platform.srv.excel.entity.PlaceholderMeta;
import cn.geelato.web.platform.srv.excel.entity.RowMeta;
import cn.geelato.web.platform.srv.excel.enums.ExcelAlignmentEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

@Component
@Slf4j
public class ExcelXSSFWriter {

    /**
     * 按多组值写入一个sheet
     * 边解析sheet，边依据字典信息及值信息进行写入
     * 按行扫描处理
     * 支持单个单元格的写入
     * 支持列表按行动态添加写入，支持多个不同的列表并行动态写入
     *
     * @param sheet              Excel工作表对象，用于写入数据
     * @param placeholderMetaMap 占位符元数据映射，键为占位符名称，值为对应的PlaceholderMeta对象
     * @param valueMapList       包含多组值映射的列表，每组值映射是一个Map，包含要写入工作表的数据
     * @param valueMap           包含单个值映射的Map，也包含要写入工作表的数据
     */
    public void writeSheet(XSSFSheet sheet, Map<String, PlaceholderMeta> placeholderMetaMap, List<Map> valueMapList, Map valueMap) {
        int lastRowIndex = sheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRowIndex; rowIndex++) {
            // 按行扫描处理
            XSSFRow row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
            }
            RowMeta rowMeta = parseTemplateRow(row, placeholderMetaMap);
            int newRowCount;
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
     * 按一组值写入Excel工作表
     * <p>
     * 根据提供的占位符元数据映射和值映射，将值写入到指定的Excel工作表中。
     *
     * @param sheet              Excel工作表对象
     * @param placeholderMetaMap 占位符元数据映射，键为占位符名称，值为对应的PlaceholderMeta对象
     * @param valueMap           值映射，包含要写入工作表的数据
     */
    public void writeSheet(XSSFSheet sheet, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        int lastRowIndex = sheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRowIndex; rowIndex++) {
            // 按行扫描处理
            XSSFRow row = sheet.getRow(rowIndex);
            if (row == null) {
                continue;
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
                            List<CellMeta> cellMetaList = listCellMetaMap.computeIfAbsent(meta.getListVar(), k -> new ArrayList<>());
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
     * 设置行值
     * <p>
     * 对于列表行，动态创建行之后再设置行值。
     *
     * @param sheet    Excel工作表对象
     * @param rowIndex 当前处理的行索引
     * @param rowMeta  行元数据对象，包含单元格的元数据
     * @param valueMap 值映射，包含要写入工作表的数据
     * @return 返回创建的新行数
     */
    private int setRowValue(XSSFSheet sheet, int rowIndex, RowMeta rowMeta, Map valueMap) {
        XSSFRow row = sheet.getRow(rowIndex);
        // ----1 设置类型为非list的cell
        for (CellMeta cellMeta : rowMeta.getNotListCellIndexes()) {
            PlaceholderMeta placeholderMeta = cellMeta.getPlaceholderMeta();
            if (!placeholderMeta.isIsDynamic()) {
                setCellValue(row.getCell(cellMeta.getIndex()), placeholderMeta, valueMap, null);
            }
        }
        int dynamicRowCount = 0;
        int shiftRowCount = 0;
        for (CellMeta cellMeta : rowMeta.getNotListCellIndexes()) {
            int newRowCount = cellMeta.getIndex() + shiftRowCount;
            XSSFCell cell = row.getCell(newRowCount);
            PlaceholderMeta placeholderMeta = cellMeta.getPlaceholderMeta();
            if (placeholderMeta.isIsDynamic()) {
                Map<String, Integer> dynamicCountMap = setCellDynamicValue(cell, placeholderMeta, valueMap, null);
                if (dynamicCountMap.get("rows") > 0) {
                    dynamicRowCount = Math.max(dynamicCountMap.get("rows"), dynamicRowCount);
                }
                if (dynamicCountMap.get("cells") > 0) {
                    shiftRowCount += dynamicCountMap.get("cells");
                }
            }
        }
        if (dynamicRowCount > 0) {
            return dynamicRowCount;
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
     * 设置合并单元格的范围
     * <p>
     * 根据提供的单元格元数据列表、值映射、值列表以及合并范围列表，将具有相同数据的单元格进行合并。
     *
     * @param sheet        Excel工作表对象，用于合并单元格
     * @param rowIndex     起始行索引
     * @param cellMetaList 单元格元数据列表，包含需要合并的单元格信息
     * @param valueMap     单个值映射，包含要写入工作表的数据
     * @param valueList    包含多组值映射的列表，每组值映射是一个Map，也包含要写入工作表的数据
     * @param mergeScope   合并范围列表，指定哪些单元格范围需要被合并
     */
    public void setMergeScope(XSSFSheet sheet, int rowIndex, List<CellMeta> cellMetaList, Map valueMap, List<Map> valueList, List<List<Integer>> mergeScope) {
        for (CellMeta cellMeta : cellMetaList) {
            if (cellMeta.getPlaceholderMeta().isIsMerge()) {
                // 获取数据相同的行
                List<List<Integer>> integerSet = ExcelCommonUtils.getIntegerSet(cellMeta, valueMap, valueList);
                if (!integerSet.isEmpty()) {
                    // 集合交集
                    List<List<Integer>> ranges = integerSet;
                    if (mergeScope != null) {
                        ranges = ExcelCommonUtils.listRetain(integerSet, mergeScope);
                    }
                    // 合并单元格
                    if (!ranges.isEmpty()) {
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
        String cellValue = cell.getStringCellValue();
        return placeholderMetaMap.get(cellValue);
    }

    private Map<String, Integer> setCellDynamicValue(XSSFCell cell, PlaceholderMeta meta, Map valueMap, Map listValueMap) {
        int newRows = 0;
        int newCells = 0;
        if (meta.getVar() == null || meta.getVar().trim().isEmpty()) {
            return Map.of("rows", newRows, "cells", newCells);
        }
        Object data = valueMap.get(meta.getVar());
        if (data instanceof List) {
            XSSFSheet sheet = cell.getSheet();
            int colIndex = cell.getColumnIndex();
            int rowIndex = cell.getRow().getRowNum();

            // 处理List<String>横向扩展
            if (!((List<?>) data).isEmpty() && ((List<?>) data).get(0) instanceof String) {
                List<String> rowData = (List<String>) data;
                // 横向插入单元格
                if (meta.isDynamicTypeCover()) {
                    coverCell(cell, rowData.size() - 1);
                } else {
                    extensionCell(cell, rowData.size() - 1);
                }
                newCells = rowData.size() - 1;
                for (int i = 0; i < rowData.size(); i++) {
                    cell.getRow().getCell(colIndex + i).setCellValue(rowData.get(i));
                }
            }
            // 处理List<List<String>>纵向扩展
            else if (!((List<?>) data).isEmpty() && ((List<?>) data).get(0) instanceof List) {
                List<List<String>> tableData = (List<List<String>>) data;
                if (meta.isDynamicTypeCover()) {
                    coverRow(cell, tableData.size() - 1);
                } else {
                    extensionRow(cell, tableData.size() - 1);
                }
                newRows = tableData.size() - 1;
                // 插入数据
                for (int i = 0; i < tableData.size(); i++) {
                    List<String> rowData = tableData.get(i);
                    if (meta.isDynamicTypeCover()) {
                        coverCell(sheet.getRow(rowIndex + i).getCell(colIndex), rowData.size() - 1);
                    } else {
                        extensionCell(sheet.getRow(rowIndex + i).getCell(colIndex), rowData.size() - 1);
                    }
                    newCells = Math.max(rowData.size() - 1, newCells);
                    for (int j = 0; j < rowData.size(); j++) {
                      sheet.getRow(rowIndex + i).getCell(colIndex + j).setCellValue(rowData.get(j));
                    }
                }
            }
        }

        return Map.of("rows", newRows, "cells", newCells);
    }

    private void extensionCell(XSSFCell cell, int addCellCount) {
        // 参数校验
        if (cell == null || addCellCount <= 0) {
            return;
        }
        XSSFRow row = cell.getRow();
        // 获取源单元格样式（只获取一次）
        CellStyle newStyle = cell.getCellStyle();
        int startCol = cell.getColumnIndex() + 1;
        // getLastCellNum()返回的是下一个空列的索引
        int endCol = row.getLastCellNum() - 1;
        // 向右移动现有单元格
        if (endCol >= startCol) {
            row.shiftCellsRight(startCol, endCol, addCellCount);
        }
        // 创建新单元格并设置样式
        for (int i = 0; i < addCellCount; i++) {
            XSSFCell newCell = row.createCell(startCol + i);
            newCell.setCellStyle(newStyle);
        }
    }

    private void coverCell(XSSFCell cell, int addCellCount) {
        if (cell == null || addCellCount <= 0) {
            return; // 参数检查
        }
        XSSFRow row = cell.getRow();
        // 获取源单元格样式（只获取一次）
        CellStyle newStyle = cell.getCellStyle();
        for (int i = 0; i < addCellCount; i++) {
            // 目标列索引
            int targetCol = cell.getColumnIndex() + 1 + i;
            XSSFCell targetCell = row.getCell(targetCol);
            if (targetCell == null) {
                // 创建新单元格
                targetCell = row.createCell(targetCol);
            } else {
                // 清空内容
                targetCell.setCellValue("");
                // 重置为空白样式
                targetCell.setCellStyle(row.getSheet().getWorkbook().createCellStyle());
            }
            // 应用新样式
            targetCell.setCellStyle(newStyle);
        }
    }

    private void extensionRow(XSSFCell cell, int addRowCount) {
        // 参数校验
        if (cell == null || addRowCount <= 0) {
            return;
        }
        XSSFSheet sheet = cell.getSheet();
        XSSFRow sourceRow = cell.getRow();
        int rowIndex = sourceRow.getRowNum();
        short rowHeight = sourceRow.getHeight();
        CellStyle sourceRowStyle = sourceRow.getRowStyle();
        CellStyle sourceCellStyle = cell.getCellStyle();
        // 获取源单元格列索引
        int sourceColIndex = cell.getColumnIndex();
        int lastRowNo = sheet.getLastRowNum();
        // 向下移动现有行
        if (rowIndex + 1 <= lastRowNo) {
            sheet.shiftRows(rowIndex + 1, lastRowNo, addRowCount);
        }
        // 创建新行并复制指定列的样式
        for (int i = 0; i < addRowCount; i++) {
            XSSFRow newRow = sheet.createRow(rowIndex + 1 + i);
            newRow.setHeight(rowHeight);
            if (sourceRowStyle != null) {
                newRow.setRowStyle(sourceRowStyle);
            }
            // 复制样式
            for (int cellIndex = 0; cellIndex < sourceRow.getLastCellNum(); cellIndex++) {
                XSSFCell templateCell = sourceRow.getCell(cellIndex);
                XSSFCell newCell = newRow.createCell(cellIndex);
                if (templateCell != null) {
                    newCell.setCellStyle(templateCell.getCellStyle());
                }
            }
        }
    }

    private void coverRow(XSSFCell cell, int addRowCount) {
        // 参数校验
        if (cell == null || addRowCount <= 0) {
            return;
        }

        XSSFSheet sheet = cell.getSheet();
        XSSFRow sourceRow = cell.getRow();
        int rowIndex = sourceRow.getRowNum();
        short rowHeight = sourceRow.getHeight();
        CellStyle sourceRowStyle = sourceRow.getRowStyle();
        CellStyle sourceCellStyle = cell.getCellStyle();
        int sourceColIndex = cell.getColumnIndex();

        for (int i = 1; i <= addRowCount; i++) {
            int targetRowIndex = rowIndex + i;
            XSSFRow targetRow = sheet.getRow(targetRowIndex);
            // 存在则调整，不存在则创建
            if (targetRow == null) {
                targetRow = sheet.createRow(targetRowIndex);
            }
            // 调整高度和样式
            targetRow.setHeight(rowHeight);
            if (sourceRowStyle != null) {
                targetRow.setRowStyle(sourceRowStyle);
            }
            // 处理目标单元格（覆盖或创建）
            XSSFCell targetCell = targetRow.getCell(sourceColIndex);
            if (targetCell != null) {
                // 覆盖已有单元格
                targetCell.setCellValue("");
                targetCell.setCellStyle(sourceCellStyle);
            } else {
                // 创建新单元格
                targetCell = targetRow.createCell(sourceColIndex);
                targetCell.setCellStyle(sourceCellStyle);
            }
        }
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
                if (meta.getVar() != null && !meta.getVar().trim().isEmpty()) {
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
            Date date;
            String valueStr = value.toString();
            if (NumberUtils.isNumber(valueStr) && "timestamp".equalsIgnoreCase(in)) {
                if (valueStr.length() != 10 && valueStr.length() != 13) {
                    return "";
                }
                date = new Date(Long.parseLong(valueStr.length() == 10 ? valueStr + "000" : valueStr));
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
                if (!value.toString().contains(".")) {
                    cell.setCellValue(Long.parseLong(value.toString()));
                } else {
                    cell.setCellValue(new BigDecimal(value.toString()).doubleValue());
                }
            } else if (meta.isValueTypeDate()) {
                cell.setCellValue(formatDate(value, meta.getFormatImport(), meta.getFormatExport()));
            } else if (meta.isValueTypeDateTime()) {
                log.info("无处理");
            } else {
                cell.setCellValue(value.toString());
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
     * <p>
     * 在指定的Excel工作表中，找到需要插入新行的位置，并新建一个XSSFRow对象。
     * 如果指定位置已经存在行，则将其下移一行。
     *
     * @param sheet    Excel工作表对象
     * @param rowIndex 需要插入新行的行索引
     * @return 返回新创建的XSSFRow对象
     */
    private XSSFRow createRow(XSSFSheet sheet, int rowIndex) {
        XSSFRow row;
        if (sheet.getRow(rowIndex) != null) {
            int lastRowNo = sheet.getLastRowNum();
            sheet.shiftRows(rowIndex, lastRowNo, 1);
        }
        row = sheet.createRow(rowIndex);
        return row;
    }

    public void createRowWithPreRowStyle(XSSFSheet sheet, Integer rowIndex) {
        XSSFRow preRow = sheet.getRow(rowIndex - 1);
        XSSFRow newRow = createRow(sheet, rowIndex);
        newRow.setHeight(preRow.getHeight());
        for (int cellIndex = 0; cellIndex < preRow.getLastCellNum(); cellIndex++) {
            XSSFCell templateCell = preRow.getCell(cellIndex);
            // 这里里要createCell，否则getCell返回空值
            XSSFCell newCell = newRow.createCell(cellIndex);
            newCell.setCellStyle(templateCell.getCellStyle());
        }
    }

    /**
     * 生成导出模板文件
     * <p>
     * 根据提供的Excel工作簿、工作表名称和导出列信息，生成一个包含表头和占位符的Excel模板文件。
     *
     * @param workbook      Excel工作簿对象
     * @param sheetName     工作表名称
     * @param exportColumns 导出列信息列表，每个元素包含列标题、对齐方式、描述和宽度等信息
     */
    public void generateTemplateFile(XSSFWorkbook workbook, String sheetName, List<ExportColumn> exportColumns) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        int profundity;// 计算最大深度
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
                    headerStyle.setAlignment(ExcelAlignmentEnum.getHorizontalAlignment(column.getAlign()));
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
            style.setAlignment(ExcelAlignmentEnum.getHorizontalAlignment(column.getAlign()));
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
     * 获取表头单元格样式
     * <p>
     * 设置表头单元格的样式，包括字体、背景色、边框和方向。
     * 字体为仿宋、12号、加粗，背景色为浅灰色，边框为上下左右，方向为水平居中和垂直居中。
     *
     * @param workbook Excel工作簿对象
     * @return 返回设置好的表头单元格样式对象
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
     * 获取表头样式
     * <p>
     * 设置单元格的字体为仿宋、11号，并添加边框（上下左右），同时设置文本对齐方式为水平居中和垂直居中。
     *
     * @param workbook Excel工作簿对象，用于创建单元格样式
     * @return 返回设置好的单元格样式对象
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
     * 从Excel中读取占位符和变量定义
     * <p>
     * 从指定的Excel工作表中读取占位符和变量定义，并将它们存储在一个映射中。
     *
     * @param sheet Excel工作表对象，包含占位符和变量定义
     * @return 返回包含占位符和变量定义的映射，键为占位符名称，值为对应的PlaceholderMeta对象
     */
    public Map<String, PlaceholderMeta> readPlaceholderMeta(XSSFSheet sheet) {
        int lastRowIndex = sheet.getLastRowNum();
        Map<String, PlaceholderMeta> map = new HashMap<>(lastRowIndex);
        // 跳过第一行，标题行
        for (int i = 1; i <= lastRowIndex; i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            PlaceholderMeta placeholderMeta = new PlaceholderMeta();
            if (row.getCell(0) != null) {
                placeholderMeta.setPlaceholder(row.getCell(0).getStringCellValue());
            }
            if (row.getCell(1) != null) {
                placeholderMeta.setVar(row.getCell(1).getStringCellValue());
            }
            if (row.getCell(2) != null) {
                placeholderMeta.setListVar(row.getCell(2).getStringCellValue());
            }
            if (row.getCell(3) != null) {
                placeholderMeta.setConstValue(row.getCell(3).getStringCellValue());
            }
            if (row.getCell(4) != null) {
                placeholderMeta.setExpression(row.getCell(4).getStringCellValue());
            }
            if (row.getCell(5) != null) {
                placeholderMeta.setValueType(row.getCell(5).getStringCellValue());
            }
            if (row.getCell(6) != null) {
                placeholderMeta.setValueComputeMode(row.getCell(6).getStringCellValue());
            }
            placeholderMeta.setIsList(getBoolean(row.getCell(7)));
            placeholderMeta.setIsMerge(getBoolean(row.getCell(8)));
            placeholderMeta.setIsUnique(getBoolean(row.getCell(9)));
            placeholderMeta.setIsImage(getBoolean(row.getCell(10)));
            if (row.getCell(11) != null) {
                placeholderMeta.setImageWidth(row.getCell(11).getNumericCellValue());
            }
            if (row.getCell(12) != null) {
                placeholderMeta.setImageHeight(row.getCell(12).getNumericCellValue());
            }
            if (row.getCell(13) != null) {
                placeholderMeta.setImageSource(row.getCell(13).getStringCellValue());
            }
            if (row.getCell(14) != null) {
                placeholderMeta.setBarcodeCode(row.getCell(14).getStringCellValue());
            }
            if (row.getCell(15) != null) {
                placeholderMeta.setFormatImport(row.getCell(15).getStringCellValue());
            }
            if (row.getCell(16) != null) {
                placeholderMeta.setFormatExport(row.getCell(16).getStringCellValue());
            }
            if (row.getCell(17) != null) {
                placeholderMeta.setDescription(row.getCell(17).getStringCellValue());
            }
            if (row.getCell(18) != null) {
                placeholderMeta.setRemark(row.getCell(18).getStringCellValue());
            }
            placeholderMeta.setIsDynamic(getBoolean(row.getCell(19)));
            if (row.getCell(20) != null) {
                placeholderMeta.setDynamicType(row.getCell(20).getStringCellValue());
            }
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
