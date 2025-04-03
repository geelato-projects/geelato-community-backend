package cn.geelato.web.platform.m.excel.service;

import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.web.platform.exception.file.FileContentIsEmptyException;
import cn.geelato.web.platform.exception.file.FileContentReadFailedException;
import cn.geelato.web.platform.m.excel.entity.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author diabl
 * @description: XSSFSheet, xlsx
 */
@Component
@Slf4j
public class ExcelXSSFReader {
    @Lazy
    @Autowired
    private ExcelCommonUtils excelCommonUtils;

    /**
     * 读取业务元数据
     * <p>
     * 从Excel工作表中读取业务元数据，并将其按表格分类存储。
     *
     * @param sheet Excel工作表对象，包含业务元数据
     * @return 返回包含业务元数据的映射，键为表格名称，值为对应的业务元数据列表
     * @throws FileContentReadFailedException 如果在读取过程中发生错误，将抛出此异常
     */
    public Map<String, List<BusinessMeta>> readBusinessMeta(XSSFSheet sheet) {
        Map<String, List<BusinessMeta>> tableMaps = new HashMap<>();
        int lastRowIndex = sheet.getLastRowNum();
        List<BusinessMeta> columns = new ArrayList<>();
        List<String> tables = new ArrayList<>();
        // 跳过第一行，标题行
        for (int i = 1; i <= lastRowIndex; i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            try {
                BusinessMeta meta = new BusinessMeta();
                meta.setTableName(row.getCell(0).getStringCellValue());
                meta.setColumnName(row.getCell(1).getStringCellValue());
                meta.setEvaluation(row.getCell(2).getStringCellValue());
                meta.setConstValue(row.getCell(3).getStringCellValue());
                meta.setVariableValue(row.getCell(4).getStringCellValue());
                meta.setExpression(row.getCell(5).getStringCellValue());
                meta.setDictCode(row.getCell(6).getStringCellValue());
                meta.setPrimaryValue(row.getCell(7).getStringCellValue());
                meta.setRemark(row.getCell(8).getStringCellValue());
                columns.add(meta);
                // 表格
                if (!tables.contains(meta.getTableName())) {
                    tables.add(meta.getTableName());
                }
            } catch (Exception ex) {
                throw new FileContentReadFailedException("Business Meta, Read Failed In (" + i + ").");
            }
        }
        // 按表格分类
        for (String tableName : tables) {
            List<BusinessMeta> metas = new ArrayList<>();
            for (BusinessMeta meta : columns) {
                if (tableName.equalsIgnoreCase(meta.getTableName())) {
                    metas.add(meta);
                }
            }
            tableMaps.put(tableName, metas);
        }

        return tableMaps;
    }

    /**
     * 读取业务数据类型数据
     * <p>
     * 从Excel工作表中读取业务数据类型数据，并将其存储在一个映射中。
     *
     * @param sheet Excel工作表对象，包含业务数据类型数据
     * @return 返回包含业务数据类型数据的映射，键为业务数据类型名称，值为对应的BusinessTypeData对象
     * @throws FileContentReadFailedException 如果在读取过程中发生错误，将抛出此异常
     */
    public Map<String, BusinessTypeData> readBusinessTypeData(XSSFSheet sheet) {
        int lastRowIndex = sheet.getLastRowNum();
        Map<String, BusinessTypeData> metaMap = new HashMap<>(lastRowIndex);
        // 跳过第一行，标题行
        for (int i = 1; i <= lastRowIndex; i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            try {
                BusinessTypeData meta = new BusinessTypeData();
                meta.setName(row.getCell(0).getStringCellValue());
                meta.setType(row.getCell(1).getStringCellValue());
                meta.setFormat(row.getCell(2).getStringCellValue());
                // 多值分解，全局规则
                meta.setMultiSeparator(row.getCell(3).getStringCellValue());
                meta.setMultiScene(row.getCell(4).getStringCellValue());
                // 规则，单格规则
                String rules = row.getCell(5).getStringCellValue();
                meta.setTypeRuleData(excelCommonUtils.readBusinessTypeRuleData(rules));
                meta.setRemark(row.getCell(6).getStringCellValue());
                metaMap.put(meta.getName(), meta);
            } catch (Exception ex) {
                throw new FileContentReadFailedException("Business Data Type, Read Failed In (" + i + ").");
            }
        }

        return metaMap;
    }

    /**
     * 读取业务数据清洗规则
     * <p>
     * 从Excel工作表中读取业务数据清洗规则，并将其存储在一个有序集合中返回。
     *
     * @param sheet Excel工作表对象，包含业务数据清洗规则
     * @return 返回包含业务数据清洗规则的集合，集合中的元素为映射，映射的键为规则的顺序号，值为对应的业务数据清洗规则对象
     * @throws FileContentReadFailedException 如果在读取过程中发生错误，将抛出此异常
     */
    public Set<Map<Integer, BusinessTypeRuleData>> readBusinessTypeRuleData(XSSFSheet sheet) {
        int lastRowIndex = sheet.getLastRowNum();
        List<BusinessTypeRuleData> typeRuleDataList = new ArrayList<>();
        // 跳过第一行，标题行
        for (int i = 2; i <= lastRowIndex; i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            try {
                BusinessTypeRuleData meta = new BusinessTypeRuleData();
                meta.setColumnName(row.getCell(0).getStringCellValue());
                meta.setType(row.getCell(1).getStringCellValue());
                meta.setRule(row.getCell(2).getStringCellValue());
                meta.setGoal(row.getCell(3).getStringCellValue());
                XSSFCell cell4 = row.getCell(4);
                if (cell4 != null) {
                    if (CellType.BOOLEAN.equals(cell4.getCellType())) {
                        meta.setRetain(cell4.getBooleanCellValue());
                    } else if (CellType.STRING.equals(cell4.getCellType())) {
                        meta.setRetain("TRUE".equalsIgnoreCase(cell4.getStringCellValue()));
                    }
                }
                XSSFCell cell5 = row.getCell(5);
                if (cell5 != null) {
                    if (CellType.NUMERIC.equals(cell5.getCellType())) {
                        meta.setOrder((int) cell5.getNumericCellValue());
                    } else if (CellType.STRING.equals(cell5.getCellType())) {
                        meta.setOrder(Integer.parseInt(cell5.getStringCellValue()));
                    }
                }
                typeRuleDataList.add(meta);
            } catch (Exception ex) {
                throw new FileContentReadFailedException("Business Data Type Rule, Read Failed In (" + i + "). " + ex.getMessage());
            }
        }
        Set<Map<Integer, BusinessTypeRuleData>> typeRuleDataSet = new LinkedHashSet<>();
        if (!typeRuleDataList.isEmpty()) {
            typeRuleDataList.sort(Comparator.comparingInt(BusinessTypeRuleData::getOrder));
            for (int i = 0; i < typeRuleDataList.size(); i++) {
                Map<Integer, BusinessTypeRuleData> ruleDataMap = new HashMap<>();
                ruleDataMap.put(i + 1, typeRuleDataList.get(i));
                typeRuleDataSet.add(ruleDataMap);
            }
        }

        return typeRuleDataSet;
    }

    /**
     * 读取业务数据
     * <p>
     * 从Excel工作表中读取业务数据，并根据提供的数据类型映射对数据进行处理。
     *
     * @param sheet               Excel工作表对象，包含业务数据
     * @param evaluator           用于计算Excel公式中单元格的值
     * @param businessTypeDataMap 数据类型映射，键为数据类型名称，值为对应的BusinessTypeData对象
     * @return 返回包含业务数据的列表，每个元素是一个包含业务数据键值对的映射
     * @throws FileContentIsEmptyException 如果业务数据表头为空，将抛出此异常
     */
    public List<Map<String, BusinessData>> readBusinessData(XSSFSheet sheet, XSSFFormulaEvaluator evaluator, Map<String, BusinessTypeData> businessTypeDataMap) {
        int lastRowIndex = sheet.getLastRowNum();
        int lastCellNum;
        // 第一行
        List<BusinessColumnMeta> headers = new ArrayList<>();
        XSSFRow firstRow = sheet.getRow(0);
        if (firstRow != null) {
            lastCellNum = firstRow.getLastCellNum();
            for (int i = 0; i < lastCellNum; i++) {
                XSSFCell cell = firstRow.getCell(i);
                if (cell != null) {
                    String cellValue = cell.getStringCellValue();
                    if (Strings.isNotBlank(cellValue)) {
                        BusinessColumnMeta busColMeta = new BusinessColumnMeta();
                        busColMeta.setIndex(i);
                        busColMeta.setBusinessTypeData(businessTypeDataMap.get(cellValue));
                        if (busColMeta.getBusinessTypeData() != null) {
                            headers.add(busColMeta);
                        }
                    }
                }
            }
        } else {
            throw new FileContentIsEmptyException("Business Data Table Header is Empty.");
        }
        // 实体数据
        List<Map<String, BusinessData>> businessDataMapList = new ArrayList<>();
        for (int i = 1; i <= lastRowIndex; i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            Map<String, BusinessData> businessDataMap = new HashMap<>();
            for (BusinessColumnMeta colMeta : headers) {
                BusinessTypeData data = colMeta.getBusinessTypeData();
                // 定位、格式
                BusinessData businessData = new BusinessData();
                businessData.setXIndex(colMeta.getIndex());
                businessData.setYIndex(i);
                businessData.setBusinessTypeData(data);
                Object cellValue = null;
                // 每个格子的数据
                XSSFCell cell = row.getCell(colMeta.getIndex());
                if (cell != null) {
                    try {
                        if (data.isColumnTypeString()) {
                            if (CellType.NUMERIC.equals(cell.getCellType())) {
                                cellValue = String.valueOf(BigDecimal.valueOf(cell.getNumericCellValue()));
                            } else if (CellType.STRING.equals(cell.getCellType())) {
                                cellValue = cell.getStringCellValue();
                            }
                        } else if (data.isColumnTypeNumber()) {
                            if (CellType.NUMERIC.equals(cell.getCellType())) {
                                String value = String.valueOf(BigDecimal.valueOf(cell.getNumericCellValue()));
                                cellValue = ExcelCommonUtils.stringToNumber(value);
                            } else if (CellType.STRING.equals(cell.getCellType())) {
                                cellValue = ExcelCommonUtils.stringToNumber(cell.getStringCellValue());
                            } else if (CellType.FORMULA.equals(cell.getCellType())) {
                                CellValue cellV = evaluator.evaluate(cell);
                                if (CellType.NUMERIC.equals(cellV.getCellType())) {
                                    String value = String.valueOf(BigDecimal.valueOf(cellV.getNumberValue()));
                                    cellValue = ExcelCommonUtils.stringToNumber(value);
                                } else if (CellType.STRING.equals(cellV.getCellType())) {
                                    cellValue = ExcelCommonUtils.stringToNumber(cellV.getStringValue());
                                }
                            }
                        } else if (data.isColumnTypeBoolean()) {
                            if (CellType.BOOLEAN.equals(cell.getCellType())) {
                                cellValue = cell.getBooleanCellValue();
                            } else if (CellType.STRING.equals(cell.getCellType()) && Strings.isNotBlank(data.getFormat())) {
                                cellValue = data.getFormat().equalsIgnoreCase(cell.getStringCellValue());
                            } else if (CellType.NUMERIC.equals(cell.getCellType()) && Strings.isNotBlank(data.getFormat())) {
                                cellValue = Objects.equals(data.getFormat(), cell.getStringCellValue());
                            } else if (CellType.NUMERIC.equals(cell.getCellType())) {
                                cellValue = cell.getNumericCellValue() > 0;
                            } else {
                                cellValue = false;
                            }
                        } else if (data.isColumnTypeDateTime()) {
                            if (CellType.NUMERIC.equals(cell.getCellType())) {
                                cellValue = cell.getDateCellValue();
                            } else if (Strings.isNotBlank(data.getFormat())) {
                                cellValue = new SimpleDateFormat(data.getFormat()).parse(cell.getStringCellValue());
                            }
                        }
                        businessData.setValue(cellValue);
                        businessData.setPrimevalValue(cellValue);
                        businessData.setTransitionValue(cellValue);
                    } catch (Exception ex) {
                        businessData.addErrorMsg(ex.getMessage());
                    }
                }
                businessDataMap.put(data.getName(), businessData);
            }
            if (!businessDataMap.isEmpty()) {
                businessDataMapList.add(businessDataMap);
            }
        }

        return businessDataMapList;
    }

    /**
     * 往业务数据中写入校验批注
     * <p>
     * 根据提供的业务数据，在Excel工作表中为校验不通过的数据单元格添加批注。
     *
     * @param sheet               Excel工作表对象，用于写入批注
     * @param style               单元格样式，用于设置校验不通过单元格的样式
     * @param businessDataMapList 包含业务数据的列表，每个元素是一个包含业务数据键值对的映射
     */
    public void writeBusinessData(XSSFSheet sheet, XSSFCellStyle style, List<Map<String, BusinessData>> businessDataMapList) {
        Map<String, BusinessData> mapSet = new HashMap<>();
        for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
            for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                BusinessData businessData = businessDataEntry.getValue();
                if (businessData != null && !businessData.isValidate()) {
                    String key = String.format("Y.%s;X.%s", businessData.getYIndex(), businessData.getXIndex());
                    BusinessData msgData = (mapSet.containsKey(key) && mapSet.get(key) != null) ? mapSet.get(key) : new BusinessData();
                    msgData.setYIndex(businessData.getYIndex());
                    msgData.setXIndex(businessData.getXIndex());
                    msgData.addAllErrorMsg(businessData.getErrorMsg());
                    mapSet.put(key, msgData);
                }
            }
        }
        // 清理 批注
        cleanSheetStyle(sheet);
        // 实体数据
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            XSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            for (Map.Entry<String, BusinessData> businessDataEntry : mapSet.entrySet()) {
                BusinessData businessData = businessDataEntry.getValue();
                if (businessData != null && businessData.getYIndex() == i) {
                    XSSFCell cell = row.getCell(businessData.getXIndex());
                    if (cell == null) {
                        cell = row.createCell(businessData.getXIndex());
                    }
                    cell.setCellStyle(style);
                    ClientAnchor anchor = new XSSFClientAnchor();
                    anchor.setCol1(cell.getColumnIndex());
                    anchor.setRow1(cell.getRowIndex());
                    anchor.setCol2(cell.getColumnIndex() + 10);
                    anchor.setRow2(cell.getRowIndex() + 10);
                    Drawing<XSSFShape> drawing = sheet.createDrawingPatriarch();
                    XSSFComment comment = (XSSFComment) drawing.createCellComment(anchor);
                    comment.setString(new XSSFRichTextString(String.join("；\r\n", businessData.getErrorMsg())));
                    cell.setCellComment(comment);
                }
            }
        }

    }

    /**
     * 插入工作表，记录唯一性约束校验失败的数据
     * <p>
     * 当唯一性约束校验失败时，将校验失败的数据插入到一个新的工作表中。
     *
     * @param workbook     Excel工作簿对象，用于创建新的工作表
     * @param repeatedData 包含重复数据及其数量的映射，键为列元数据对象，值为数据值及其数量的映射
     */
    public void writeRepeatedData(XSSFWorkbook workbook, Map<ColumnMeta, Map<Object, Long>> repeatedData) {
        if (repeatedData != null && !repeatedData.isEmpty()) {
            XSSFSheet sheet = workbook.createSheet("唯一约束，导入数据重复值及数量"); // 创建新的工作表
            int x = 0;
            for (Map.Entry<ColumnMeta, Map<Object, Long>> columnMetaMapEntry : repeatedData.entrySet()) {
                // 表头
                ColumnMeta meta = columnMetaMapEntry.getKey();
                createRowCell(sheet, 0, x, String.format("%s(%s:%s)", meta.getTitle(), meta.getTableName(), meta.getFieldName()));
                CellRangeAddress rangea = new CellRangeAddress(0, 0, x, x + 1); // A1到D1的范围
                sheet.addMergedRegion(rangea);
                createRowCell(sheet, 1, x, "值");
                createRowCell(sheet, 1, x + 1, "值数量");
                // 内容
                int y = 2;
                Map<Object, Long> objectLongMap = columnMetaMapEntry.getValue();
                for (Map.Entry<Object, Long> mapEntry : objectLongMap.entrySet()) {
                    createRowCell(sheet, y, x, mapEntry.getKey());
                    createRowCell(sheet, y, x + 1, mapEntry.getValue());
                    y = y + 1;
                }
                x = x + 2;
            }
        }
    }

    /**
     * 创建表格行和单元格
     * <p>
     * 在给定的Excel工作表中创建或获取指定行和列的单元格，并设置其值。
     *
     * @param sheet Excel工作表对象
     * @param y     行索引
     * @param x     列索引
     * @param value 要设置的单元格值
     */
    private void createRowCell(XSSFSheet sheet, int y, int x, Object value) {
        XSSFRow row = sheet.getRow(y);
        if (row == null) {
            row = sheet.createRow(y);
        }
        XSSFCell cell = row.getCell(x);
        if (cell == null) {
            cell = row.createCell(x);
        }
        cell.setCellValue(String.valueOf(value));
    }

    /**
     * 清理Excel工作表中的批注和格式
     * <p>
     * 遍历指定Excel工作表中的所有单元格，移除其中的批注，并清理单元格的格式。
     *
     * @param sheet 要清理的Excel工作表对象
     */
    private void cleanSheetStyle(XSSFSheet sheet) {
        int yCount = sheet.getLastRowNum();
        for (int y = 1; y <= yCount; y++) {
            XSSFRow row = sheet.getRow(y);
            if (row != null) {
                int xCount = row.getLastCellNum();
                for (int x = 0; x < xCount; x++) {
                    XSSFCell cell = row.getCell(x);
                    if (cell != null) {
                        // 单元格注释
                        XSSFComment comment = cell.getCellComment();
                        if (comment != null) {
                            cell.removeCellComment();
                        }
                        // 清理格式
                        /*XSSFCellStyle style = cell.getCellStyle();
                        if (style != null) {
                            style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
                            cell.setCellStyle(style);
                        }*/
                    }
                }
            }
        }
    }
}
