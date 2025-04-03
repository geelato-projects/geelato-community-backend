package cn.geelato.web.platform.m.excel.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.MediaTypes;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.common.Base64Helper;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.base.entity.SysConfig;
import cn.geelato.web.platform.m.base.service.SysConfigService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.excel.entity.ExportColumn;
import cn.geelato.web.platform.m.excel.entity.ExportTemplate;
import cn.geelato.web.platform.m.excel.entity.PlaceholderMeta;
import cn.geelato.web.platform.m.excel.entity.WordWaterMarkMeta;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.enums.FileGenreEnum;
import cn.geelato.web.platform.m.file.param.FileParam;
import cn.geelato.web.platform.m.file.utils.FileParamUtils;
import cn.geelato.web.platform.m.zxing.entity.Barcode;
import cn.geelato.web.platform.m.zxing.service.BarcodeService;
import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.poifs.crypt.HashAlgorithm;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * @author diabl
 */
@Component
@Slf4j
public class ExportExcelService {
    private static final Pattern pattern = Pattern.compile("^[a-zA-Z0-9_\\-]+\\.[a-zA-Z0-9]{1,5}$");
    private static final String SAVE_TABLE_TYPE = AttachmentSourceEnum.ATTACH.getValue();
    private final SimpleDateFormat sdf = new SimpleDateFormat(DateUtils.DATEVARIETY);
    @Autowired
    private ExportTemplateService exportTemplateService;
    @Autowired
    private ExcelWriter excelWriter;
    @Autowired
    private ExcelXSSFWriter excelXSSFWriter;
    @Autowired
    private WordXWPFWriter wordXWPFWriter;
    @Autowired
    private SysConfigService sysConfigService;
    @Autowired
    private FileHandler fileHandler;
    @Autowired
    private BarcodeService barcodeService;

    /**
     * 导出文件
     * <p>
     * 根据提供的模板ID、文件名、数据集合、对象数据、水印文本、水印样式和是否只读等参数，导出文件。
     *
     * @param templateId   导出模板ID
     * @param fileName     导出文件名称
     * @param valueMapList 数据集合，包含多组值映射，每组值映射是一个Map
     * @param valueMap     对象数据，包含要写入文件的数据
     * @param markText     水印文本，用于在文件中添加水印
     * @param markKey      配置的水印样式，用于定义水印的样式
     * @param readonly     是否只读，指定导出的文件是否应为只读
     * @return 返回导出文件的ApiResult对象，包含文件信息或错误信息
     */
    public ApiResult<?> exportWps(String templateId, String index, String fileName, List<Map> valueMapList, Map valueMap, String markText, String markKey, boolean readonly) {
        try {
            // 水印
            WordWaterMarkMeta markMeta = setWaterMark(markText, markKey);
            // 模型
            ExportTemplate exportTemplate = exportTemplateService.getModel(ExportTemplate.class, templateId);
            Assert.notNull(exportTemplate, "导出模板不存在");
            // 模板
            Base64Helper templateAttach = getTemplate(exportTemplate.indexTemplateDefault(index));
            Assert.notNull(templateAttach, "导出模板文件不存在");
            // 模板源数据
            Map<String, PlaceholderMeta> metaMap = null;
            Base64Helper templateRuleAttach = getTemplate(exportTemplate.getTemplateRule());
            if (templateRuleAttach != null) {
                // 读取，模板源数据
                metaMap = getPlaceholderMeta(templateRuleAttach.getFile());
            } else if (Strings.isNotBlank(exportTemplate.getBusinessMetaData())) {
                metaMap = getPlaceholderMeta(exportTemplate.getBusinessMetaData());
            }
            if (metaMap == null || metaMap.isEmpty()) {
                throw new RuntimeException("导出模板源数据不存在！");
            }
            // 条形码信息设置
            barcodeFormat(metaMap);
            // 实体文件名称
            String templateExt = templateAttach.getName().substring(templateAttach.getName().lastIndexOf("."));
            String templateName = templateAttach.getName().substring(0, templateAttach.getName().lastIndexOf("."));
            if (Strings.isNotBlank(fileName)) {
                if (pattern.matcher(fileName).matches()) {
                    fileName = fileName.substring(0, fileName.lastIndexOf("."));
                }
                fileName = fileName + templateExt;
            } else {
                fileName = String.format("%s_%s%s", templateName, sdf.format(new Date()), templateExt);
            }
            // 实体文件 upload/存放表/租户编码/应用Id
            String directory = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, SAVE_TABLE_TYPE, exportTemplate.getTenantCode(), exportTemplate.getAppId(), fileName, true);
            File exportFile = new File(directory);
            // 生成实体文件
            generateEntityFile(templateAttach.getFile(), exportFile, metaMap, valueMapList, valueMap, markMeta, readonly);
            if (readonly) {
                exportFile.setReadOnly();
            }
            // 保存文件信息
            FileParam fileParam = FileParamUtils.byLocal(SAVE_TABLE_TYPE, FileGenreEnum.exportFile.name(), exportTemplate.getAppId(), exportTemplate.getTenantCode());
            Attachment attachment = fileHandler.save(exportFile, fileName, directory, fileParam);
            return ApiResult.success(attachment);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }


    /**
     * 根据列元数据导出Excel文件
     * <p>
     * 根据提供的列元数据、占位符元数据、值映射列表、单个值映射、水印文本、水印关键字和是否只读标志，导出Excel文件。
     *
     * @param appId            应用ID
     * @param fileName         文件名（可选）
     * @param valueMapList     值映射列表，包含多组数据
     * @param valueMap         单个值映射，包含数据
     * @param exportColumns    导出列信息列表
     * @param placeholderMetas 占位符元数据列表
     * @param markText         水印文本（可选）
     * @param markKey          水印关键字（可选）
     * @param readonly         是否只读
     * @return ApiResult对象，包含操作结果和返回数据
     */
    public ApiResult<?> exportExcelByColumnMeta(String appId, String fileName, List<Map> valueMapList, Map valueMap, List<ExportColumn> exportColumns, List<PlaceholderMeta> placeholderMetas, String markText, String markKey, boolean readonly) {
        try {
            String tenantCode = SessionCtx.getCurrentTenantCode();
            // 水印
            WordWaterMarkMeta markMeta = setWaterMark(markText, markKey);
            // 实体文件名称
            String templateExt = ".xlsx";
            if (Strings.isNotBlank(fileName)) {
                if (pattern.matcher(fileName).matches()) {
                    fileName = fileName.substring(0, fileName.lastIndexOf("."));
                }
            } else {
                fileName = "exportExcelByColumnMeta";
            }
            // 模板源数据
            Map<String, PlaceholderMeta> metaMap = getPlaceholderMeta(placeholderMetas);
            // 条形码信息设置
            barcodeFormat(metaMap);
            // 生成导出模板
            String templateName = String.format("%s_%s%s", fileName, "导出模板", templateExt);
            Base64Helper templateAttach = getTemplate(tenantCode, appId, templateName, exportColumns);
            Assert.notNull(templateAttach, "导出模板创建失败！");
            // 实体文件 upload/存放表/租户编码/应用Id
            String exportFileName = String.format("%s_%s%s", fileName, sdf.format(new Date()), templateExt);
            String directory = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, SAVE_TABLE_TYPE, tenantCode, appId, exportFileName, true);
            File exportFile = new File(directory);
            // 生成实体文件
            generateEntityFile(templateAttach.getFile(), exportFile, metaMap, valueMapList, valueMap, markMeta, readonly);
            if (readonly) {
                exportFile.setReadOnly();
            }
            // 保存文件信息
            FileParam fileParam = FileParamUtils.byLocal(SAVE_TABLE_TYPE, FileGenreEnum.exportFile.name(), appId, tenantCode);
            Attachment attachment = fileHandler.save(exportFile, exportFileName, directory, fileParam);
            return ApiResult.success(attachment);
        } catch (Exception e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 根据占位符元数据格式化条形码信息
     *
     * @param metaMap 占位符元数据映射，键为占位符标识，值为占位符元数据
     * @throws RuntimeException 如果条形码配置错误，将抛出运行时异常
     */
    public void barcodeFormat(Map<String, PlaceholderMeta> metaMap) {
        for (PlaceholderMeta meta : metaMap.values()) {
            meta.setBarcode(null);
            if (meta.isImageSourceBarcode() && Strings.isNotEmpty(meta.getBarcodeCode())) {
                ApiResult<Barcode> barcodeResult = barcodeService.getBarcodeByCode(meta.getBarcodeCode());
                if (barcodeResult.isSuccess() && barcodeResult.getData() != null) {
                    meta.setBarcode(barcodeResult.getData());
                } else {
                    throw new RuntimeException(String.format("条形码配置错误（%s）: %s", meta.getBarcodeCode(), barcodeResult.getMsg()));
                }
            }
        }
    }

    /**
     * 水印处理
     * <p>
     * 根据提供的标记文本或标记键，设置水印元数据。
     *
     * @param markText 水印文本，如果提供了有效的标记文本，则使用该文本设置水印
     * @param markKey  水印配置的标记键，如果提供了有效的标记键，则从系统配置中查询水印配置并设置水印
     * @return 返回设置好的水印元数据对象
     */
    private WordWaterMarkMeta setWaterMark(String markText, String markKey) {
        WordWaterMarkMeta meta = null;
        if (Strings.isNotBlank(markKey)) {
            Map<String, Object> params = new HashMap<>();
            params.put("configKey", markKey);
            List<SysConfig> list = sysConfigService.queryModel(SysConfig.class, params);
            if (list != null && !list.isEmpty() && list.get(0) != null) {
                SysConfig config = list.get(0);
                try {
                    if (config.isEncrypted()) {
                        SysConfigService.decrypt(config);
                    }
                    meta = JSON.parseObject(config.getConfigValue(), WordWaterMarkMeta.class);
                    Assert.notNull(meta, "水印功能，系统配置值解析为空。");
                    meta.setDefaultText(markText);
                } catch (Exception e) {
                    throw new RuntimeException("水印功能，系统配置值解析失败");
                }
            } else {
                throw new RuntimeException("水印功能，配置值查询失败");
            }
        } else if (Strings.isNotBlank(markText)) {
            meta = WordWaterMarkMeta.defaultWaterMarkMeta();
            meta.setDefaultText(markText);
        }

        return meta;
    }

    /**
     * 从Excel文件中获取占位符元数据
     * <p>
     * 从给定的Excel文件中读取占位符元数据，并将其存储在Map中返回。
     *
     * @param file 要读取的Excel文件
     * @return 包含占位符元数据的Map，其中键为占位符名称，值为对应的PlaceholderMeta对象
     * @throws IOException 如果在读取文件或关闭资源时发生I/O错误
     */
    private Map<String, PlaceholderMeta> getPlaceholderMeta(File file) throws IOException {
        Map<String, PlaceholderMeta> metaMap;
        Workbook workbook = null;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        try {
            // excel文件类型
            String contentType = Files.probeContentType(file.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(0);
                metaMap = excelWriter.readPlaceholderMeta(sheet);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(0);
                metaMap = excelXSSFWriter.readPlaceholderMeta(sheet);
                workbook.close();
                workbook = null;
            } else {
                throw new RuntimeException("暂不支持导出该格式文件！");
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
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

        return metaMap;
    }

    /**
     * 模板数据解析
     * <p>
     * 将JSON格式的字符串解析为占位符元数据映射。
     *
     * @param jsonText JSON格式的字符串，包含占位符元数据
     * @return 返回包含占位符元数据的映射，键为占位符名称，值为对应的PlaceholderMeta对象；如果解析失败，则返回null
     */
    private Map<String, PlaceholderMeta> getPlaceholderMeta(String jsonText) {
        Map<String, PlaceholderMeta> metaMap;
        try {
            List<PlaceholderMeta> metas = JSON.parseArray(jsonText, PlaceholderMeta.class);
            metaMap = getPlaceholderMeta(metas);
        } catch (Exception e) {
            metaMap = null;
        }

        return metaMap;
    }

    /**
     * 从占位符元数据列表中获取占位符元数据映射
     * <p>
     * 将传入的占位符元数据列表转换为以占位符名称为键，占位符元数据为值的映射。
     *
     * @param metas 占位符元数据列表
     * @return 包含占位符名称和对应占位符元数据的映射
     */
    private Map<String, PlaceholderMeta> getPlaceholderMeta(List<PlaceholderMeta> metas) {
        Map<String, PlaceholderMeta> metaMap = new HashMap<>();
        if (metas != null && !metas.isEmpty()) {
            for (PlaceholderMeta meta : metas) {
                if (excelXSSFWriter.validatePlaceholderMeta(meta)) {
                    metaMap.put(meta.getPlaceholder(), meta);
                }
            }
        }

        return metaMap;
    }

    /**
     * 生成实体文件
     * <p>
     * 根据提供的模板文件、占位符元数据、数据集合、单个数据、水印元数据和是否只读标志，生成实体文件。
     *
     * @param templateFile 模板文件，包含要导出的内容模板
     * @param exportFile   实体文件（路径），用于保存生成的文件
     * @param metaMap      占位符元数据，包含占位符和对应的信息
     * @param valueMapList 数据集合，包含多组数据
     * @param valueMap     单个数据，包含要写入文件的数据
     * @param markMeta     水印元数据，包含水印的样式和信息
     * @param readonly     是否只读，指定生成的文件是否应为只读
     * @throws IOException 如果在文件读写过程中发生I/O异常，将抛出该异常
     */
    private void generateEntityFile(File templateFile, File exportFile, Map<String, PlaceholderMeta> metaMap, List<Map> valueMapList, Map valueMap, WordWaterMarkMeta markMeta, Boolean readonly) throws IOException {
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        OutputStream outputStream = null;
        Workbook workbook = null;
        // 只读加密密码
        String password = SessionCtx.getCurrentTenantCode();
        try {
            // excel文件类型
            String contentType = Files.probeContentType(templateFile.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(templateFile);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                // 替换占位符
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(0);
                excelWriter.writeSheet(sheet, metaMap, valueMapList, valueMap);
                sheet.setForceFormulaRecalculation(true);
                // 水印
                // 只读
                if (readonly) {
                    sheet.protectSheet(password);
                    ((HSSFWorkbook) workbook).writeProtectWorkbook(password, password);
                }
                // 写入文件
                outputStream = new FileOutputStream(exportFile);
                workbook.write(outputStream);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                // 替换占位符
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(0);
                excelXSSFWriter.writeSheet(sheet, metaMap, valueMapList, valueMap);
                sheet.setForceFormulaRecalculation(true);
                // 水印
                // 只读
                if (readonly) {
                    sheet.protectSheet(password);
                    ((XSSFWorkbook) workbook).lockStructure();
                    ((XSSFWorkbook) workbook).lockRevision();
                    ((XSSFWorkbook) workbook).lockWindows();
                }
                // 写入文件
                outputStream = new FileOutputStream(exportFile);
                workbook.write(outputStream);
                outputStream.flush();
                workbook.close();
                workbook = null;
            } else if (MediaTypes.APPLICATION_WORD_DOC.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                HWPFDocument document = new HWPFDocument(fileSystem);
                // 替换占位符
                // 写入文件
                outputStream = new FileOutputStream(exportFile);
                document.write(outputStream);
                outputStream.flush();
                document.close();
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_WORD_DOCX.equals(contentType)) {
                XWPFDocument document = new XWPFDocument(bufferedInputStream);
                document.getParagraphs();
                // 替换占位符
                wordXWPFWriter.writeDocument(document, metaMap, valueMapList, valueMap);
                // 写入水印
                DocxWaterMarkUtils.setXWPFDocumentWaterMark(document, markMeta);
                // 只读
                if (readonly) {
                    document.enforceReadonlyProtection(password, HashAlgorithm.sha1);
                }
                // 写入文件
                outputStream = new FileOutputStream(exportFile);
                document.write(outputStream);
                outputStream.flush();
                document.close();
            } else {
                throw new RuntimeException("暂不支持导出该格式文件！");
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
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
    }


    /**
     * 生成并保存Excel模板文件
     * <p>
     * 根据提供的租户编码、应用ID、文件名和导出列信息，生成一个Excel模板文件，并将其保存到服务器并返回相应的Base64Info对象。
     *
     * @param tenantCode    租户编码
     * @param appId         应用ID
     * @param fileName      文件名
     * @param exportColumns 导出列信息列表
     * @return 返回包含文件信息的Base64Info对象
     * @throws IOException 如果在文件操作过程中出现I/O异常
     */
    private Base64Helper getTemplate(String tenantCode, String appId, String fileName, List<ExportColumn> exportColumns) throws IOException {
        Base64Helper info;
        OutputStream outputStream = null;
        XSSFWorkbook workbook = null;
        try {
            // 创建文件
            String exportPath = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, SAVE_TABLE_TYPE, tenantCode, appId, fileName, true);
            // 读取文件，
            workbook = new XSSFWorkbook();
            excelXSSFWriter.generateTemplateFile(workbook, "list", exportColumns);
            // 输出数据到文件
            outputStream = new BufferedOutputStream(new FileOutputStream(exportPath));
            workbook.write(outputStream);
            outputStream.flush();
            workbook.close();
            // 保存附件
            FileParam fileParam = FileParamUtils.byLocal(SAVE_TABLE_TYPE, FileGenreEnum.exportTemplate.name(), appId, tenantCode);
            Attachment attachment = fileHandler.save(new File(exportPath), fileName, exportPath, fileParam);
            // 数据转换
            info = Base64Helper.fromAttachment(attachment);
        } catch (Exception ex) {
            throw new RuntimeException(ex.getMessage());
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            if (workbook != null) {
                workbook.close();
            }
        }

        return info;
    }

    /**
     * 将base64字符串转换为文件对象
     * <p>
     * 根据提供的base64字符串，将其解码为字节数组，并创建一个临时文件，将字节数组写入该文件。
     * 如果base64字符串长度大于64，则将其解析为Base64Info对象，并从中提取文件名和base64编码内容。
     * 如果base64字符串长度不大于64，则将其视为文件ID，并从文件系统中获取对应的文件。
     *
     * @param template base64字符串或文件ID
     * @return 返回包含文件信息和临时文件对象的Base64Info对象，如果转换失败则返回null
     */
    private Base64Helper getTemplate(String template) {
        Base64Helper info = null;
        if (Strings.isNotBlank(template)) {
            if (template.length() > 64) {
                try {
                    info = Base64Helper.fromString(template);
                    info.setFile(info.toTempFile());
                } catch (Exception ex) {
                    log.info(ex.getMessage(), ex);
                }
            } else {
                info = fileHandler.toBase64Helper(template);
            }
        }
        return info;
    }
}
