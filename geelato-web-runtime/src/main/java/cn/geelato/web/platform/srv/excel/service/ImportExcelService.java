package cn.geelato.web.platform.srv.excel.service;

import cn.geelato.meta.ExportTemplate;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.core.enums.MysqlDataTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.common.Base64Helper;
import cn.geelato.web.platform.srv.excel.exception.*;
import cn.geelato.web.platform.handler.FileHandler;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import cn.geelato.web.platform.srv.base.service.UploadService;
import cn.geelato.web.platform.srv.excel.entity.*;
import cn.geelato.meta.Attachment;
import cn.geelato.web.platform.srv.excel.exception.FileNotFoundException;
import cn.geelato.web.platform.srv.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.srv.file.enums.FileGenreEnum;
import cn.geelato.web.platform.srv.file.param.FileParam;
import cn.geelato.web.platform.utils.FileParamUtils;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.apache.poi.hssf.usermodel.HSSFCellStyle;
import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author diabl
 */
@Component
@Slf4j
public class ImportExcelService {
    private static final String REQUEST_FILE_PART = "file";
    private static final String REDIS_UNIQUE_KEY = "uniques";
    private static final double IMPORT_PAGE_SIZE = 100.0;
    private static final int ROW_ACCESS_WINDOW_SIZE = 50;
    private static final String SAVE_TABLE_TYPE = AttachmentSourceEnum.ATTACH.getValue();
    private final MetaManager metaManager = MetaManager.singleInstance();

    private final SimpleDateFormat sdf_dv = new SimpleDateFormat(DateUtils.DATEVARIETY);
    private final SimpleDateFormat sdf_y = new SimpleDateFormat(DateUtils.YEAR);
    private final SimpleDateFormat sdf_d = new SimpleDateFormat(DateUtils.DATE);
    private final SimpleDateFormat sdf_t = new SimpleDateFormat(DateUtils.TIME);
    private final SimpleDateFormat sdf_dt = new SimpleDateFormat(DateUtils.DATETIME);
    @Autowired
    protected RuleService ruleService;
    @Autowired
    private ExportTemplateService exportTemplateService;
    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private ExcelXSSFReader excelXSSFReader;
    @Autowired
    private FileHandler fileHandler;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ExcelCommonUtils excelCommonUtils;

    public ApiResult<?> importExcel(HttpServletRequest request, HttpServletResponse response, String importType, String templateId, String index, String attachId) {
        System.gc();
        String currentUUID = String.valueOf(UIDGenerator.generate());
        try {
            long importStart = System.currentTimeMillis();
            // 文件内容
            Map<String, List<BusinessMeta>> businessMetaListMap = new HashMap<>();// 元数据
            Map<String, BusinessTypeData> businessTypeDataMap = new HashMap<>();// 数据类型
            Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet = new LinkedHashSet<>();// 清洗规则
            List<Map<String, BusinessData>> businessDataMapList;// 业务数据
            // 事务模板查询
            ExportTemplate exportTemplate = exportTemplateService.getModel(ExportTemplate.class, templateId);
            ExcelCommonUtils.notNull(exportTemplate, new FileNotFoundException("ExportTemplate Data Not Found"));
            // 事务，模板元数据
            // Attachment templateRuleAttach = getFile(exportTemplate.getTemplateRule());
            // ExcelCommonUtils.notNull(templateRuleAttach, new FileNotFoundException("Business Data Type And Meta File Not Found"));
            File templateRuleFile = getTemplate(currentUUID, exportTemplate.getTemplateRule());
            if (templateRuleFile != null) {
                // 元数据
                businessMetaListMap = getBusinessMeta(templateRuleFile, 2);
                // 事务，模板数据类型
                businessTypeDataMap = getBusinessTypeData(templateRuleFile, 0);
                // 清洗规则
                businessTypeRuleDataSet = getBusinessTypeRuleData(templateRuleFile, 1);
            } else if (Strings.isNotBlank(exportTemplate.getBusinessTypeData()) &&
                    Strings.isNotBlank(exportTemplate.getBusinessRuleData()) && Strings.isNotBlank(exportTemplate.getBusinessMetaData())) {
                // 元数据
                businessMetaListMap = getBusinessMeta(exportTemplate.getBusinessMetaData());
                // 事务，模板数据类型
                businessTypeDataMap = getBusinessTypeData(exportTemplate.getBusinessTypeData());
                // 清洗规则
                businessTypeRuleDataSet = getBusinessTypeRuleData(exportTemplate.getBusinessRuleData());
            }
            if (businessMetaListMap == null || businessMetaListMap.isEmpty() ||
                    businessTypeDataMap == null || businessTypeDataMap.isEmpty() ||
                    businessTypeRuleDataSet == null || businessTypeRuleDataSet.isEmpty()) {
                throw new FileNotFoundException("Business Data Type And Meta File Not Found");
            }
            // 事务，业务数据
            Attachment businessFile = null;
            if (Strings.isNotBlank(attachId)) {
                businessFile = fileHandler.getAttachment(attachId);
                ExcelCommonUtils.notNull(businessFile, new FileNotFoundException("Business Data File Not Found"));
            }
            businessDataMapList = getBusinessData(businessFile, request, businessTypeDataMap, 0);
            // 忽略默认字段
            List<String> columnNames = excelCommonUtils.getDefaultColumns();
            // 表检查
            validateExcel(businessMetaListMap, businessTypeDataMap, businessDataMapList, columnNames);
            // 业务数据清洗规则
            businessDataMapList = excelCommonUtils.handleBusinessDataRules(currentUUID, businessDataMapList, businessTypeRuleDataSet);
            System.gc();
            // 需要转化的业务数据
            // businessDataMapList = excelCommonUtils.handleBusinessDataRule(currentUUID, businessDataMapList, true);
            // 需要分割的业务数据，多值数据处理
            // businessDataMapList = excelCommonUtils.handleBusinessDataMultiScene(businessDataMapList);
            // 需要转化的业务数据
            // businessDataMapList = excelCommonUtils.handleBusinessDataRule(currentUUID, businessDataMapList, false);
            // 设置缓存
            List<String> cacheKeys = excelCommonUtils.setCache(currentUUID, businessMetaListMap, businessDataMapList);
            // 获取
            long parseStart = System.currentTimeMillis();
            Map<ColumnMeta, Map<Object, Long>> repeatedData = new HashMap<>();
            Map<String, List<Map<String, Object>>> tableData = new HashMap<>();
            for (Map.Entry<String, List<BusinessMeta>> metaMap : businessMetaListMap.entrySet()) {
                // 获取表格字段信息
                EntityMeta entityMeta = metaManager.getByEntityName(metaMap.getKey(), false);
                Assert.notNull(entityMeta, String.format("Table Meta [%s] Is Null", metaMap.getKey()));
                // 数值唯一性校验，数值
                Map<String, ColumnMeta> uniqueColumns = excelCommonUtils.getUniqueColumns(entityMeta.getFieldMetas(), columnNames);
                List<String> uniqueRedis = excelCommonUtils.setUniqueRedis(currentUUID, metaMap.getKey(), uniqueColumns.keySet());
                cacheKeys.addAll(uniqueRedis);
                // 当前字段
                List<Map<String, Object>> columnData = new ArrayList<>();
                long countCow = 0;
                for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
                    // 一行业务数据，键值对
                    Map<String, Object> valueMap = new HashMap<>();
                    for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                        valueMap.put(businessDataEntry.getKey(), businessDataEntry.getValue().getValue());
                    }
                    // 一行数据库数据
                    Map<String, Object> columnMap = new HashMap<>();
                    long start = System.currentTimeMillis();
                    for (BusinessMeta meta : metaMap.getValue()) {
                        FieldMeta fieldMeta = entityMeta.getFieldMeta(meta.getColumnName());
                        Assert.notNull(fieldMeta, String.format("Table ColumnName [%s] Is Null", meta.getColumnName()));
                        Object value = null;
                        BusinessData businessData = businessDataMap.get(meta.getVariableValue());
                        if (businessData != null) {
                            try {
                                // 获取值
                                value = getValue(currentUUID, fieldMeta.getColumnMeta(), meta, businessData, valueMap);
                                // 验证值
                                Set<String> errorMsg = validateValue(currentUUID, fieldMeta.getColumnMeta(), businessData, value, columnNames);
                                businessData.addAllErrorMsg(errorMsg);
                            } catch (Exception ex) {
                                businessData.addErrorMsg(ex.getMessage());
                            }
                        } else if (meta.isEvaluationTypeConst()) {
                            value = meta.getConstValue();
                        } else if (meta.isEvaluationTypeJsExpression()) {
                            value = JsProvider.executeExpression(meta.getExpression(), valueMap);
                        } else if (meta.isEvaluationTypeSerialNumber()) {
                            value = currentUUID;
                        }
                        columnMap.put(meta.getColumnName(), value);
                    }
                    columnData.add(columnMap);
                }
                repeatedData.putAll(validateValue(uniqueColumns, columnData));
                tableData.put(metaMap.getKey(), columnData);
            }
            System.gc();
            // 数据唯一性校验
            // 释放缓存
            redisTemplate.delete(cacheKeys);
            // 业务数据校验
            if (!validBusinessData(businessDataMapList) || !repeatedData.isEmpty()) {
                Attachment errorAttach = writeBusinessData(exportTemplate, businessFile, request, response, businessDataMapList, repeatedData, 0);
                return ApiResult.fail(errorAttach).exception(new FileContentValidFailedException("For more information, see the error file."));
            }
            // 插入数据 "@biz": "myBizCode",
            long insertStart = System.currentTimeMillis();
            List<String> returnPks;
            if (!tableData.isEmpty()) {
                Map<String, Object> insertMap = new HashMap<>();
                insertMap.put("@biz", "myBizCode");
                for (Map.Entry<String, List<Map<String, Object>>> table : tableData.entrySet()) {
                    /*List<Map<String, Object>> columns = table.getValue();
                    if (columns != null && columns.size() > 0) {
                        int total = columns.size();
                        int page = (int) Math.ceil(total / IMPORT_PAGE_SIZE);
                        int size = (int) IMPORT_PAGE_SIZE;
                        for (int i = 0; i < page; i++) {
                            int maxSize = ((i + 1) * size) > total ? total : ((i + 1) * size);
                            List<Map<String, Object>> insertList = new ArrayList<>();
                            for (int n = (i * size); n < maxSize; n++) {
                                insertList.add(columns.get(n));
                            }
                        }
                    }*/
                    insertMap.put(table.getKey(), table.getValue());
                }
                returnPks = (List<String>) ruleService.batchSave(JSON.toJSONString(insertMap), "all".equalsIgnoreCase(importType));
            } else {
                throw new FileContentIsEmptyException("Business Import Data Is Empty");
            }
            String message = String.format("导入数量：预计 [%s]，实际 [%s]", businessDataMapList.size(), (returnPks == null ? 0 : returnPks.size()));
            return ApiResult.success(currentUUID, message);
        } catch (Exception ex) {
            return ApiResult.fail(currentUUID).exception(ex);
        } finally {
            System.gc();
        }
    }

    private void validateExcel(Map<String, List<BusinessMeta>> businessMetaListMap, Map<String, BusinessTypeData> businessTypeDataMap,
                               List<Map<String, BusinessData>> businessDataMapList, List<String> columnNames) {
        // 导入数据表头
        List<String> businessDataNames = new ArrayList<>();
        // 业务数据类型
        List<String> businessTypeDataNames = new ArrayList<>();
        if (businessDataMapList != null && !businessDataMapList.isEmpty()) {
            for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMapList.get(0).entrySet()) {
                businessDataNames.add(businessDataEntry.getKey());
            }
        }
        if (businessTypeDataMap != null && !businessTypeDataMap.isEmpty()) {
            for (Map.Entry<String, BusinessTypeData> businessTypeDataEntry : businessTypeDataMap.entrySet()) {
                businessTypeDataNames.add(businessTypeDataEntry.getKey());
            }
        }
        // 表头应该包含所有业务数据类型
        if (!new HashSet<>(businessDataNames).containsAll(businessTypeDataNames)) {
            throw new FileException(String.format("business data table header deficiency：[%s]", listSubtraction(businessTypeDataNames, businessDataNames)));
        }
        if (!new HashSet<>(businessTypeDataNames).containsAll(businessDataNames)) {
            throw new FileException(String.format("business type data name deficiency：[%s]", listSubtraction(businessDataNames, businessTypeDataNames)));
        }
        if (businessMetaListMap != null && !businessMetaListMap.isEmpty()) {
            for (Map.Entry<String, List<BusinessMeta>> businessMetaEntry : businessMetaListMap.entrySet()) {
                List<String> nullableColumnNames = new ArrayList<>();
                List<String> metaColumnNames = new ArrayList<>();
                List<String> metaVariableValues = new ArrayList<>();
                // 获取表格字段信息
                EntityMeta entityMeta = metaManager.getByEntityName(businessMetaEntry.getKey(), false);
                Assert.notNull(entityMeta, "Table Meta Is Null");
                // 必填项
                Map<String, ColumnMeta> nullableColumns = excelCommonUtils.getNullableColumns(entityMeta.getFieldMetas(), columnNames);

                if (nullableColumns != null && !nullableColumns.isEmpty()) {
                    for (Map.Entry<String, ColumnMeta> columnMetaEntry : nullableColumns.entrySet()) {
                        nullableColumnNames.add(columnMetaEntry.getKey());
                    }
                }
                if (businessMetaEntry.getValue() != null && !businessMetaEntry.getValue().isEmpty()) {
                    for (BusinessMeta businessMeta : businessMetaEntry.getValue()) {
                        if (Strings.isNotBlank(businessMeta.getColumnName())) {
                            metaColumnNames.add(businessMeta.getColumnName());
                        }
                        if (Strings.isNotBlank(businessMeta.getVariableValue())) {
                            metaVariableValues.add(businessMeta.getVariableValue());
                        }
                    }
                }
                // 元数据必须包含 必填项
                if (!new HashSet<>(metaColumnNames).containsAll(nullableColumnNames)) {
                    throw new FileException(String.format("business meta required fields are missing：[%s]", listSubtraction(nullableColumnNames, metaColumnNames)));
                }
                // 数据类型必须包含 用到的所有变量
                if (!new HashSet<>(businessTypeDataNames).containsAll(metaVariableValues)) {
                    throw new FileException(String.format("business meta variable values are missing：[%s]", listSubtraction(metaVariableValues, businessTypeDataNames)));
                }
            }
        }
    }

    private String listSubtraction(List<String> listA, List<String> listB) {
        List<String> difference = new ArrayList<>(listA);
        difference.removeAll(listB);

        return String.join(",", difference);
    }

    /**
     * 业务数据类型与元数据类型校验
     * <p>
     * 对给定的业务数据类型和值进行校验，确保其符合元数据中的数据类型和约束条件。
     *
     * @param currentUUID  批次号，用于唯一标识当前批次的数据
     * @param columnMeta   元数据对象，包含字段名、数据类型、长度、精度等信息
     * @param businessData 业务数据类型对象，包含业务类型数据
     * @param value        待校验的值
     * @param columnNames  当前处理的数据列名集合
     * @return 返回包含校验错误信息的集合，如果校验通过则返回空集合
     */
    private Set<String> validateValue(String currentUUID, ColumnMeta columnMeta, BusinessData businessData, Object value, List<String> columnNames) {
        Set<String> errorMsg = new LinkedHashSet<>();
        BusinessTypeData typeData = businessData.getBusinessTypeData();

        if (MysqlDataTypeEnum.getBooleans().contains(columnMeta.getDataType())) {
            log.info("暂未处理");
        } else if (MysqlDataTypeEnum.getTinyBooleans().contains(columnMeta.getDataType()) && Arrays.asList(new String[]{"BIT", "SWITCH"}).contains(columnMeta.getSelectType())) {
            log.info("暂未处理");
        } else if (MysqlDataTypeEnum.getStrings().contains(columnMeta.getDataType())) {
            if (value != null && String.valueOf(value).length() > columnMeta.getCharMaxLength()) {
                errorMsg.add(String.format("当前长度：%s；已超出字段最大长度：%s。", String.valueOf(value).length(), columnMeta.getCharMaxLength()));
            }
        } else if (MysqlDataTypeEnum.getNumbers().contains(columnMeta.getDataType())) {
            if (value != null && String.valueOf(value).length() > (columnMeta.getNumericPrecision() + columnMeta.getNumericScale())) {
                errorMsg.add(String.format("当前长度：%s；已超出字段数值位数限制：%s。", String.valueOf(value).length(), (columnMeta.getNumericPrecision() + columnMeta.getNumericScale())));
            }
        } else if (MysqlDataTypeEnum.getDates().contains(columnMeta.getDataType())) {
            log.info("暂未处理");
        } else {
            log.info("暂未处理");
            // errorMsg.add(String.format("业务数据格式：%s；而数据库存储格式为：%s。", typeData.getType(), columnMeta.getDataType()));
        }
        if (value == null && !columnMeta.isNullable() && !columnNames.contains(columnMeta.getName())) {
            errorMsg.add(String.format("原始数据[%s]，对应字段值不能为空。", String.join("=>", businessData.getTransitionValueString())));
        }
        if (value != null && columnMeta.isUniqued() && !columnNames.contains(columnMeta.getName())) {
            Map<String, Set<Object>> redisValues = (Map<String, Set<Object>>) redisTemplate.opsForValue().get(String.format("%s:%s:%s", currentUUID, columnMeta.getTableName(), REDIS_UNIQUE_KEY));
            if (redisValues != null && !redisValues.isEmpty()) {
                Set<Object> values = redisValues.get(columnMeta.getFieldName());
                if (values != null && values.contains(value)) {
                    errorMsg.add(String.format("唯一约束，数据库已存在相同值[%s]。", value));
                }
            }
        }

        return errorMsg;
    }

    /**
     * 验证数据值
     * <p>
     * 根据提供的唯一列元数据和列数据，验证数据值是否重复，并返回重复的数据信息。
     *
     * @param uniqueColumns 包含唯一列元数据的映射，键为列名，值为列元数据对象
     * @param columnData    包含列数据的列表，每个元素为一个包含键值对的映射，键为列名，值为列数据
     * @return 返回包含重复数据信息的映射，键为列元数据对象，值为包含重复数据值和出现次数的映射
     */
    private Map<ColumnMeta, Map<Object, Long>> validateValue(Map<String, ColumnMeta> uniqueColumns, List<Map<String, Object>> columnData) {
        Map<ColumnMeta, Map<Object, Long>> repeateData = new HashMap<>();
        if (uniqueColumns != null && !uniqueColumns.isEmpty() && !columnData.isEmpty()) {
            for (Map.Entry<String, ColumnMeta> metaEntry : uniqueColumns.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (Map<String, Object> columnMap : columnData) {
                    Object value = columnMap.get(metaEntry.getKey());
                    if (value != null) {
                        values.add(value);
                    }
                }
                if (!values.isEmpty()) {
                    Map<Object, Long> countMap = values.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                    countMap = countMap.entrySet().stream().filter(entry -> entry.getValue() > 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (!countMap.isEmpty()) {
                        List<Map.Entry<Object, Long>> list = new ArrayList<>(countMap.entrySet());
                        list.sort(Map.Entry.<Object, Long>comparingByValue().reversed());
                        Map<Object, Long> sortedMap = new LinkedHashMap<>();
                        for (Map.Entry<Object, Long> entry : list) {
                            sortedMap.put(entry.getKey(), entry.getValue());
                        }
                        repeateData.put(metaEntry.getValue(), sortedMap);
                    }
                }
            }
        }

        return repeateData;
    }

    /**
     * 根据元数据和业务数据获取对应的值
     * <p>
     * 根据提供的当前UUID、列元数据、业务元数据、业务数据和一行业务数据映射，获取对应的值。
     *
     * @param currentUUID  当前UUID
     * @param columnMeta   列元数据，包含数据类型等信息
     * @param meta         业务元数据，包含评估类型、常量值、变量值、表达式、字典编码、主键值等信息
     * @param businessData 业务数据对象，包含值、原始值等信息
     * @param valueMap     一行业务数据映射，包含多个字段的值
     * @return 返回获取到的值，可能为null
     */
    private Object getValue(String currentUUID, ColumnMeta columnMeta, BusinessMeta meta, BusinessData businessData, Map<String, Object> valueMap) {
        Object value = null;
        if (meta.isEvaluationTypeConst()) {
            value = meta.getConstValue();
        } else if (meta.isEvaluationTypeVariable()) {
            value = businessData.getValue();
        } else if (meta.isEvaluationTypeJsExpression()) {
            value = JsProvider.executeExpression(meta.getExpression(), valueMap);
        } else if (meta.isEvaluationTypePrimaryKey()) {
            if (businessData.getValue() != null) {
                Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, meta.getPrimaryValue()));
                if (redisValues != null && !redisValues.isEmpty()) {
                    value = redisValues.get(String.valueOf(businessData.getValue()));
                }
            }
        } else if (meta.isEvaluationTypeCheckBox()) {
            if (businessData.getValue() != null) {
                Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, meta.getDictCode()));
                if (redisValues != null && !redisValues.isEmpty()) {
                    String[] oValues = String.valueOf(businessData.getValue()).split(",");
                    if (oValues.length > 0) {
                        Set<String> nValues = new LinkedHashSet<>();
                        for (String oValue : oValues) {
                            String nValue = redisValues.get(oValue);
                            if (Strings.isNotBlank(nValue)) {
                                nValues.add(nValue);
                            }
                        }
                        value = String.join(",", nValues);
                    }
                }
            }
        } else if (meta.isEvaluationTypeDictionary()) {
            if (businessData.getValue() != null) {
                Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, meta.getDictCode()));
                if (redisValues != null && !redisValues.isEmpty()) {
                    value = redisValues.get(String.valueOf(businessData.getValue()));
                }
            }
        } else if (meta.isEvaluationTypeSerialNumber()) {
            value = currentUUID;
        } else if (meta.isEvaluationTypePrimitive()) {
            value = businessData.getPrimevalValue();
        }
        if (value != null) {
            if ("year".equalsIgnoreCase(columnMeta.getDataType())) {
                value = sdf_y.format(value);
            } else if ("date".equalsIgnoreCase(columnMeta.getDataType())) {
                value = sdf_d.format(value);
            } else if ("time".equalsIgnoreCase(columnMeta.getDataType())) {
                value = sdf_t.format(value);
            } else if ("dateTime".equalsIgnoreCase(columnMeta.getDataType())) {
                value = sdf_dt.format(value);
            }
        }

        return value;
    }

    /**
     * 获取元数据
     * <p>
     * 从指定的Excel文件中读取指定工作表次序的业务元数据。
     *
     * @param file       要读取的Excel文件
     * @param sheetIndex 要读取的工作表次序
     * @return 返回包含业务元数据的映射，键为业务类型，值为对应的业务元数据列表
     * @throws IOException 如果在文件读取或处理过程中发生I/O异常，将抛出此异常
     */
    private Map<String, List<BusinessMeta>> getBusinessMeta(File file, int sheetIndex) throws IOException {
        Map<String, List<BusinessMeta>> businessMetaListMap;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            // excel文件类型
            String contentType = Files.probeContentType(file.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                businessMetaListMap = excelReader.readBusinessMeta(sheet);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                businessMetaListMap = excelXSSFReader.readBusinessMeta(sheet);
                workbook.close();
                workbook = null;
            } else {
                throw new FileTypeNotSupportedException("Business Meta, Excel Type: " + contentType);
            }
        } catch (IOException ex) {
            throw new FileException("Business Meta, Excel Sheet(" + sheetIndex + ") Reader Failed! " + ex.getMessage());
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

        return businessMetaListMap;
    }

    /**
     * 获取业务数据类型
     * <p>
     * 从指定的Excel文件中读取指定工作表的数据，并解析为业务数据类型数据。
     *
     * @param file       要读取的Excel文件对象
     * @param sheetIndex 要读取的工作表次序（从0开始计数）
     * @return 返回包含业务数据类型数据的映射，键为业务类型名称，值为对应的业务类型数据对象
     * @throws IOException 如果在文件读取或解析过程中发生I/O异常，将抛出该异常
     */
    private Map<String, BusinessTypeData> getBusinessTypeData(File file, int sheetIndex) throws IOException {
        Map<String, BusinessTypeData> businessTypeDataMap;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            // excel文件类型
            String contentType = Files.probeContentType(file.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                businessTypeDataMap = excelReader.readBusinessTypeData(sheet);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                businessTypeDataMap = excelXSSFReader.readBusinessTypeData(sheet);
                workbook.close();
                workbook = null;
            } else {
                throw new FileTypeNotSupportedException("Business Data Type, Excel Type: " + contentType);
            }
        } catch (IOException ex) {
            throw new FileException("Business Data Type, Excel Sheet(" + sheetIndex + ") Reader Failed! " + ex.getMessage());
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

        return businessTypeDataMap;
    }

    /**
     * 获取业务数据类型
     * <p>
     * 从指定的Excel文件中读取指定工作表次序的业务数据类型规则数据。
     *
     * @param file       Excel文件对象
     * @param sheetIndex 工作表次序，从0开始计数
     * @return 返回包含业务数据类型规则数据的集合
     * @throws IOException 如果在文件读取或处理过程中发生I/O异常，将抛出该异常
     */
    private Set<Map<Integer, BusinessTypeRuleData>> getBusinessTypeRuleData(File file, int sheetIndex) throws IOException {
        Set<Map<Integer, BusinessTypeRuleData>> typeRuleDataSet;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            // excel文件类型
            String contentType = Files.probeContentType(file.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                typeRuleDataSet = excelReader.readBusinessTypeRuleData(sheet);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                typeRuleDataSet = excelXSSFReader.readBusinessTypeRuleData(sheet);
                workbook.close();
                workbook = null;
            } else {
                throw new FileTypeNotSupportedException("Business Data Type Rule, Excel Type: " + contentType);
            }
        } catch (IOException ex) {
            throw new FileException("Business Data Type Rule, Excel Sheet(" + sheetIndex + ") Reader Failed! " + ex.getMessage());
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

        return typeRuleDataSet;
    }

    /**
     * 获取业务数据
     * <p>
     * 从上传的文件或请求中获取Excel数据，并解析为业务数据列表。
     *
     * @param businessFile        上传的文件对象，若为空则从请求中获取文件
     * @param request             HTTP请求对象，用于从请求中获取文件
     * @param businessTypeDataMap 业务数据类型映射，键为业务类型名称，值为对应的业务类型数据对象
     * @param sheetIndex          要读取的工作表次序（从0开始计数）
     * @return 返回包含业务数据的列表，每个元素为一个映射，键为列名，值为对应的业务数据对象
     * @throws IOException 如果在文件读取或解析过程中发生I/O异常，将抛出该异常
     */
    private List<Map<String, BusinessData>> getBusinessData(Attachment businessFile, HttpServletRequest request, Map<String, BusinessTypeData> businessTypeDataMap, int sheetIndex) throws IOException {
        List<Map<String, BusinessData>> businessDataMapList;
        InputStream inputStream = null;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            String contentType;
            if (businessFile != null) {
                File file = fileHandler.toFile(businessFile);
                contentType = Files.probeContentType(file.toPath());
                fileInputStream = new FileInputStream(file);
                bufferedInputStream = new BufferedInputStream(fileInputStream);
            } else {
                Part filePart = request.getPart(REQUEST_FILE_PART);
                contentType = filePart.getContentType();
                inputStream = filePart.getInputStream();
                bufferedInputStream = new BufferedInputStream(inputStream);
            }
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                sheet.setForceFormulaRecalculation(true);
                HSSFFormulaEvaluator evaluator = (HSSFFormulaEvaluator) workbook.getCreationHelper().createFormulaEvaluator();
                businessDataMapList = excelReader.readBusinessData(sheet, evaluator, businessTypeDataMap);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                sheet.setForceFormulaRecalculation(true);
                XSSFFormulaEvaluator evaluator = (XSSFFormulaEvaluator) workbook.getCreationHelper().createFormulaEvaluator();
                businessDataMapList = excelXSSFReader.readBusinessData(sheet, evaluator, businessTypeDataMap);
                workbook.close();
                workbook = null;
            } else {
                throw new FileTypeNotSupportedException("Business Data, Excel Type: " + contentType);
            }
        } catch (Exception ex) {
            throw new FileException("Business Data, Excel Sheet(" + sheetIndex + ") Reader Failed! " + ex.getMessage());
        } finally {
            if (bufferedInputStream != null) {
                bufferedInputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (workbook != null) {
                workbook.close();
            }
        }

        return businessDataMapList;
    }

    /**
     * 业务数据校验
     * <p>
     * 对传入的业务数据进行校验，确保所有数据均通过验证。
     *
     * @param businessDataMapList 业务数据列表，每个元素为一个映射，映射的键为业务类型名称，值为对应的业务数据对象
     * @return 如果所有数据均通过验证，则返回true；否则返回false
     */
    private boolean validBusinessData(List<Map<String, BusinessData>> businessDataMapList) {
        boolean isValid = true;
        for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
            for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                BusinessData businessData = businessDataEntry.getValue();
                if (!businessData.isValidate()) {
                    isValid = false;
                    break;
                }
            }
            if (!isValid) {
                break;
            }
        }

        return isValid;
    }

    /**
     * 有错误业务数据值，将批注写入对应表格，并生成新文件
     * <p>
     * 当业务数据中存在错误值时，将错误信息以批注的形式写入对应的Excel表格中，并生成一个新的文件。
     *
     * @param exportTemplate      导出模板对象，包含租户编码和应用Id等信息
     * @param businessFile        上传的业务数据文件对象，若为空则从请求中获取文件
     * @param request             HTTP请求对象，用于从请求中获取文件
     * @param response            HTTP响应对象，用于返回生成的文件
     * @param businessDataMapList 业务数据列表，每个元素为一个映射，映射的键为列名，值为对应的业务数据对象
     * @param repeatedData        包含重复数据信息的映射，键为列元数据对象，值为包含重复数据值和出现次数的映射
     * @param sheetIndex          要写入批注的工作表次序（从0开始计数）
     * @return 返回包含新生成文件信息的Attach对象
     * @throws IOException 如果在文件读写或处理过程中发生I/O异常，将抛出该异常
     */
    private Attachment writeBusinessData(ExportTemplate exportTemplate, Attachment businessFile, HttpServletRequest request, HttpServletResponse response, List<Map<String, BusinessData>> businessDataMapList, Map<ColumnMeta, Map<Object, Long>> repeatedData, int sheetIndex) throws IOException {
        Attachment attachment;

        InputStream inputStream = null;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        OutputStream outputStream = null;
        // OutputStream responseOut = null;
        // FileInputStream responseIn = null;
        Workbook workbook = null;
        SXSSFWorkbook sWorkbook = null;
        try {
            String contentType;
            String fileName;
            if (businessFile != null) {
                File file = fileHandler.toFile(businessFile);
                contentType = Files.probeContentType(file.toPath());
                fileName = businessFile.getName();
                // 输入流
                fileInputStream = new FileInputStream(file);
                bufferedInputStream = new BufferedInputStream(fileInputStream);
            } else {
                // 业务文件
                Part filePart = request.getPart("file");
                contentType = filePart.getContentType();
                fileName = filePart.getSubmittedFileName();
                // 输入流
                inputStream = filePart.getInputStream();
                bufferedInputStream = new BufferedInputStream(inputStream);
            }
            String templateExt = fileName.substring(fileName.lastIndexOf("."));
            String templateName = fileName.substring(0, fileName.lastIndexOf("."));
            // 错误文件 upload/存放表/租户编码/应用Id
            String errorFileName = String.format("%s：%s%s%s", templateName, "错误提示 ", sdf_dv.format(new Date()), templateExt);
            String directory = UploadService.getRootSavePath(SAVE_TABLE_TYPE, exportTemplate.getTenantCode(), exportTemplate.getAppId(), errorFileName, true);
            File errorFile = new File(directory);
            // 文件处理
            if (MediaTypes.APPLICATION_EXCEL_XLS.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                // 创建CellStyle对象并设置填充颜色
                HSSFCellStyle style = (HSSFCellStyle) workbook.createCellStyle();
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                // 写入工作表
                excelReader.writeBusinessData(sheet, style, businessDataMapList);
                // 写入文件
                outputStream = new FileOutputStream(errorFile);
                workbook.write(outputStream);
                workbook.close();
                workbook = null;
                fileSystem.close();
            } else if (MediaTypes.APPLICATION_EXCEL_XLSX.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                ExcelXSSFUtils.reserveSheet((XSSFWorkbook) workbook, sheetIndex);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(0);
                // 创建CellStyle对象并设置填充颜色
                XSSFCellStyle style = (XSSFCellStyle) workbook.createCellStyle();
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                // 写入工作表
                excelXSSFReader.writeBusinessData(sheet, style, businessDataMapList);
                excelXSSFReader.writeRepeatedData((XSSFWorkbook) workbook, repeatedData);
                // 大数据导入
                int maxRow = sheet.getLastRowNum();
                sWorkbook = new SXSSFWorkbook((XSSFWorkbook) workbook, Math.min(maxRow, ROW_ACCESS_WINDOW_SIZE));
                // 写入文件
                outputStream = new BufferedOutputStream(new FileOutputStream(errorFile));
                sWorkbook.write(outputStream);
                outputStream.flush();
                sWorkbook.dispose();
                sWorkbook.close();
                workbook.close();
                workbook = null;
            } else {
                throw new FileTypeNotSupportedException("Business Data, Excel Type: " + contentType);
            }
            // 保存文件信息
            FileParam fileParam = FileParamUtils.byLocal(SAVE_TABLE_TYPE, FileGenreEnum.importErrorFile.name(), exportTemplate.getAppId(), exportTemplate.getTenantCode());
            attachment = fileHandler.save(errorFile, errorFileName, directory, fileParam);
            // 可下载
            /*responseOut = response.getOutputStream();
            responseIn = new FileInputStream(errorFile);
            errorFileName = URLEncoder.encode(errorFileName, "UTF-8");
            String mineType = request.getServletContext().getMimeType(errorFileName);
            response.setContentType(mineType);
            response.setHeader("Content-Disposition", "attachment; filename=" + errorFileName);
            int len = 0;
            byte[] buffer = new byte[1024];
            while ((len = responseIn.read(buffer)) > 0) {
                responseOut.write(buffer, 0, len);
            }*/
        } catch (Exception ex) {
            throw new FileException("Business Data Error Message, Excel Sheet(" + sheetIndex + ") Writer Failed! " + ex.getMessage());
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
            if (bufferedInputStream != null) {
                bufferedInputStream.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            /*if (responseOut != null) {
                responseOut.close();
            }
            if (responseIn != null) {
                responseIn.close();
            }*/
            if (sWorkbook != null) {
                sWorkbook.close();
                sWorkbook.dispose();
            }
            if (workbook != null) {
                workbook.close();
            }
        }

        return attachment;
    }

    /**
     * 将base64字符串转换为文件
     * <p>
     * 根据提供的当前UUID和模板base64字符串，将其解码并转换为文件对象。
     *
     * @param currentUUID 当前操作的UUID
     * @param template    包含文件信息的base64字符串
     * @return 返回转换后的文件对象，如果转换失败则返回null
     */
    private File getTemplate(String currentUUID, String template) {
        File file = null;
        if (Strings.isNotBlank(template)) {
            if (template.length() > 64) {
                try {
                    file = Base64Helper.toTempFile(template);
                } catch (Exception ex) {
                    log.info(ex.getMessage(), ex);
                }
            } else {
                file = fileHandler.toFile(template);
            }
        }

        return file;
    }

    /**
     * 模板数据解析
     * <p>
     * 解析传入的JSON字符串，将其转换为业务元数据映射。
     *
     * @param jsonText 包含业务元数据的JSON字符串
     * @return 返回包含业务元数据的映射，键为表名，值为对应的业务元数据列表
     */
    private Map<String, List<BusinessMeta>> getBusinessMeta(String jsonText) {
        Map<String, List<BusinessMeta>> businessMetaListMap = new HashMap<>();
        List<String> tables = new ArrayList<>();
        try {
            List<BusinessMeta> dataList = JSON.parseArray(jsonText, BusinessMeta.class);
            if (dataList != null && !dataList.isEmpty()) {
                for (BusinessMeta meta : dataList) {
                    if (!tables.contains(meta.getTableName())) {
                        tables.add(meta.getTableName());
                    }
                }
            }
            // 按表格分类
            for (String tableName : tables) {
                List<BusinessMeta> metas = new ArrayList<>();
                for (BusinessMeta meta : dataList) {
                    if (tableName.equalsIgnoreCase(meta.getTableName())) {
                        metas.add(meta);
                    }
                }
                businessMetaListMap.put(tableName, metas);
            }
        } catch (Exception ex) {
            businessMetaListMap = null;
        }

        return businessMetaListMap;
    }

    /**
     * 模板数据解析
     * <p>
     * 解析传入的JSON字符串，将其转换为业务类型规则数据的集合。
     *
     * @param jsonText 包含业务类型规则数据的JSON字符串
     * @return 返回包含业务类型规则数据的集合，如果解析失败则返回null
     */
    private Set<Map<Integer, BusinessTypeRuleData>> getBusinessTypeRuleData(String jsonText) {
        Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet = new LinkedHashSet<>();
        try {
            List<BusinessTypeRuleData> dataList = JSON.parseArray(jsonText, BusinessTypeRuleData.class);
            if (dataList != null && !dataList.isEmpty()) {
                dataList.sort(Comparator.comparingInt(BusinessTypeRuleData::getOrder));
                for (int i = 0; i < dataList.size(); i++) {
                    Map<Integer, BusinessTypeRuleData> ruleDataMap = new HashMap<>();
                    ruleDataMap.put(i + 1, dataList.get(i));
                    businessTypeRuleDataSet.add(ruleDataMap);
                }
            }
        } catch (Exception ex) {
            businessTypeRuleDataSet = null;
        }

        return businessTypeRuleDataSet;
    }

    /**
     * 模板数据解析
     * <p>
     * 解析传入的JSON字符串，将其转换为业务类型数据的映射。
     *
     * @param jsonText 包含业务类型数据的JSON字符串
     * @return 返回包含业务类型数据的映射，键为业务类型名称，值为对应的业务类型数据对象；如果解析失败则返回null
     */
    private Map<String, BusinessTypeData> getBusinessTypeData(String jsonText) {
        Map<String, BusinessTypeData> businessTypeDataMap = new HashMap<>();
        try {
            List<BusinessTypeData> dataList = JSON.parseArray(jsonText, BusinessTypeData.class);
            if (dataList != null && !dataList.isEmpty()) {
                for (BusinessTypeData meta : dataList) {
                    businessTypeDataMap.put(meta.getName(), meta);
                }
            }
        } catch (Exception ex) {
            businessTypeDataMap = null;
        }

        return businessTypeDataMap;
    }
}
