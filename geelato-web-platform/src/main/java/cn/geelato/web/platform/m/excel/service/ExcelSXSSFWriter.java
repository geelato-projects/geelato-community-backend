package cn.geelato.web.platform.m.excel.service;

import cn.geelato.web.platform.m.excel.entity.BusinessData;
import org.apache.poi.ss.usermodel.ClientAnchor;
import org.apache.poi.ss.usermodel.Drawing;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFCell;
import org.apache.poi.xssf.streaming.SXSSFRow;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import cn.geelato.core.meta.model.field.ColumnMeta;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 * @description: SXSSF
 * @date 2024/1/4 15:44
 */
@Component
public class ExcelSXSSFWriter {

    /**
     * 往业务数据中写入校验批注
     *
     * @param sheet
     * @param style               填充颜色
     * @param businessDataMapList 业务数据
     */
    public void writeBusinessData(SXSSFSheet sheet, XSSFCellStyle style, List<Map<String, BusinessData>> businessDataMapList) {
        Map<String, BusinessData> mapSet = new HashMap<>();
        for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
            for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                BusinessData businessData = businessDataEntry.getValue();
                if (businessData != null && !businessData.isValidate()) {
                    String key = String.format("Y.%s;X.%s", businessData.getYIndex(), businessData.getXIndex());
                    BusinessData msgData = (mapSet.containsKey(key) && mapSet.get(key) != null) ? mapSet.get(key) : new BusinessData();
                    msgData.setYIndex(businessData.getYIndex());
                    msgData.setXIndex(businessData.getXIndex());
                    msgData.addAllErrorMsgs(businessData.getErrorMsg());
                    mapSet.put(key, msgData);
                }
            }
        }
        // 清理 批注
        cleanSheetStyle(sheet);
        // 实体数据
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            SXSSFRow row = sheet.getRow(i);
            if (row == null) {
                break;
            }
            for (Map.Entry<String, BusinessData> businessDataEntry : mapSet.entrySet()) {
                BusinessData businessData = businessDataEntry.getValue();
                if (businessData != null && businessData.getYIndex() == i) {
                    SXSSFCell cell = row.getCell(businessData.getXIndex());
                    if (cell == null) {
                        cell = row.createCell(businessData.getXIndex());
                    }
                    cell.setCellStyle(style);
                    ClientAnchor anchor = new XSSFClientAnchor();
                    anchor.setCol1(cell.getColumnIndex());
                    anchor.setRow1(cell.getRowIndex());
                    anchor.setCol2(cell.getColumnIndex() + 10);
                    anchor.setRow2(cell.getRowIndex() + 10);
                    Drawing drawing = sheet.createDrawingPatriarch();
                    XSSFComment comment = (XSSFComment) drawing.createCellComment(anchor);
                    comment.setString(new XSSFRichTextString(String.join("；\r\n", businessData.getErrorMsg())));
                    cell.setCellComment(comment);
                }
            }
        }

    }

    /**
     * 插入工作表，唯一性约束校验失败
     *
     * @param workbook
     * @param repeatedData
     */
    public void writeRepeatedData(SXSSFWorkbook workbook, Map<ColumnMeta, Map<Object, Long>> repeatedData) {
        if (repeatedData != null && repeatedData.size() > 0) {
            SXSSFSheet sheet = workbook.createSheet("唯一约束，导入数据重复值及数量"); // 创建新的工作表
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
     * 创建表格
     *
     * @param sheet
     * @param y
     * @param x
     * @param value
     */
    private void createRowCell(SXSSFSheet sheet, int y, int x, Object value) {
        SXSSFRow row = sheet.getRow(y);
        if (row == null) {
            row = sheet.createRow(y);
        }
        SXSSFCell cell = row.getCell(x);
        if (cell == null) {
            cell = row.createCell(x);
        }
        cell.setCellValue(String.valueOf(value));
    }

    /**
     * 清理 批注，
     *
     * @param sheet
     */
    private void cleanSheetStyle(SXSSFSheet sheet) {
        int yCount = sheet.getLastRowNum();
        for (int y = 1; y <= yCount; y++) {
            SXSSFRow row = sheet.getRow(y);
            if (row != null) {
                int xCount = row.getLastCellNum();
                for (int x = 0; x < xCount; x++) {
                    SXSSFCell cell = row.getCell(x);
                    if (cell != null) {
                        // 单元格注释
                        XSSFComment comment = (XSSFComment) cell.getCellComment();
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
