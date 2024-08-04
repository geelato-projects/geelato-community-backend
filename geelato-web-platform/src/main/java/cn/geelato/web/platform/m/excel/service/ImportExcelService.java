package cn.geelato.web.platform.m.excel.service;

import cn.geelato.web.platform.exception.file.*;
import cn.geelato.web.platform.exception.file.FileNotFoundException;
import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.excel.entity.*;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
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
import cn.geelato.lang.api.ApiResult;
import cn.geelato.core.enums.MysqlDataTypeEnum;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.utils.UIDGenerator;
import cn.geelato.web.platform.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.exception.file.*;
import cn.geelato.web.platform.m.base.entity.Attach;
import cn.geelato.web.platform.m.base.entity.Base64Info;
import cn.geelato.web.platform.m.base.service.AttachService;
import cn.geelato.web.platform.m.excel.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author diabl
 * @date 2024/3/12 14:39
 */
@Component
public class ImportExcelService {
    private static final String EXCEL_XLS_CONTENT_TYPE = "application/vnd.ms-excel";
    private static final String EXCEL_XLSX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final String REQUEST_FILE_PART = "file";
    private static final String IMPORT_ERROR_FILE_GENRE = "importErrorFile";
    private static final String REDIS_UNIQUE_KEY = "uniques";
    private static final double IMPORT_PAGE_SIZE = 100.0;
    private static final int ROW_ACCESS_WINDOW_SIZE = 50;
    private final Logger logger = LoggerFactory.getLogger(ImportExcelService.class);
    private final MetaManager metaManager = MetaManager.singleInstance();

    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyyMMddHHmmss");
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    @Autowired
    protected RuleService ruleService;
    @Autowired
    private ExportTemplateService exportTemplateService;
    @Autowired
    private ExcelReader excelReader;
    @Autowired
    private ExcelXSSFReader excelXSSFReader;
    @Autowired
    private ExcelSXSSFWriter excelSXSSFWriter;
    @Autowired
    private UploadService uploadService;
    @Autowired
    private AttachService attachService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private ExcelCommonUtils excelCommonUtils;

    public ApiResult importExcel(HttpServletRequest request, HttpServletResponse response, String importType, String templateId, String attachId) {
        System.gc();
        ApiResult result = new ApiResult();
        String currentUUID = String.valueOf(UIDGenerator.generate());
        try {
            long importStart = System.currentTimeMillis();
            // 文件内容
            Map<String, List<BusinessMeta>> businessMetaListMap = new HashMap<>();// 元数据
            Map<String, BusinessTypeData> businessTypeDataMap = new HashMap<>();// 数据类型
            Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet = new LinkedHashSet<>();// 清洗规则
            List<Map<String, BusinessData>> businessDataMapList = new ArrayList<>();// 业务数据
            // 事务模板查询
            ExportTemplate exportTemplate = exportTemplateService.getModel(ExportTemplate.class, templateId);
            ExcelCommonUtils.notNull(exportTemplate, new FileNotFoundException("ExportTemplate Data Not Found"));
            logger.info(String.format("事务模板（%s[%s]）", exportTemplate.getTitle(), exportTemplate.getId()));
            // 事务，模板元数据
            // Attach templateRuleAttach = getFile(exportTemplate.getTemplateRule());
            // ExcelCommonUtils.notNull(templateRuleAttach, new FileNotFoundException("Business Data Type And Meta File Not Found"));
            // logger.info(String.format("数据类型+元数据（%s[%s]）%s", templateRuleAttach.getName(), templateRuleAttach.getId(), templateRuleAttach.getPath()));
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
            Attach businessFile = null;
            if (Strings.isNotBlank(attachId)) {
                businessFile = getFile(attachId);
                ExcelCommonUtils.notNull(businessFile, new FileNotFoundException("Business Data File Not Found"));
                logger.info(String.format("业务数据（%s[%s]）[%s]", businessFile.getName(), businessFile.getId(), sdf.format(new Date())));
            }
            businessDataMapList = getBusinessData(businessFile, request, businessTypeDataMap, 0);
            // 忽略默认字段
            List<String> columnNames = excelCommonUtils.getDefaultColumns();
            // 表检查
            validateExcel(businessMetaListMap, businessTypeDataMap, businessDataMapList, columnNames);
            // 业务数据清洗规则
            businessDataMapList = excelCommonUtils.handleBusinessDataRules(currentUUID, businessDataMapList, businessTypeRuleDataSet);
            System.gc();
            logger.info(String.format("BusinessData Handle Rule [TRUE] = %s [%s]", (businessDataMapList == null ? 0 : businessDataMapList.size()), sdf.format(new Date())));
            // 需要转化的业务数据
            // businessDataMapList = excelCommonUtils.handleBusinessDataRule(currentUUID, businessDataMapList, true);
            // logger.info(String.format("BusinessData Handle Rule [TRUE] = %s [%s]", (businessDataMapList == null ? 0 : businessDataMapList.size()), sdf.format(new Date())));
            // 需要分割的业务数据，多值数据处理
            // businessDataMapList = excelCommonUtils.handleBusinessDataMultiScene(businessDataMapList);
            // logger.info(String.format("BusinessData Handle Multi Scene = %s [%s]", (businessDataMapList == null ? 0 : businessDataMapList.size()), sdf.format(new Date())));
            // 需要转化的业务数据
            // businessDataMapList = excelCommonUtils.handleBusinessDataRule(currentUUID, businessDataMapList, false);
            // logger.info(String.format("BusinessData Handle Rule [FALSE] = %s [%s]", (businessDataMapList == null ? 0 : businessDataMapList.size()), sdf.format(new Date())));
            // 设置缓存
            List<String> cacheKeys = excelCommonUtils.setCache(currentUUID, businessMetaListMap, businessDataMapList);
            logger.info(String.format("Redis Template [ADD] = %s [%s]", (cacheKeys == null ? 0 : cacheKeys.size()), sdf.format(new Date())));
            // 获取
            logger.info(String.format("业务数据解析-开始 [%s]", sdf.format(new Date())));
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
                                value = getValue(currentUUID, fieldMeta.getColumn(), meta, businessData, valueMap);
                                // 验证值
                                Set<String> errorMsg = validateValue(currentUUID, fieldMeta.getColumn(), businessData, value, columnNames);
                                businessData.setErrorMsgs(errorMsg);
                            } catch (Exception ex) {
                                businessData.setErrorMsg(ex.getMessage());
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
                    logger.info(String.format("表 %s 解析成功，第 %s 行。用时 %s ms。", metaMap.getKey(), (countCow += 1), (System.currentTimeMillis() - start)));
                    columnData.add(columnMap);
                }
                repeatedData.putAll(validateValue(uniqueColumns, columnData));
                tableData.put(metaMap.getKey(), columnData);
            }
            System.gc();
            // 数据唯一性校验
            logger.info(String.format("业务数据解析-结束 用时：%s ms", (System.currentTimeMillis() - parseStart)));
            // 释放缓存
            redisTemplate.delete(cacheKeys);
            logger.info(String.format("Redis Template [DELETE] [%s]", sdf.format(new Date())));
            // 业务数据校验
            if (!validBusinessData(businessDataMapList) || repeatedData.size() > 0) {
                Attach errorAttach = writeBusinessData(exportTemplate, businessFile, request, response, businessDataMapList, repeatedData, 0);
                logger.info(String.format("业务数据校验-错误 [%s]", sdf.format(new Date())));
                return result.error(new FileContentValidFailedException("For more information, see the error file.")).setData(errorAttach);
            }
            // 插入数据 "@biz": "myBizCode",
            logger.info(String.format("插入业务数据-开始 [%s]", sdf.format(new Date())));
            long insertStart = System.currentTimeMillis();
            List<String> returnPks = new ArrayList<>();
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
                            logger.info(String.format("插入数据范围：[%s, %s)", i * size, maxSize));
                            List<Map<String, Object>> insertList = new ArrayList<>();
                            for (int n = (i * size); n < maxSize; n++) {
                                insertList.add(columns.get(n));
                            }
                        }
                    }*/
                    insertMap.put(table.getKey(), table.getValue());
                }
                returnPks = (List<String>) ruleService.batchSave(JSON.toJSONString(insertMap), "all".equalsIgnoreCase(importType));
                result.setMsg(String.format("导入数量：预计 [%s]，实际 [%s]", businessDataMapList.size(), (returnPks == null ? 0 : returnPks.size())));
            } else {
                throw new FileContentIsEmptyException("Business Import Data Is Empty");
            }
            logger.info(String.format("插入业务数据-结束 用时：%s ms", (System.currentTimeMillis() - insertStart)));
            logger.info(String.format("导入业务数据-结束 用时：%s ms", (System.currentTimeMillis() - importStart)));
            logger.info(String.format("导入业务数据-数量 预计 [%s]，实际 [%s]", businessDataMapList.size(), (returnPks == null ? 0 : returnPks.size())));
            result.setData(currentUUID);
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            result.error(ex).setData(currentUUID);
        } finally {
            System.gc();
        }

        return result;
    }

    private void validateExcel(Map<String, List<BusinessMeta>> businessMetaListMap, Map<String, BusinessTypeData> businessTypeDataMap,
                               List<Map<String, BusinessData>> businessDataMapList, List<String> columnNames) {
        // 导入数据表头
        List<String> businessDataNames = new ArrayList<>();
        // 业务数据类型
        List<String> businessTypeDataNames = new ArrayList<>();
        if (businessDataMapList != null && businessDataMapList.size() > 0) {
            for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMapList.get(0).entrySet()) {
                businessDataNames.add(businessDataEntry.getKey());
            }
        }
        if (businessTypeDataMap != null && businessTypeDataMap.size() > 0) {
            for (Map.Entry<String, BusinessTypeData> businessTypeDataEntry : businessTypeDataMap.entrySet()) {
                businessTypeDataNames.add(businessTypeDataEntry.getKey());
            }
        }
        // 表头应该包含所有业务数据类型
        if (!businessDataNames.containsAll(businessTypeDataNames)) {
            throw new FileException(String.format("business data table header deficiency：[%s]", listSubtraction(businessTypeDataNames, businessDataNames)));
        }
        if (!businessTypeDataNames.containsAll(businessDataNames)) {
            throw new FileException(String.format("business type data name deficiency：[%s]", listSubtraction(businessDataNames, businessTypeDataNames)));
        }
        if (businessMetaListMap != null && businessMetaListMap.size() > 0) {
            for (Map.Entry<String, List<BusinessMeta>> businessMetaEntry : businessMetaListMap.entrySet()) {
                List<String> nullableColumnNames = new ArrayList<>();
                List<String> metaColumnNames = new ArrayList<>();
                List<String> metaVariableValues = new ArrayList<>();
                // 获取表格字段信息
                EntityMeta entityMeta = metaManager.getByEntityName(businessMetaEntry.getKey(), false);
                Assert.notNull(entityMeta, "Table Meta Is Null");
                // 必填项
                Map<String, ColumnMeta> nullableColumns = excelCommonUtils.getNullableColumns(entityMeta.getFieldMetas(), columnNames);

                if (nullableColumns != null && nullableColumns.size() > 0) {
                    for (Map.Entry<String, ColumnMeta> columnMetaEntry : nullableColumns.entrySet()) {
                        nullableColumnNames.add(columnMetaEntry.getKey());
                    }
                }
                if (businessMetaEntry.getValue() != null && businessMetaEntry.getValue().size() > 0) {
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
                if (!metaColumnNames.containsAll(nullableColumnNames)) {
                    throw new FileException(String.format("business meta required fields are missing：[%s]", listSubtraction(nullableColumnNames, metaColumnNames)));
                }
                // 数据类型必须包含 用到的所有变量
                if (!businessTypeDataNames.containsAll(metaVariableValues)) {
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
     *
     * @param currentUUID  批次号
     * @param columnMeta   元数据
     * @param businessData 业务数据类型
     * @param value        值
     * @return
     */
    private Set<String> validateValue(String currentUUID, ColumnMeta columnMeta, BusinessData businessData, Object value, List<String> columnNames) {
        Set<String> errorMsg = new LinkedHashSet<>();
        BusinessTypeData typeData = businessData.getBusinessTypeData();

        if (MysqlDataTypeEnum.getBooleans().contains(columnMeta.getDataType())) {

        } else if (MysqlDataTypeEnum.getTinyBooleans().contains(columnMeta.getDataType()) && Arrays.asList(new String[]{"BIT", "SWITCH"}).contains(columnMeta.getSelectType())) {

        } else if (MysqlDataTypeEnum.getStrings().contains(columnMeta.getDataType())) {
            if (value != null && String.valueOf(value).length() > columnMeta.getCharMaxLength()) {
                errorMsg.add(String.format("当前长度：%s；已超出字段最大长度：%s。", String.valueOf(value).length(), columnMeta.getCharMaxLength()));
            }
        } else if (MysqlDataTypeEnum.getNumbers().contains(columnMeta.getDataType())) {
            if (value != null && String.valueOf(value).length() > (columnMeta.getNumericPrecision() + columnMeta.getNumericScale())) {
                errorMsg.add(String.format("当前长度：%s；已超出字段数值位数限制：%s。", String.valueOf(value).length(), (columnMeta.getNumericPrecision() + columnMeta.getNumericScale())));
            }
        } else if (MysqlDataTypeEnum.getDates().contains(columnMeta.getDataType())) {

        } else {
            // errorMsg.add(String.format("业务数据格式：%s；而数据库存储格式为：%s。", typeData.getType(), columnMeta.getDataType()));
        }
        if (value == null && !columnMeta.isNullable() && !columnNames.contains(columnMeta.getName())) {
            errorMsg.add(String.format("原始数据[%s]，对应字段值不能为空。", String.join("=>", businessData.getTransitionValueString())));
        }
        if (value != null && columnMeta.isUniqued() && !columnNames.contains(columnMeta.getName())) {
            Map<String, Set<Object>> redisValues = (Map<String, Set<Object>>) redisTemplate.opsForValue().get(String.format("%s:%s:%s", currentUUID, columnMeta.getTableName(), REDIS_UNIQUE_KEY));
            if (redisValues != null && redisValues.size() > 0) {
                Set<Object> values = redisValues.get(columnMeta.getFieldName());
                if (values != null && values.contains(value)) {
                    errorMsg.add(String.format("唯一约束，数据库已存在相同值[%s]。", value));
                }
            }
        }

        return errorMsg;
    }

    private Map<ColumnMeta, Map<Object, Long>> validateValue(Map<String, ColumnMeta> uniqueColumns, List<Map<String, Object>> columnData) {
        Map<ColumnMeta, Map<Object, Long>> repeateData = new HashMap<>();
        if (uniqueColumns != null && uniqueColumns.size() > 0 && columnData.size() > 0) {
            for (Map.Entry<String, ColumnMeta> metaEntry : uniqueColumns.entrySet()) {
                List<Object> values = new ArrayList<>();
                for (Map<String, Object> columnMap : columnData) {
                    Object value = columnMap.get(metaEntry.getKey());
                    if (value != null) {
                        values.add(value);
                    }
                }
                if (values.size() > 0) {
                    Map<Object, Long> countMap = values.stream().collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
                    countMap = countMap.entrySet().stream().filter(entry -> entry.getValue() > 1).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                    if (countMap != null && countMap.size() > 0) {
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
     * 元数据对应的值
     *
     * @param currentUUID
     * @param columnMeta   元数据
     * @param meta         元数据
     * @param businessData 业务数据
     * @param valueMap     一行业务数据
     * @return
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
                if (redisValues != null && redisValues.size() > 0) {
                    value = redisValues.get(String.valueOf(businessData.getValue()));
                }
            }
        } else if (meta.isEvaluationTypeCheckBox()) {
            if (businessData.getValue() != null) {
                Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, meta.getDictCode()));
                if (redisValues != null && redisValues.size() > 0) {
                    String[] oValues = String.valueOf(businessData.getValue()).split(",");
                    if (oValues != null && oValues.length > 0) {
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
                if (redisValues != null && redisValues.size() > 0) {
                    value = redisValues.get(String.valueOf(businessData.getValue()));
                }
            }
        } else if (meta.isEvaluationTypeSerialNumber()) {
            value = currentUUID;
        } else if (meta.isEvaluationTypePrimitive()) {
            value = businessData.getPrimevalValue();
        }
        if (value != null) {
            if (columnMeta.getDataType().equalsIgnoreCase("year")) {
                value = new SimpleDateFormat("yyyy").format(value);
            } else if (columnMeta.getDataType().equalsIgnoreCase("date")) {
                value = new SimpleDateFormat("yyyy-MM-dd").format(value);
            } else if (columnMeta.getDataType().equalsIgnoreCase("time")) {
                value = new SimpleDateFormat("HH:mm:ss").format(value);
            } else if (columnMeta.getDataType().equalsIgnoreCase("dateTime")) {
                value = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(value);
            }
        }

        return value;
    }

    /**
     * 获取元数据
     *
     * @param file       文件
     * @param sheetIndex 工作表次序
     * @return
     * @throws IOException
     */
    private Map<String, List<BusinessMeta>> getBusinessMeta(File file, int sheetIndex) throws IOException {
        Map<String, List<BusinessMeta>> businessMetaListMap = new HashMap<>();
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            // excel文件类型
            String contentType = Files.probeContentType(file.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (EXCEL_XLS_CONTENT_TYPE.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                businessMetaListMap = excelReader.readBusinessMeta(sheet);
                workbook.close();
            } else if (EXCEL_XLSX_CONTENT_TYPE.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                businessMetaListMap = excelXSSFReader.readBusinessMeta(sheet);
                workbook.close();
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
     *
     * @param file       文件
     * @param sheetIndex 工作表次序
     * @return
     * @throws IOException
     */
    private Map<String, BusinessTypeData> getBusinessTypeData(File file, int sheetIndex) throws IOException {
        Map<String, BusinessTypeData> businessTypeDataMap = new HashMap<>();
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            // excel文件类型
            String contentType = Files.probeContentType(file.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (EXCEL_XLS_CONTENT_TYPE.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                businessTypeDataMap = excelReader.readBusinessTypeData(sheet);
                workbook.close();
            } else if (EXCEL_XLSX_CONTENT_TYPE.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                businessTypeDataMap = excelXSSFReader.readBusinessTypeData(sheet);
                workbook.close();
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
     *
     * @param file       文件
     * @param sheetIndex 工作表次序
     * @return
     * @throws IOException
     */
    private Set<Map<Integer, BusinessTypeRuleData>> getBusinessTypeRuleData(File file, int sheetIndex) throws IOException {
        Set<Map<Integer, BusinessTypeRuleData>> typeRuleDataSet = new LinkedHashSet<>();
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            // excel文件类型
            String contentType = Files.probeContentType(file.toPath());
            // 读取文件
            fileInputStream = new FileInputStream(file);
            bufferedInputStream = new BufferedInputStream(fileInputStream);
            if (EXCEL_XLS_CONTENT_TYPE.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                typeRuleDataSet = excelReader.readBusinessTypeRuleData(sheet);
                workbook.close();
            } else if (EXCEL_XLSX_CONTENT_TYPE.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                typeRuleDataSet = excelXSSFReader.readBusinessTypeRuleData(sheet);
                workbook.close();
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
     *
     * @param request
     * @param businessTypeDataMap 数据类型
     * @param sheetIndex          工作表次序
     * @return
     * @throws IOException
     */
    private List<Map<String, BusinessData>> getBusinessData(Attach businessFile, HttpServletRequest request, Map<String, BusinessTypeData> businessTypeDataMap, int sheetIndex) throws IOException {
        List<Map<String, BusinessData>> businessDataMapList = new ArrayList<>();
        InputStream inputStream = null;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        Workbook workbook = null;
        try {
            String contentType = null;
            if (businessFile != null) {
                File file = new File(businessFile.getPath());
                contentType = Files.probeContentType(file.toPath());
                fileInputStream = new FileInputStream(file);
                bufferedInputStream = new BufferedInputStream(fileInputStream);
            } else {
                Part filePart = request.getPart(REQUEST_FILE_PART);
                contentType = filePart.getContentType();
                inputStream = filePart.getInputStream();
                bufferedInputStream = new BufferedInputStream(inputStream);
            }
            if (EXCEL_XLS_CONTENT_TYPE.equals(contentType)) {
                POIFSFileSystem fileSystem = new POIFSFileSystem(bufferedInputStream);
                workbook = new HSSFWorkbook(fileSystem);
                HSSFSheet sheet = (HSSFSheet) workbook.getSheetAt(sheetIndex);
                sheet.setForceFormulaRecalculation(true);
                HSSFFormulaEvaluator evaluator = (HSSFFormulaEvaluator) workbook.getCreationHelper().createFormulaEvaluator();
                businessDataMapList = excelReader.readBusinessData(sheet, evaluator, businessTypeDataMap);
                workbook.close();
            } else if (EXCEL_XLSX_CONTENT_TYPE.equals(contentType)) {
                workbook = new XSSFWorkbook(bufferedInputStream);
                XSSFSheet sheet = (XSSFSheet) workbook.getSheetAt(sheetIndex);
                sheet.setForceFormulaRecalculation(true);
                XSSFFormulaEvaluator evaluator = (XSSFFormulaEvaluator) workbook.getCreationHelper().createFormulaEvaluator();
                businessDataMapList = excelXSSFReader.readBusinessData(sheet, evaluator, businessTypeDataMap);
                workbook.close();
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
     *
     * @param businessDataMapList 业务数据
     * @return
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
     *
     * @param request
     * @param businessDataMapList 业务数据
     * @param repeatedData        业务数据
     * @param sheetIndex          工作表次序
     * @return
     * @throws IOException
     */
    private Attach writeBusinessData(ExportTemplate exportTemplate, Attach businessFile, HttpServletRequest request, HttpServletResponse response, List<Map<String, BusinessData>> businessDataMapList, Map<ColumnMeta, Map<Object, Long>> repeatedData, int sheetIndex) throws IOException {
        Attach attachMap = new Attach();

        InputStream inputStream = null;
        FileInputStream fileInputStream = null;
        BufferedInputStream bufferedInputStream = null;
        OutputStream outputStream = null;
        OutputStream responseOut = null;
        FileInputStream responseIn = null;
        Workbook workbook = null;
        SXSSFWorkbook sWorkbook = null;
        try {
            String contentType = null;
            String fileName = null;
            if (businessFile != null) {
                File file = new File(businessFile.getPath());
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
            String errorFileName = String.format("%s：%s%s%s", templateName, "错误提示 ", dateTimeFormat.format(new Date()), templateExt);
            String directory = UploadService.getSavePath(UploadService.ROOT_DIRECTORY, AttachmentSourceEnum.PLATFORM_ATTACH.getValue(), exportTemplate.getTenantCode(), exportTemplate.getAppId(), errorFileName, true);
            File errorFile = new File(directory);
            // 文件处理
            if (EXCEL_XLS_CONTENT_TYPE.equals(contentType)) {
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
            } else if (EXCEL_XLSX_CONTENT_TYPE.equals(contentType)) {
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
                sWorkbook = new SXSSFWorkbook((XSSFWorkbook) workbook, maxRow > ROW_ACCESS_WINDOW_SIZE ? ROW_ACCESS_WINDOW_SIZE : maxRow);
                // 写入文件
                outputStream = new BufferedOutputStream(new FileOutputStream(errorFile));
                sWorkbook.write(outputStream);
                outputStream.flush();
                sWorkbook.dispose();
                workbook.close();
            } else {
                throw new FileTypeNotSupportedException("Business Data, Excel Type: " + contentType);
            }
            // 保存文件信息
            BasicFileAttributes attributes = Files.readAttributes(errorFile.toPath(), BasicFileAttributes.class);
            Attach attach = new Attach();
            attach.setGenre(IMPORT_ERROR_FILE_GENRE);
            attach.setName(errorFileName);
            attach.setType(Files.probeContentType(errorFile.toPath()));
            attach.setSize(attributes.size());
            attach.setPath(directory);
            attachMap = attachService.createModel(attach);
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
            if (responseOut != null) {
                responseOut.close();
            }
            if (responseIn != null) {
                responseIn.close();
            }
            if (sWorkbook != null) {
                sWorkbook.close();
                sWorkbook.dispose();
            }
            if (workbook != null) {
                workbook.close();
            }
        }

        return attachMap;
    }

    /**
     * 将base64转为file
     *
     * @param currentUUID
     * @param template
     * @return
     */
    private File getTemplate(String currentUUID, String template) {
        File file = null;
        if (Strings.isNotBlank(template)) {
            if (template.length() > 64) {
                try {
                    Base64Info bi = JSON.parseObject(template, Base64Info.class);
                    if (bi != null && Strings.isNotBlank(bi.getName()) && Strings.isNotBlank(bi.getBase64())) {
                        // 解码Base64字符串为字节数组
                        byte[] decodedBytes = Base64.getDecoder().decode(bi.getBase64());
                        // 创建临时文件
                        String fileExt = bi.getName().substring(bi.getName().lastIndexOf("."));
                        File tempFile = File.createTempFile("temp_base64_import", fileExt);
                        tempFile.deleteOnExit();
                        // 将字节数组写入临时文件
                        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                            fos.write(decodedBytes);
                        }
                        // 将临时文件吸入file
                        file = tempFile;
                        logger.info(String.format("base64Name：%s；tempFilePath：%s", bi.getName(), file.getAbsolutePath()));
                    }
                } catch (Exception ex) {
                    logger.info(ex.getMessage(), ex);
                }
            } else {
                Attach attach = getFile(template);
                if (attach != null) {
                    file = new File(attach.getPath());
                    logger.info(String.format("AttachName：%s；tempFilePath：%s", attach.getName(), file.getAbsolutePath()));
                }
            }
        }

        return file;
    }

    /**
     * 获取文件
     *
     * @param attachId
     * @return
     */
    private Attach getFile(String attachId) {
        try {
            if (Strings.isNotBlank(attachId)) {
                Attach attach = attachService.getModel(attachId);
                File file = new File(attach.getPath());
                if (file.exists()) {
                    return attach;
                }
            }
        } catch (Exception ex) {
            logger.info(ex.getMessage(), ex);
        }

        return null;
    }

    /**
     * 模板数据解析
     *
     * @param jsonText
     * @return
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
     *
     * @param jsonText
     * @return
     */
    private Set<Map<Integer, BusinessTypeRuleData>> getBusinessTypeRuleData(String jsonText) {
        Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet = new LinkedHashSet<>();
        try {
            List<BusinessTypeRuleData> dataList = JSON.parseArray(jsonText, BusinessTypeRuleData.class);
            if (dataList != null && !dataList.isEmpty()) {
                dataList.sort(new Comparator<BusinessTypeRuleData>() {
                    @Override
                    public int compare(BusinessTypeRuleData o1, BusinessTypeRuleData o2) {
                        return o1.getOrder() - o2.getOrder();
                    }
                });
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
     *
     * @param jsonText
     * @return
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
