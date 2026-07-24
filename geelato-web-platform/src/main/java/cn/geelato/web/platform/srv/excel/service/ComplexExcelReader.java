package cn.geelato.web.platform.srv.excel.service;

import cn.geelato.meta.Attachment;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.common.FileHandler;
import cn.geelato.web.platform.srv.excel.entity.ComplexCellData;
import cn.geelato.web.platform.srv.excel.entity.ComplexListColumnData;
import cn.geelato.web.platform.srv.excel.entity.ComplexListData;
import cn.geelato.web.platform.srv.excel.exception.FileException;
import cn.geelato.web.platform.srv.excel.exception.FileTypeNotSupportedException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 复杂Excel读取工具
 * <p>
 * 按"复杂导入"模板配置（固定单元格、列表区域、列表字段）读取Excel文件，
 * 采用位置（单元格引用/列字母+行号）定位数据。模板配置JSON结构为：
 * <pre>
 * {
 *   "tableData":       [ComplexCellData],       // 固定单元格
 *   "listData":        [ComplexListData],       // 列表区域
 *   "listColumnData":  [ComplexListColumnData]  // 列表字段（列位置）
 * }
 * </pre>
 * position 形如 "A1" 或合并区间 "A1:D2"；startRow 为 1-based 行号；列字母统一大写。
 *
 * @author diabl
 */
@Component
@Slf4j
public class ComplexExcelReader {

    @Autowired
    private FileHandler fileHandler;

    /**
     * 读取复杂Excel
     * <p>
     * 打开业务数据文件，按模板配置（固定单元格、列表区域）读取数据。
     *
     * @param businessFile   业务数据文件
     * @param cellDataList   固定单元格配置（tableData）
     * @param listDataList   列表区域配置（listData）
     * @param listColumnList 列表字段配置（listColumnData）
     * @return 解析结果（扁平化）：固定单元格字段与列表区域（键=列表fieldName，值=行数组）平铺在同一层级
     */
    public Map<String, Object> readComplexExcel(Attachment businessFile, List<ComplexCellData> cellDataList, List<ComplexListData> listDataList, List<ComplexListColumnData> listColumnList) throws IOException {
        Map<String, Object> result = new LinkedHashMap<>();
        File file = fileHandler.toFile(businessFile);
        String contentType = Files.probeContentType(file.toPath());
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            Sheet sheet;
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                sheet = workbook.getSheetAt(0);
                sheet.setForceFormulaRecalculation(true);
                HSSFFormulaEvaluator evaluator = (HSSFFormulaEvaluator) workbook.getCreationHelper().createFormulaEvaluator();
                readComplexSheet(sheet, evaluator, cellDataList, listDataList, listColumnList, result);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                sheet = workbook.getSheetAt(0);
                sheet.setForceFormulaRecalculation(true);
                XSSFFormulaEvaluator evaluator = (XSSFFormulaEvaluator) workbook.getCreationHelper().createFormulaEvaluator();
                readComplexSheet(sheet, evaluator, cellDataList, listDataList, listColumnList, result);
                workbook.close();
                workbook = null;
            } else {
                throw new FileTypeNotSupportedException("Complex Business Data, Excel Type: " + contentType);
            }
        } catch (IOException ex) {
            throw new FileException("Complex Business Data, Excel Reader Failed! " + ex.getMessage());
        } finally {
            if (bufferedInputStream != null) {
                bufferedInputStream.close();
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (workbook != null) {
                workbook.close();
            }
        }

        return result;
    }

    /**
     * 按模板配置解析工作表
     * <p>
     * 解析结果扁平化输出：固定单元格字段直接作为顶层键，列表区域以列表fieldName为键、
     * 行数据列表为值，平铺到同一层级，形如：
     * <pre>
     * {
     *   "customerId": "...",            // 固定单元格
     *   "ilEcoCargo": [ {...}, {...} ]  // 列表区域
     * }
     * </pre>
     *
     * @param sheet          工作表
     * @param evaluator      公式求值器
     * @param cellDataList   固定单元格配置
     * @param listDataList   列表区域配置
     * @param listColumnList 列表字段配置
     * @param result         解析结果（写回扁平化数据）
     */
    private void readComplexSheet(Sheet sheet, FormulaEvaluator evaluator, List<ComplexCellData> cellDataList, List<ComplexListData> listDataList, List<ComplexListColumnData> listColumnList, Map<String, Object> result) {
        // 列表字段按 listFieldName 分组
        Map<String, List<ComplexListColumnData>> listColumnMap = new LinkedHashMap<>();
        if (listColumnList != null && !listColumnList.isEmpty()) {
            for (ComplexListColumnData column : listColumnList) {
                if (column.getListFieldName() != null && !column.getListFieldName().isEmpty()) {
                    listColumnMap.computeIfAbsent(column.getListFieldName(), k -> new ArrayList<>()).add(column);
                }
            }
        }
        // 固定单元格：position 取合并区域左上角单元格的值，直接作为顶层键
        if (cellDataList != null && !cellDataList.isEmpty()) {
            for (ComplexCellData cell : cellDataList) {
                String fieldName = cell.getFieldName();
                String position = cell.getPosition();
                if (fieldName == null || fieldName.isEmpty() || position == null || position.isEmpty()) {
                    continue;
                }
                // position 形如 A1 或 A1:D2，取 ":" 前半部分作为单元格引用
                String refStr = position.split(":")[0];
                CellReference ref = new CellReference(refStr);
                Row row = sheet.getRow(ref.getRow());
                Cell poiCell = row == null ? null : row.getCell(ref.getCol());
                result.put(fieldName, getCellValue(poiCell, evaluator));
            }
        }
        // 列表区域：以列表fieldName为键、行数据列表为值，平铺到顶层；从 startRow（1-based）开始，到该区域出现空行结束
        if (listDataList != null && !listDataList.isEmpty()) {
            for (ComplexListData list : listDataList) {
                String listFieldName = list.getFieldName();
                if (listFieldName == null || listFieldName.isEmpty()) {
                    continue;
                }
                List<ComplexListColumnData> columns = listColumnMap.getOrDefault(listFieldName, new ArrayList<>());
                List<Map<String, Object>> rows = readComplexList(sheet, evaluator, list, columns);
                result.put(listFieldName, rows);
            }
        }
    }

    /**
     * 读取列表区域
     *
     * @param sheet     工作表
     * @param evaluator 公式求值器
     * @param list      列表区域配置
     * @param columns   列表字段配置
     * @return 行数据列表
     */
    private List<Map<String, Object>> readComplexList(Sheet sheet, FormulaEvaluator evaluator, ComplexListData list, List<ComplexListColumnData> columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (columns == null || columns.isEmpty()) {
            return rows;
        }
        int startRow = list.getStartRow();
        if (startRow < 1) {
            startRow = 1;
        }
        // 预解析每个字段的列索引：position 形如 "A" 或区间 "A:C"，取 ":" 前半部分作为列字母
        int[] colIndices = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            ComplexListColumnData column = columns.get(i);
            String position = column.getPosition();
            int colIdx = -1;
            if (position != null && !position.isEmpty()) {
                String colLetter = position.split(":")[0];
                CellReference ref = new CellReference(colLetter + "1");
                colIdx = ref.getCol();
            }
            colIndices[i] = colIdx;
        }
        // 从 startRow（1-based）开始向下读取，整行所有字段为空则结束
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIdx = startRow - 1; rowIdx <= lastRowNum; rowIdx++) {
            Row row = sheet.getRow(rowIdx);
            Map<String, Object> rowMap = new LinkedHashMap<>();
            boolean hasValue = false;
            for (int i = 0; i < columns.size(); i++) {
                ComplexListColumnData column = columns.get(i);
                String fieldName = column.getFieldName();
                if (fieldName == null || fieldName.isEmpty() || colIndices[i] < 0) {
                    continue;
                }
                Cell poiCell = row == null ? null : row.getCell(colIndices[i]);
                Object value = getCellValue(poiCell, evaluator);
                if (value != null) {
                    String strValue = String.valueOf(value);
                    if (!strValue.isEmpty()) {
                        hasValue = true;
                    }
                }
                rowMap.put(fieldName, value);
            }
            if (!hasValue) {
                break;
            }
            rows.add(rowMap);
        }

        return rows;
    }

    /**
     * 读取单元格的值（公式先求值）
     * <p>
     * 日期格式的数值单元格会被格式化为与Excel显示一致的字符串（如 "2026/7/24"），
     * 避免返回 {@link java.util.Date} 导致序列化为带时区偏移的 ISO-8601 字符串。
     *
     * @param cell      POI单元格
     * @param evaluator 公式求值器
     * @return 单元格值，空单元格返回 null
     */
    private Object getCellValue(Cell cell, FormulaEvaluator evaluator) {
        if (cell == null) {
            return null;
        }
        if (cell.getCellType() == CellType.FORMULA && evaluator != null) {
            try {
                CellValue cv = evaluator.evaluate(cell);
                if (cv == null) {
                    return null;
                }
                switch (cv.getCellType()) {
                    case NUMERIC:
                        return cv.getNumberValue();
                    case STRING:
                        return cv.getStringValue();
                    case BOOLEAN:
                        return cv.getBooleanValue();
                    default:
                        return null;
                }
            } catch (Exception ex) {
                log.error(ex.getMessage(), ex);
                return null;
            }
        }
        switch (cell.getCellType()) {
            case NUMERIC:
                // 日期格式：统一格式化为 yyyy-MM-dd HH:mm:ss，避免Date序列化时的时区偏移与格式不统一问题
                if (DateUtil.isCellDateFormatted(cell)) {
                    return new SimpleDateFormat(DateUtils.DATETIME).format(cell.getDateCellValue());
                }
                return cell.getNumericCellValue();
            case STRING:
                return cell.getStringCellValue();
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }
}
