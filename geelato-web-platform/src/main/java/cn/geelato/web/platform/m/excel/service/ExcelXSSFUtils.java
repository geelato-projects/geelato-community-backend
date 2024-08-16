package cn.geelato.web.platform.m.excel.service;

import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

/**
 * @author diabl
 */
public class ExcelXSSFUtils {
    public static final String FONT_NAME_SONGTI = "宋体";
    public static final String FONT_NAME_FANGSONG = "仿宋";
    public static final String FONT_NAME_FANGSONG_GB2312 = "仿宋_GB2312";

    public static XSSFWorkbook copySheet(XSSFSheet sourceSheet) {
        XSSFWorkbook targetWorkbook = new XSSFWorkbook();
        XSSFSheet targetSheet = targetWorkbook.createSheet(sourceSheet.getSheetName());
        copySheet(sourceSheet, targetSheet);

        return targetWorkbook;
    }

    public static void copySheet(XSSFSheet sourceSheet, XSSFSheet targetSheet) {
        for (int i = 0; i < sourceSheet.getPhysicalNumberOfRows(); i++) {
            XSSFRow sourceRow = sourceSheet.getRow(i);
            XSSFRow targetRow = targetSheet.createRow(i);
            if (sourceRow != null) {
                copyRow(sourceRow, targetRow);
            }
        }
    }

    public static void copyRow(XSSFRow sourceRow, XSSFRow targetRow) {
        for (int i = 0; i < sourceRow.getPhysicalNumberOfCells(); i++) {
            XSSFCell sourceCell = sourceRow.getCell(i);
            XSSFCell targetCell = targetRow.createCell(i);
            if (sourceCell != null) {
                targetCell.copyCellFrom(sourceCell, new CellCopyPolicy());
            }
        }
    }

    public static void reserveSheet(XSSFWorkbook workbook, int reserveIndex) {
        workbook.setActiveSheet(reserveIndex);
        int maxSheets = workbook.getNumberOfSheets();
        for (int i = maxSheets - 1; i > reserveIndex; i--) {
            workbook.removeSheetAt(i);
        }
        for (int i = 0; i < reserveIndex; i++) {
            workbook.removeSheetAt(0);
        }
    }

    /**
     * 单元格样式，表头通用
     * 边框，上下左右
     * 方向，水平居中，垂直居中
     *
     * @param style
     */
    public static void setTableHeaderGeneralStyle(XSSFCellStyle style) {
        // 创建一个单元格样式，并设置背景色为浅灰色
        byte[] rgb = new byte[]{(byte) 242, (byte) 243, (byte) 245}; // RGB for #C0C0C0 242, 243, 245;
        XSSFColor myColor = new XSSFColor(rgb, null);
        style.setFillForegroundColor(myColor);
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        // 表格通用样式
        ExcelXSSFUtils.setTableGeneralStyle(style);
    }

    /**
     * 单元格样式，表格通用
     * 边框，上下左右
     * 方向，水平居中，垂直居中
     *
     * @param style
     */
    public static void setTableGeneralStyle(XSSFCellStyle style) {
        // 设置边框
        style.setBorderTop(BorderStyle.THIN); // 设置上边框
        style.setBorderBottom(BorderStyle.THIN); // 设置下边框
        style.setBorderLeft(BorderStyle.THIN); // 设置左边框
        style.setBorderRight(BorderStyle.THIN); // 设置右边框
        // 数值剧中
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
    }

    /**
     * 创建字体样式
     *
     * @param workbook
     * @param fontName
     * @param fontHeight
     * @return
     */
    public static XSSFFont getCellFont(XSSFWorkbook workbook, String fontName, short fontHeight) {
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints(fontHeight);
        font.setFontName(fontName);
        return font;
    }

    /**
     * 设置列宽，根据字符串长度
     *
     * @param sheet
     * @param value
     * @param index
     */
    public static void setColumnWidth(XSSFSheet sheet, String value, int index) {
        int width = value.length() <= 3 ? 3 : (value.length() >= 72 ? 72 : value.length());
        // 根据文本长度估算列宽（这里是一个简单的估算，可能需要调整）
        int estimatedWidth = (int) (value.length() * 3.5 * 256); // 假设每个字符大约需要1.5个字符宽度的空间
        sheet.setColumnWidth(index, estimatedWidth); // 设置第一列的列宽
    }

    /**
     * 设置列宽，固定列宽
     *
     * @param sheet
     * @param index
     * @param extent
     */
    public static void setColumnWidth(XSSFSheet sheet, int index, int extent) {
        int estimatedWidth = (int) (5 * 3.5 * 256); // 假设每个字符大约需要1.5个字符宽度的空间
        // 根据文本长度估算列宽（这里是一个简单的估算，可能需要调整）
        for (int i = index; i < extent; i++) {
            sheet.setColumnWidth(i, estimatedWidth); // 设置第一列的列宽
        }
    }

    public static void setColumnWidth(XSSFSheet sheet, int index, long width) {
        width = width <= 90 ? 90 : (width >= 2000 ? 2000 : width);
        // 根据文本长度估算列宽（这里是一个简单的估算，可能需要调整）
        // 假设每个字符大约需要1.5个字符宽度的空间 256
        int estimatedWidth = (int) Math.floor(width * 25.6);
        sheet.setColumnWidth(index, estimatedWidth);
    }

    /**
     * 设置单元格 备注
     *
     * @param sheet
     * @param cell
     * @param mark
     */
    public static void setCellComment(XSSFSheet sheet, XSSFCell cell, String mark) {
        if (Strings.isBlank(mark)) {
            return;
        }
        ClientAnchor anchor = new XSSFClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setRow1(cell.getRowIndex());
        anchor.setCol2(cell.getColumnIndex() + 5);
        anchor.setRow2(cell.getRowIndex() + 5);
        Drawing drawing = sheet.createDrawingPatriarch();
        XSSFComment comment = (XSSFComment) drawing.createCellComment(anchor);
        comment.setString(new XSSFRichTextString(mark));
        cell.setCellComment(comment);
    }

    /**
     * 获取或创建
     *
     * @param row
     * @param index
     * @return
     */
    public static XSSFCell getCell(XSSFRow row, int index) {
        XSSFCell cell = row.getCell(index);
        if (cell == null) {
            cell = row.createCell(index);
        }
        return cell;
    }

    /**
     * 设置单元格，样式，值
     *
     * @param row
     * @param index
     * @param cellStyle
     * @param value
     * @return
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, String value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value);
        return cell;
    }

    /**
     * 设置单元格，样式，值
     *
     * @param row
     * @param index
     * @param cellStyle
     * @param value
     * @return
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, Boolean value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value);
        return cell;
    }

    /**
     * 设置单元格，样式，值
     *
     * @param row
     * @param index
     * @param cellStyle
     * @param value
     * @return
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, Double value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value);
        return cell;
    }

    /**
     * 设置单元格，样式，值
     *
     * @param row
     * @param index
     * @param cellStyle
     * @param value
     * @return
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, Integer value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value == null ? null : String.valueOf(value));
        return cell;
    }
}
