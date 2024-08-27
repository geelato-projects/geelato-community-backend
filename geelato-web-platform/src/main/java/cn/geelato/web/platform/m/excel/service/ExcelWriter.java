package cn.geelato.web.platform.m.excel.service;

import cn.geelato.core.script.js.JsProvider;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.excel.entity.CellMeta;
import cn.geelato.web.platform.m.excel.entity.PlaceholderMeta;
import cn.geelato.web.platform.m.excel.entity.RowMeta;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class ExcelWriter {

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
    public void writeSheet(HSSFSheet sheet, Map<String, PlaceholderMeta> placeholderMetaMap, List<Map> valueMapList, Map valueMap) {
        int lastRowIndex = sheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRowIndex; rowIndex++) {
            // 按行扫描处理
            HSSFRow row = sheet.getRow(rowIndex);
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
    public void writeSheet(HSSFSheet sheet, Map<String, PlaceholderMeta> placeholderMetaMap, Map valueMap) {
        int lastRowIndex = sheet.getLastRowNum();
        for (int rowIndex = 0; rowIndex <= lastRowIndex; rowIndex++) {
            // 按行扫描处理
            HSSFRow row = sheet.getRow(rowIndex);
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

    private RowMeta parseTemplateRow(HSSFRow row, Map<String, PlaceholderMeta> placeholderMetaMap) {
        RowMeta rowMeta = new RowMeta();
        // 将列表与非列表cellMeta分组，存在不同的ArrayList中
        // 列表类占位符单元格索引位置Map，列表变量名为key，列表为value
        Map<String, List<CellMeta>> listCellMetaMap = new HashMap<>();
        // 非列表类占位符单元格索引位置list
        List<CellMeta> notListCellIndexes = new LinkedList<>();
        short lastCellNum = row.getLastCellNum();
        for (int cellIndex = 0; cellIndex < lastCellNum; cellIndex++) {
            HSSFCell cell = row.getCell(cellIndex);
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
    private int setRowValue(HSSFSheet sheet, int rowIndex, RowMeta rowMeta, Map valueMap) {
        HSSFRow row = sheet.getRow(rowIndex);
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
        int newRowCount = 0;
        while (newRowCount < listMaxRowCount - 1) {
            newRowCount++;
            createRowWithPreRowStyle(sheet, rowIndex + newRowCount);
        }

        // ----2.2 为模板行及动态创建的行设置值
        // 设置类型为list的cell
        for (String key : rowMeta.getListCellMetaMap().keySet()) {
            List<Map> valueList = (List) valueMap.get(key);
            List<CellMeta> cellMetaList = rowMeta.getListCellMetaMap().get(key);
            int currentRowIndexInList = 0;
            // 设置列表每行数据的值
            if (valueList != null) {
                for (Map listValueMap : valueList) {
                    HSSFRow newRow = sheet.getRow(rowIndex + currentRowIndexInList);
                    currentRowIndexInList++;
                    if (cellMetaList != null) {
                        for (CellMeta cellMeta : cellMetaList) {
                            HSSFCell cell = newRow.getCell(cellMeta.getIndex());
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
    public void setMergeScope(HSSFSheet sheet, int rowIndex, List<CellMeta> cellMetaList, Map valueMap, List<Map> valueList, List<List<Integer>> mergeScope) {
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

    private PlaceholderMeta getPlaceholderMeta(HSSFCell cell, Map<String, PlaceholderMeta> placeholderMetaMap) {
        if (cell == null) {
            return null;
        }
        CellType cellType = cell.getCellType();
        String cellValue = cellType.equals(cellType) ? cell.getStringCellValue() : "";
        return placeholderMetaMap.get(cellValue);
    }


    private void setCellValue(HSSFCell cell, PlaceholderMeta meta, Map valueMap, Map listValueMap) {
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

    private void setCellValueByValueType(HSSFCell cell, PlaceholderMeta meta, Object value) {
        if (value != null) {
            if (meta.isValueTypeNumber()) {
                if (!value.toString().contains(".")) {
                    cell.setCellValue(Long.parseLong(value.toString()));
                } else {
                    cell.setCellValue(new BigDecimal(value.toString()).doubleValue());
                }
            } else if (meta.isValueTypeDate()) {
                // value 应为时间戳
                cell.setCellValue(ExcelCommonUtils.DATE_FORMAT.format(value));
            } else if (meta.isValueTypeDateTime()) {
                // value 应为时间戳
                cell.setCellValue(ExcelCommonUtils.DATE_TIME_FORMAT.format(value));
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
     *
     * @param sheet
     * @param rowIndex
     * @return
     */
    private HSSFRow createRow(HSSFSheet sheet, int rowIndex) {
        HSSFRow row = null;
        if (sheet.getRow(rowIndex) != null) {
            int lastRowNo = sheet.getLastRowNum();
            sheet.shiftRows(rowIndex, lastRowNo, 1);
        }
        row = sheet.createRow(rowIndex);
        return row;
    }

    public HSSFRow createRowWithPreRowStyle(HSSFSheet sheet, Integer rowIndex) {
        HSSFRow preRow = sheet.getRow(rowIndex - 1);
        HSSFRow newRow = createRow(sheet, rowIndex);
        newRow.setHeight(preRow.getHeight());
        for (int cellIndex = 0; cellIndex < preRow.getLastCellNum(); cellIndex++) {
            HSSFCell templateCell = preRow.getCell(cellIndex);
            // 这里里要createCell，否则getCell返回空值
            HSSFCell newCell = newRow.createCell(cellIndex);
            newCell.setCellStyle(templateCell.getCellStyle());
        }
        return newRow;
    }

    /**
     * 从excel中读取占位符、变量定义
     *
     * @param sheet
     * @return
     */
    public Map<String, PlaceholderMeta> readPlaceholderMeta(HSSFSheet sheet) {
        int lastRowIndex = sheet.getLastRowNum();
        Map<String, PlaceholderMeta> map = new HashMap<String, PlaceholderMeta>(lastRowIndex);
        // 跳过第一行，标题行
        for (int i = 1; i <= lastRowIndex; i++) {
            HSSFRow row = sheet.getRow(i);
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
            placeholderMeta.setDescription(row.getCell(13).getStringCellValue());
            if (validatePlaceholderMeta(placeholderMeta)) {
                map.put(placeholderMeta.getPlaceholder(), placeholderMeta);
            }
        }
        return map;
    }

    public boolean validatePlaceholderMeta(PlaceholderMeta placeholderMeta) {
        return true;
    }

    private boolean getBoolean(HSSFCell cell) {
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
