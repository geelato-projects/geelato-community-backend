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
     * 设置表头通用单元格样式
     * <p>
     * 设置单元格的边框（上下左右）、方向（水平居中、垂直居中），并应用浅灰色背景。
     *
     * @param style 需要设置的单元格样式对象
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
     * 设置单元格样式为表格通用样式
     * <p>
     * 设置单元格的边框为上下左右四边，且边框样式为细线。同时设置单元格的文本对齐方式为水平居中和垂直居中。
     *
     * @param style 要设置的单元格样式对象
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
     * <p>
     * 根据提供的参数创建一个新的字体样式。
     *
     * @param workbook   Excel工作簿对象，用于创建字体
     * @param fontName   字体名称
     * @param fontHeight 字体高度
     * @return 返回创建好的字体样式对象
     */
    public static XSSFFont getCellFont(XSSFWorkbook workbook, String fontName, short fontHeight) {
        XSSFFont font = workbook.createFont();
        font.setFontHeightInPoints(fontHeight);
        font.setFontName(fontName);
        return font;
    }

    /**
     * 设置列宽，根据字符串长度
     * <p>
     * 根据提供的字符串长度和Excel工作表对象，计算并设置指定列的宽度。
     *
     * @param sheet Excel工作表对象
     * @param value 用于计算列宽的字符串
     * @param index 要设置宽度的列的索引
     */
    public static void setColumnWidth(XSSFSheet sheet, String value, int index) {
        int width = value.length() <= 3 ? 3 : (Math.min(value.length(), 72));
        // 根据文本长度估算列宽（这里是一个简单的估算，可能需要调整）
        int estimatedWidth = (int) (value.length() * 3.5 * 256); // 假设每个字符大约需要1.5个字符宽度的空间
        sheet.setColumnWidth(index, estimatedWidth); // 设置第一列的列宽
    }

    /**
     * 设置列宽，固定列宽
     * <p>
     * 根据给定的Excel工作表和列索引范围，设置指定列的宽度为固定值。
     *
     * @param sheet  Excel工作表对象
     * @param index  列起始索引
     * @param extent 列结束索引（不包括此索引）
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
     * 设置单元格备注
     * <p>
     * 为指定的单元格添加备注信息。
     *
     * @param sheet Excel工作表对象
     * @param cell  要添加备注的单元格对象
     * @param mark  备注信息内容
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
        Drawing<XSSFShape> drawing = sheet.createDrawingPatriarch();
        XSSFComment comment = (XSSFComment) drawing.createCellComment(anchor);
        comment.setString(new XSSFRichTextString(mark));
        cell.setCellComment(comment);
    }

    /**
     * 获取或创建Excel单元格
     * <p>
     * 如果指定行和索引位置的单元格不存在，则创建一个新的单元格。
     *
     * @param row   要获取或创建单元格的行对象
     * @param index 要获取或创建单元格的索引位置
     * @return 返回指定行和索引位置的单元格对象
     */
    public static XSSFCell getCell(XSSFRow row, int index) {
        XSSFCell cell = row.getCell(index);
        if (cell == null) {
            cell = row.createCell(index);
        }
        return cell;
    }

    /**
     * 设置单元格的样式和值
     * <p>
     * 在指定的行中，根据索引获取或创建单元格，并设置其样式和值。
     *
     * @param row       要设置单元格的行对象
     * @param index     单元格的索引位置
     * @param cellStyle 要设置的单元格样式
     * @param value     要设置的单元格值
     * @return 返回设置后的单元格对象
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, String value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value);
        return cell;
    }

    /**
     * 设置单元格的样式和值
     * <p>
     * 在指定的行和列索引处创建一个单元格，设置其样式和值为指定的布尔值。
     *
     * @param row       要设置单元格的行对象
     * @param index     要设置单元格的列索引
     * @param cellStyle 要应用的单元格样式
     * @param value     要设置的单元格值，类型为布尔值
     * @return 返回设置后的单元格对象
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, Boolean value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value);
        return cell;
    }

    /**
     * 设置单元格的样式和值
     * <p>
     * 在指定的行和列索引处创建一个单元格，并为其设置样式和值。
     *
     * @param row       行对象
     * @param index     列索引
     * @param cellStyle 单元格样式对象
     * @param value     要设置的单元格值
     * @return 返回设置好的单元格对象
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, Double value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value);
        return cell;
    }

    /**
     * 设置单元格的样式和值
     * <p>
     * 在指定的行和列索引位置创建或获取一个单元格，并为其设置样式和值。
     *
     * @param row       行对象，用于获取或创建单元格
     * @param index     列索引，指定单元格的位置
     * @param cellStyle 单元格样式对象，用于设置单元格的样式
     * @param value     单元格的值，如果为null，则单元格值也将被设置为null
     * @return 返回设置好的单元格对象
     */
    public static XSSFCell setCell(XSSFRow row, int index, XSSFCellStyle cellStyle, Integer value) {
        XSSFCell cell = ExcelXSSFUtils.getCell(row, index);
        cell.setCellStyle(cellStyle);
        cell.setCellValue(value == null ? null : String.valueOf(value));
        return cell;
    }
}
