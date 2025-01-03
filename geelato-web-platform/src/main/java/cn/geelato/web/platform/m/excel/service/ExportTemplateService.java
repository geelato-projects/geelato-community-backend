package cn.geelato.web.platform.m.excel.service;

import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.excel.entity.*;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.param.FileParam;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author diabl
 */
@Component
public class ExportTemplateService extends BaseService {
    public static final String[] EXPORT_META_HEADER = {"占位符", "变量", "列表变量", "常量值", "表达式", "值类型", "取值计算方式", "来源于列表", "合并单元格", "合并唯一约束", "图片", "图片宽度cm", "图片高度cm", "图片来源", "条形码编码", "输入值格式", "输出值格式", "备注"};
    public static final String[] IMPORT_META_TYPE_HEADER = {"列名", "类型", "格式", "多值分隔符", "多值场景", "清洗规则", "备注"};
    public static final String[] IMPORT_META_META_HEADER = {"表格", "字段名称", "取值计算方式", "常量取值", "变量取值", "表达式取值", "数据字典取值", "模型取值", "备注"};
    private static final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATEVARIETY);
    private static final String SAVE_TABLE_TYPE = AttachmentSourceEnum.PLATFORM_RESOURCES.getValue();
    @Lazy
    @Autowired
    private FileHandler fileHandler;

    public ApiResult generateFile(String id, String fileType) throws IOException {
        ApiResult result = new ApiResult();
        if (Strings.isBlank(id) || Strings.isBlank(fileType)) {
            return ApiResult.fail(ApiErrorMsg.PARAMETER_MISSING);
        }
        String[] fileTypes = fileType.split(",");
        ExportTemplate exportTemplate = this.getModel(ExportTemplate.class, id);
        Assert.notNull(exportTemplate, ApiErrorMsg.QUERY_FAIL);
        if (Arrays.asList(fileTypes).contains("template") && "import".equalsIgnoreCase(exportTemplate.getUseType()) && Strings.isNotBlank(exportTemplate.getBusinessTypeData())) {
            result = this.generateImportTemplate(exportTemplate);
        }
        if (Arrays.asList(fileTypes).contains("meta")) {
            if ("import".equalsIgnoreCase(exportTemplate.getUseType()) && Strings.isNotBlank(exportTemplate.getBusinessMetaData()) && Strings.isNotBlank(exportTemplate.getBusinessRuleData()) && Strings.isNotBlank(exportTemplate.getBusinessTypeData())) {
                result = this.generateImportMeta(exportTemplate);
            } else if ("export".equalsIgnoreCase(exportTemplate.getUseType()) && Strings.isNotBlank(exportTemplate.getBusinessMetaData())) {
                result = this.generateExportMeta(exportTemplate);
            }
        }

        return result;
    }

    private ApiResult generateImportTemplate(ExportTemplate meta) throws IOException {
        OutputStream outputStream = null;
        XSSFWorkbook workbook = null;
        FileInputStream fileInputStream = null;
        try {
            List<BusinessTypeData> businessTypeData = JSON.parseArray(meta.getBusinessTypeData(), BusinessTypeData.class);
            if (businessTypeData == null || businessTypeData.isEmpty()) {
                return ApiResult.fail("Excel模板字段定义不存在，无法生成文件！");
            }
            // 创建文件，
            String excelPath = getSavePath(meta, "import-template.xlsx", true);
            // 读取文件，
            workbook = new XSSFWorkbook();
            importTemplateSheet(workbook, "IMPORT TEMPLATE", businessTypeData);
            // 输出数据到文件
            outputStream = new BufferedOutputStream(new FileOutputStream(excelPath));
            workbook.write(outputStream);
            outputStream.flush();
            workbook.close();
            // 保存附件
            String excelFileName = String.format("%s：导入模板 %s.xlsx", meta.getTitle(), sdf.format(new Date()));
            Attachment attachment = saveAttach(meta, excelPath, excelFileName);
            // 转base64
            fileInputStream = new FileInputStream(excelPath);
            Map<String, Object> templateMap = fileToBase64(fileInputStream, attachment);
            // 模板数据处理，保留备份
            backupsAndUpdateExportTemplate(meta, JSON.toJSONString(templateMap), null);

            return ApiResult.success(attachment);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    private ApiResult generateImportMeta(ExportTemplate meta) throws IOException {
        OutputStream outputStream = null;
        XSSFWorkbook workbook = null;
        FileInputStream fileInputStream = null;
        try {
            List<BusinessTypeData> businessTypeData = JSON.parseArray(meta.getBusinessTypeData(), BusinessTypeData.class);
            if (businessTypeData == null || businessTypeData.isEmpty()) {
                return ApiResult.fail("Excel模板字段定义不存在，无法生成文件！");
            }
            List<BusinessTypeRuleData> businessTypeRuleData = JSON.parseArray(meta.getBusinessRuleData(), BusinessTypeRuleData.class);
            if (businessTypeRuleData == null || businessTypeRuleData.isEmpty()) {
                return ApiResult.fail("Excel模板数据处理规则不存在，无法生成文件！");
            }
            List<BusinessMeta> businessMetaData = JSON.parseArray(meta.getBusinessMetaData(), BusinessMeta.class);
            if (businessMetaData == null || businessMetaData.isEmpty()) {
                return ApiResult.fail("数据保存配置不存在，无法生成文件！");
            }
            // 创建文件，
            String excelPath = getSavePath(meta, "import-meta.xlsx", true);
            // 读取文件，
            workbook = new XSSFWorkbook();
            importMetaTypeSheet(workbook, "Excel模板字段定义", businessTypeData);
            importMetaRuleSheet(workbook, "Excel模板数据处理规则", businessTypeRuleData);
            importMetaMetaSheet(workbook, "数据保存配置", businessMetaData);
            // 输出数据到文件
            outputStream = new BufferedOutputStream(new FileOutputStream(excelPath));
            workbook.write(outputStream);
            outputStream.flush();
            workbook.close();
            // 保存附件
            String excelFileName = String.format("%s：元数据 %s.xlsx", meta.getTitle(), sdf.format(new Date()));
            Attachment attachment = saveAttach(meta, excelPath, excelFileName);
            // 转base64
            fileInputStream = new FileInputStream(excelPath);
            Map<String, Object> templateMap = fileToBase64(fileInputStream, attachment);
            // 模板数据处理，保留备份
            backupsAndUpdateExportTemplate(meta, null, JSON.toJSONString(templateMap));

            return ApiResult.success(attachment);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    private ApiResult generateExportMeta(ExportTemplate meta) throws IOException {
        OutputStream outputStream = null;
        XSSFWorkbook workbook = null;
        FileInputStream fileInputStream = null;
        try {
            List<PlaceholderMeta> placeholderMetas = JSON.parseArray(meta.getBusinessMetaData(), PlaceholderMeta.class);
            if (placeholderMetas == null || placeholderMetas.isEmpty()) {
                return ApiResult.fail("数据保存配置不存在，无法生成文件！");
            }
            // 创建文件，
            String excelPath = getSavePath(meta, "export-meta.xlsx", true);
            // 读取文件，
            workbook = new XSSFWorkbook();
            exportMetaSheet(workbook, "EXPORT META", placeholderMetas);
            // 输出数据到文件
            outputStream = new BufferedOutputStream(new FileOutputStream(excelPath));
            workbook.write(outputStream);
            outputStream.flush();
            workbook.close();
            // 保存附件
            String excelFileName = String.format("%s：元数据 %s.xlsx", meta.getTitle(), sdf.format(new Date()));
            Attachment attachment = saveAttach(meta, excelPath, excelFileName);
            // 转base64
            fileInputStream = new FileInputStream(excelPath);
            Map<String, Object> templateMap = fileToBase64(fileInputStream, attachment);
            // 模板数据处理，保留备份
            backupsAndUpdateExportTemplate(meta, null, JSON.toJSONString(templateMap));

            return ApiResult.success(attachment);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (outputStream != null) {
                outputStream.close();
            }
            if (workbook != null) {
                workbook.close();
            }
        }
    }

    /**
     * 往文档中写入模板信息
     * <p>
     * 在给定的Excel工作簿中创建一个新的工作表，并根据提供的业务类型数据列表，向该工作表中写入表头信息。
     *
     * @param workbook  Excel工作簿对象
     * @param sheetName 新建工作表的名称
     * @param metas     业务类型数据列表，每个元素包含业务类型名称、类型、格式和备注等信息
     */
    private void importTemplateSheet(XSSFWorkbook workbook, String sheetName, List<BusinessTypeData> metas) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        // 创建字体样式
        XSSFCellStyle headerCellStyle = getHeaderCellStyle(workbook);
        // 写入信息，
        XSSFRow row = sheet.createRow(0);
        for (int i = 0; i < metas.size(); i++) {
            BusinessTypeData data = metas.get(i);
            XSSFCell cell = row.createCell(i);
            cell.setCellStyle(headerCellStyle);
            if (Strings.isNotBlank(data.getName())) {
                cell.setCellValue(data.getName());
                // 调整列宽
                ExcelXSSFUtils.setColumnWidth(sheet, data.getName(), i);
            }
            // 备注
            String mark = String.format("类型：%s；\r\n格式：%s；\r\n说明：%s；", data.getType(), data.getFormat(), data.getRemark());
            ExcelXSSFUtils.setCellComment(sheet, cell, mark);
        }
        //  冻结第一行，列不冻结
        sheet.createFreezePane(0, 1);
    }

    /**
     * 往文档中写入数据类型信息，用于导入功能
     * <p>
     * 在给定的Excel工作簿中创建一个新的工作表，并根据提供的业务类型数据列表，向该工作表中写入数据类型信息。
     *
     * @param workbook  Excel工作簿对象
     * @param sheetName 新建工作表的名称
     * @param metas     业务类型数据列表，每个元素包含业务类型名称、类型、格式、备注等信息
     */
    private void importMetaTypeSheet(XSSFWorkbook workbook, String sheetName, List<BusinessTypeData> metas) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        // 写入表头
        XSSFRow row = sheet.createRow(0);
        XSSFCellStyle headerCellStyle = getHeaderCellStyle(workbook);
        for (int i = 0; i < IMPORT_META_TYPE_HEADER.length; i++) {
            XSSFCell cell = row.createCell(i);
            cell.setCellStyle(headerCellStyle);
            cell.setCellValue(IMPORT_META_TYPE_HEADER[i]);
        }
        // 写入数据
        XSSFCellStyle cellStyle = getCellStyle(workbook);
        for (int i = 0; i < metas.size(); i++) {
            XSSFRow dRow = sheet.createRow(i + 1);
            ExcelXSSFUtils.setCell(dRow, 0, cellStyle, metas.get(i).getName());
            ExcelXSSFUtils.setCell(dRow, 1, cellStyle, metas.get(i).getType());
            ExcelXSSFUtils.setCell(dRow, 2, cellStyle, metas.get(i).getFormat());
            ExcelXSSFUtils.setCell(dRow, 3, cellStyle, "");
            ExcelXSSFUtils.setCell(dRow, 4, cellStyle, "");
            ExcelXSSFUtils.setCell(dRow, 5, cellStyle, "");
            ExcelXSSFUtils.setCell(dRow, 6, cellStyle, metas.get(i).getRemark());
        }
        // 设置列宽
        ExcelXSSFUtils.setColumnWidth(sheet, 0, IMPORT_META_TYPE_HEADER.length);
    }

    /**
     * 往文档中写入信息，导入功能，清洗规则
     * <p>
     * 在Excel工作簿中创建一个新的工作表，并向其中写入清洗规则的表头和数据。
     *
     * @param workbook  Excel工作簿对象
     * @param sheetName 新建工作表的名称
     * @param metas     清洗规则数据列表，包含处理列名、清洗规则类型、规则、目标、是否保留原值和次序等信息
     */
    private void importMetaRuleSheet(XSSFWorkbook workbook, String sheetName, List<BusinessTypeRuleData> metas) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        // 写入表头
        CellRangeAddress mergedRegion1 = new CellRangeAddress(0, 1, 0, 0);
        sheet.addMergedRegion(mergedRegion1);
        CellRangeAddress mergedRegion2 = new CellRangeAddress(0, 0, 1, 5);
        sheet.addMergedRegion(mergedRegion2);
        CellRangeAddress mergedRegion3 = new CellRangeAddress(0, 1, 6, 6);
        sheet.addMergedRegion(mergedRegion3);
        XSSFCellStyle headerCellStyle = getHeaderCellStyle(workbook);
        XSSFRow row1 = sheet.createRow(0);
        ExcelXSSFUtils.setCell(row1, 0, headerCellStyle, "处理列名");
        ExcelXSSFUtils.setCell(row1, 1, headerCellStyle, "清洗规则");
        ExcelXSSFUtils.setCell(row1, 6, headerCellStyle, "说明");
        XSSFRow row2 = sheet.createRow(1);
        ExcelXSSFUtils.setCell(row2, 1, headerCellStyle, "类型");
        ExcelXSSFUtils.setCell(row2, 2, headerCellStyle, "规则");
        ExcelXSSFUtils.setCell(row2, 3, headerCellStyle, "目标");
        ExcelXSSFUtils.setCell(row2, 4, headerCellStyle, "保留原值");
        ExcelXSSFUtils.setCell(row2, 5, headerCellStyle, "次序");
        // 写入数据
        XSSFCellStyle cellStyle = getCellStyle(workbook);
        for (int i = 0; i < metas.size(); i++) {
            XSSFRow dRow = sheet.createRow(i + 2);
            ExcelXSSFUtils.setCell(dRow, 0, cellStyle, metas.get(i).getColumnName());
            ExcelXSSFUtils.setCell(dRow, 1, cellStyle, metas.get(i).getType());
            ExcelXSSFUtils.setCell(dRow, 2, cellStyle, metas.get(i).getRule());
            ExcelXSSFUtils.setCell(dRow, 3, cellStyle, metas.get(i).getGoal());
            ExcelXSSFUtils.setCell(dRow, 4, cellStyle, metas.get(i).isRetain());
            ExcelXSSFUtils.setCell(dRow, 5, cellStyle, metas.get(i).getOrder());
            ExcelXSSFUtils.setCell(dRow, 6, cellStyle, metas.get(i).getRemark());
        }
        // 设置列宽
        ExcelXSSFUtils.setColumnWidth(sheet, 0, 7);
    }

    /**
     * 往文档中写入元数据，用于导入功能
     * <p>
     * 在给定的Excel工作簿中创建一个新的工作表，并根据提供的业务元数据列表，向该工作表中写入元数据信息。
     *
     * @param workbook  Excel工作簿对象
     * @param sheetName 新建工作表的名称
     * @param metas     业务元数据列表，每个元素包含表名、列名、评估规则、常量值、变量值、表达式、字典编码、主键值和备注等信息
     */
    private void importMetaMetaSheet(XSSFWorkbook workbook, String sheetName, List<BusinessMeta> metas) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        // 写入表头
        XSSFRow row = sheet.createRow(0);
        XSSFCellStyle headerCellStyle = getHeaderCellStyle(workbook);
        for (int i = 0; i < IMPORT_META_META_HEADER.length; i++) {
            XSSFCell cell = row.createCell(i);
            cell.setCellStyle(headerCellStyle);
            cell.setCellValue(IMPORT_META_META_HEADER[i]);
        }
        // 写入数据
        XSSFCellStyle cellStyle = getCellStyle(workbook);
        for (int i = 0; i < metas.size(); i++) {
            XSSFRow dRow = sheet.createRow(i + 1);
            ExcelXSSFUtils.setCell(dRow, 0, cellStyle, metas.get(i).getTableName());
            ExcelXSSFUtils.setCell(dRow, 1, cellStyle, metas.get(i).getColumnName());
            ExcelXSSFUtils.setCell(dRow, 2, cellStyle, metas.get(i).getEvaluation());
            ExcelXSSFUtils.setCell(dRow, 3, cellStyle, metas.get(i).getConstValue());
            ExcelXSSFUtils.setCell(dRow, 4, cellStyle, metas.get(i).getVariableValue());
            ExcelXSSFUtils.setCell(dRow, 5, cellStyle, metas.get(i).getExpression());
            ExcelXSSFUtils.setCell(dRow, 6, cellStyle, metas.get(i).getDictCode());
            ExcelXSSFUtils.setCell(dRow, 7, cellStyle, metas.get(i).getPrimaryValue());
            ExcelXSSFUtils.setCell(dRow, 8, cellStyle, metas.get(i).getRemark());
        }
        // 设置列宽
        ExcelXSSFUtils.setColumnWidth(sheet, 0, IMPORT_META_META_HEADER.length);
    }

    /**
     * 往文档中写入信息，导出功能，元数据
     * <p>
     * 在给定的Excel工作簿中创建一个新的工作表，并根据提供的占位符元数据列表，向该工作表中写入元数据信息。
     *
     * @param workbook  Excel工作簿对象
     * @param sheetName 新建工作表的名称
     * @param metas     占位符元数据列表，每个元素包含占位符名称、变量名、列表变量名、常量值、表达式、值类型、值计算模式、是否列表、是否合并、是否唯一、是否图片、图片宽度、图片高度、图片来源、条形码编码、导入格式、导出格式和描述等信息
     */
    private void exportMetaSheet(XSSFWorkbook workbook, String sheetName, List<PlaceholderMeta> metas) {
        XSSFSheet sheet = workbook.createSheet(sheetName);
        // 写入表头
        XSSFRow row = sheet.createRow(0);
        XSSFCellStyle headerCellStyle = getHeaderCellStyle(workbook);
        for (int i = 0; i < EXPORT_META_HEADER.length; i++) {
            XSSFCell cell = row.createCell(i);
            cell.setCellStyle(headerCellStyle);
            cell.setCellValue(EXPORT_META_HEADER[i]);
        }
        // 写入数据
        XSSFCellStyle cellStyle = getCellStyle(workbook);
        for (int i = 0; i < metas.size(); i++) {
            XSSFRow dRow = sheet.createRow(i + 1);
            ExcelXSSFUtils.setCell(dRow, 0, cellStyle, metas.get(i).getPlaceholder());
            ExcelXSSFUtils.setCell(dRow, 1, cellStyle, metas.get(i).getVar());
            ExcelXSSFUtils.setCell(dRow, 2, cellStyle, metas.get(i).getListVar());
            ExcelXSSFUtils.setCell(dRow, 3, cellStyle, metas.get(i).getConstValue());
            ExcelXSSFUtils.setCell(dRow, 4, cellStyle, metas.get(i).getExpression());
            ExcelXSSFUtils.setCell(dRow, 5, cellStyle, metas.get(i).getValueType());
            ExcelXSSFUtils.setCell(dRow, 6, cellStyle, metas.get(i).getValueComputeMode());
            ExcelXSSFUtils.setCell(dRow, 7, cellStyle, metas.get(i).isIsList());
            ExcelXSSFUtils.setCell(dRow, 8, cellStyle, metas.get(i).isIsMerge());
            ExcelXSSFUtils.setCell(dRow, 9, cellStyle, metas.get(i).isIsUnique());
            ExcelXSSFUtils.setCell(dRow, 10, cellStyle, metas.get(i).isIsImage());
            ExcelXSSFUtils.setCell(dRow, 11, cellStyle, metas.get(i).getImageWidth());
            ExcelXSSFUtils.setCell(dRow, 12, cellStyle, metas.get(i).getImageHeight());
            ExcelXSSFUtils.setCell(dRow, 13, cellStyle, metas.get(i).getImageSource());
            ExcelXSSFUtils.setCell(dRow, 14, cellStyle, metas.get(i).getBarcodeCode());
            ExcelXSSFUtils.setCell(dRow, 15, cellStyle, metas.get(i).getFormatImport());
            ExcelXSSFUtils.setCell(dRow, 16, cellStyle, metas.get(i).getFormatExport());
            ExcelXSSFUtils.setCell(dRow, 17, cellStyle, metas.get(i).getDescription());
        }
        // 设置列宽
        ExcelXSSFUtils.setColumnWidth(sheet, 0, EXPORT_META_HEADER.length);
    }

    /**
     * 获取文件保存路径
     * <p>
     * 根据提供的导出模板元数据、文件名和是否重命名标志，获取文件的保存路径。
     * 路径格式为：upload/存放表/租户编码/应用Id
     *
     * @param meta     导出模板元数据对象
     * @param fileName 文件名
     * @param isRename 是否重命名文件
     * @return 返回文件的保存路径
     */
    private String getSavePath(ExportTemplate meta, String fileName, boolean isRename) {
        return UploadService.getSavePath(UploadService.ROOT_DIRECTORY, SAVE_TABLE_TYPE, meta.getTenantCode(), meta.getAppId(), fileName, isRename);
    }

    /**
     * 保存文件至附件表中
     * <p>
     * 将Excel文件保存到附件表中，并返回保存后的资源对象。
     *
     * @param meta      导出模板元数据对象，包含应用ID等信息
     * @param excelPath Excel文件的路径
     * @param fileName  文件名
     * @return 返回保存后的资源对象
     * @throws IOException 如果在文件读写或保存过程中出现I/O异常，将抛出该异常
     */
    public Attachment saveAttach(ExportTemplate meta, String excelPath, String fileName) throws IOException {
        File excelFile = new File(excelPath);
        FileParam fileParam = new FileParam(SAVE_TABLE_TYPE, "fileTemplate", meta.getAppId(), meta.getTenantCode());
        return fileHandler.save(excelFile, fileName, excelPath, fileParam);
    }

    /**
     * 将文件转为base64格式
     * <p>
     * 将提供的文件输入流转换为Base64编码的字符串，并将相关信息存储在一个Map中返回。
     *
     * @param fileInputStream 文件输入流，包含要转换的文件数据
     * @param attachment      资源对象，包含文件的类型、大小、名称等信息
     * @return 返回包含文件信息的Map，包括文件的Base64编码内容、类型、大小和名称
     * @throws IOException 如果在文件读取过程中发生I/O异常，将抛出该异常
     */
    private Map<String, Object> fileToBase64(FileInputStream fileInputStream, Attachment attachment) throws IOException {
        Map<String, Object> templateMap = new HashMap<>();
        byte[] excelBytes = fileInputStream.readAllBytes();
        String base64Content = Base64.getEncoder().encodeToString(excelBytes);
        templateMap.put("type", attachment.getType());
        templateMap.put("size", attachment.getSize());
        templateMap.put("name", attachment.getName());
        templateMap.put("base64", base64Content);
        return templateMap;
    }

    /**
     * 备份原数据并更新原数据
     * <p>
     * 在更新导出模板数据之前，先备份原始数据。
     *
     * @param meta         导出模板元数据对象
     * @param template     模板内容
     * @param templateRule 模板规则
     * @throws InvocationTargetException 如果调用方法时发生异常
     * @throws IllegalAccessException    如果访问非法时发生异常
     */
    private void backupsAndUpdateExportTemplate(ExportTemplate meta, String template, String templateRule) throws InvocationTargetException, IllegalAccessException {
        if (template == null && templateRule == null) {
            return;
        }
        // 新建
        ExportTemplate newExTe = new ExportTemplate();
        BeanUtils.copyProperties(newExTe, meta);
        newExTe.setId(null);
        newExTe.setDeleteAt(new Date());
        newExTe.setDelStatus(DeleteStatusEnum.IS.getCode());
        newExTe.setEnableStatus(EnableStatusEnum.DISABLED.getCode());
        dao.save(newExTe);
        // 更新
        if (template != null) {
            meta.setTemplate(template);
        }
        if (templateRule != null) {
            meta.setTemplateRule(templateRule);
        }
        this.updateModel(meta);
    }

    /**
     * 获取表头单元格样式
     * <p>
     * 设置表头单元格的字体为仿宋、12号、加粗，背景色为浅灰色，边框为上下左右均有，文本方向为水平居中、垂直居中。
     *
     * @param workbook Excel工作簿对象
     * @return 返回设置好的单元格样式对象
     */
    private XSSFCellStyle getHeaderCellStyle(XSSFWorkbook workbook) {
        // 创建单元格样式，并将字体样式应用到单元格样式中
        XSSFCellStyle style = workbook.createCellStyle();
        // 创建字体样式
        XSSFFont font = ExcelXSSFUtils.getCellFont(workbook, ExcelXSSFUtils.FONT_NAME_SONGTI, (short) 12);
        // 设置字体加粗
        font.setBold(true);
        style.setFont(font);
        // 其他样式
        ExcelXSSFUtils.setTableHeaderGeneralStyle(style);

        return style;
    }

    /**
     * 获取单元格样式
     * <p>
     * 设置单元格的字体为仿宋、11号，边框为上下左右，文本方向为水平居中、垂直居中。
     *
     * @param workbook Excel工作簿对象
     * @return 返回设置好的单元格样式对象
     */
    private XSSFCellStyle getCellStyle(XSSFWorkbook workbook) {
        // 创建单元格样式，并将字体样式应用到单元格样式中
        XSSFCellStyle style = workbook.createCellStyle();
        // 创建字体样式
        XSSFFont font = ExcelXSSFUtils.getCellFont(workbook, ExcelXSSFUtils.FONT_NAME_SONGTI, (short) 11);
        style.setFont(font);
        // 其他样式
        ExcelXSSFUtils.setTableGeneralStyle(style);

        return style;
    }


}
