package cn.geelato.web.platform.m.excel.service;

import cn.geelato.web.platform.m.excel.entity.*;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import org.apache.logging.log4j.util.Strings;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.enums.EnableStatusEnum;
import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.field.ColumnMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.web.platform.exception.file.FileException;
import cn.geelato.web.platform.m.base.entity.Dict;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.web.platform.m.excel.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * @author diabl
 * @date 2023/10/27 9:53
 */
@Component
public class ExcelCommonUtils {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    public static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    public static final Pattern CELL_META_PATTERN = Pattern.compile("\\$\\{[\\\u4e00-\\\u9fa5,\\w,\\.\\(\\)\\（\\）]+\\}");
    public static final Pattern ROW_META_PATTERN = Pattern.compile("\\$\\{rowMeta\\.[\\w,\\.,\\=]+\\}");
    private static final int REDIS_TIME_OUT = 60;
    private static final int GGL_QUERY_TOTAL = 10000;
    private static final String REDIS_UNIQUE_KEY = "uniques";
    private static final Logger logger = LoggerFactory.getLogger(ExcelCommonUtils.class);
    private final FilterGroup filterGroup = new FilterGroup().addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(DeleteStatusEnum.NO.getCode()));
    private final MetaManager metaManager = MetaManager.singleInstance();
    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;
    @Autowired
    @Qualifier("secondaryDao")
    protected Dao dao2;
    @Autowired
    protected RuleService ruleService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 为空，抛出异常
     *
     * @param object
     * @param fileException
     * @param <T>
     * @throws T
     */
    public static <T extends FileException> void notNull(@Nullable Object object, T fileException) throws T {
        if (object == null) {
            throw fileException;
        }
    }

    /**
     * 字符串转数值
     *
     * @param value
     * @return
     */
    public static Object stringToNumber(String value) {
        Object cellValue = null;
        if (Strings.isNotBlank(value) && value.matches("-?\\d+(\\.\\d+)?")) {
            if (value.indexOf(".") == -1) {
                cellValue = Long.parseLong(value);
            } else {
                cellValue = new BigDecimal(value).doubleValue();
            }
        }
        return cellValue;
    }

    /**
     * 合并唯一约束的交集
     *
     * @param cellMetaList
     * @param valueMap
     * @param valueList
     * @return
     */
    public static List<List<Integer>> getMergeUniqueScope(List<CellMeta> cellMetaList, Map valueMap, List<Map> valueList) {
        List<List<List<Integer>>> limitSetMap = new LinkedList<>();
        int uniqueNum = 0; // 唯一约束的数据量
        for (CellMeta cellMeta : cellMetaList) {
            if (cellMeta.getPlaceholderMeta().isIsMerge() && cellMeta.getPlaceholderMeta().isIsUnique()) {
                uniqueNum += 1;
                // 获取数据相同的行
                List<List<Integer>> integerSet = getIntegerSet(cellMeta, valueMap, valueList);
                if (integerSet != null && integerSet.size() > 0) {
                    limitSetMap.add(integerSet);
                }
            }
        }
        // 有约束为空，无约束为null
        if (uniqueNum > 0) {
            List<List<Integer>> result = new LinkedList<>();
            if (uniqueNum == limitSetMap.size()) {
                result = limitSetMap.get(0);
                for (int i = 1; i < limitSetMap.size(); i++) {
                    result = listRetain(result, limitSetMap.get(i));
                }
            }
            return result;
        } else {
            return null;
        }
    }

    public static List<List<Integer>> listRetain(List<List<Integer>> integerSet0, List<List<Integer>> integerSet1) {
        List<List<Integer>> result = new LinkedList<>();
        for (List<Integer> list0 : integerSet0) {
            for (List<Integer> list1 : integerSet1) {
                List<Integer> intersection = list0.stream().filter(list1::contains).collect(Collectors.toList());
                if (intersection != null && intersection.size() > 0) {
                    result.add(intersection);
                }
            }
        }

        return result;
    }

    /**
     * 获取数据相同的行
     *
     * @param cellMeta
     * @param valueMap
     * @param valueList
     * @return
     */
    public static List<List<Integer>> getIntegerSet(CellMeta cellMeta, Map valueMap, List<Map> valueList) {
        List<List<Integer>> integerSet = new LinkedList<>();
        if (valueList != null) {
            // 列表数据
            Object[] rowValues = new Object[valueList.size()];
            for (int i = 0; i < valueList.size(); i++) {
                rowValues[i] = getCellValue(cellMeta.getPlaceholderMeta(), valueMap, valueList.get(i));
            }
            // 比对
            int startIndex = 0;
            for (int i = 1; i <= rowValues.length; i++) {
                // 检查是否到达了数组的末尾，或者当前元素与下一个元素不同
                if (i == rowValues.length || !rowValues[i].equals(rowValues[i - 1])) {
                    // 结束列大于开始列（注意结束索引是i-1，因为当元素不同时，我们实际上在检查前一个元素）
                    if (i - 1 > startIndex) {
                        List<Integer> integerList = new LinkedList<>();
                        for (int j = startIndex; j <= i - 1; j++) {
                            integerList.add(j);
                        }
                        integerSet.add(integerList);
                    }
                    startIndex = i; // 更新起始索引为当前索引（如果未到达末尾）
                }
            }
        }
        logger.info(cellMeta.getPlaceholderMeta().getPlaceholder() + ": " + JSON.toJSONString(integerSet));
        return integerSet;
    }

    /**
     * 获取单元格值
     *
     * @param meta
     * @param valueMap
     * @param listValueMap
     * @return
     */
    public static Object getCellValue(PlaceholderMeta meta, Map valueMap, Map listValueMap) {
        Object value = "";
        // 不是列表，且是变更
        if (meta.isValueComputeModeVar()) {
            if (meta.isIsList()) {
                Object v = listValueMap.get(meta.getVar());
                value = getCellValueByValueType(meta, v);
            } else {
                if (meta.getVar() != null && meta.getVar().trim().length() > 0) {
                    Object v = valueMap.get(meta.getVar());
                    value = getCellValueByValueType(meta, v);
                }
            }
        } else if (meta.isValueComputeModeConst()) {
            value = getCellValueByValueType(meta, meta.getConstValue());
        } else if (meta.isValueComputeModeExpression()) {
            Object v = JsProvider.executeExpression(meta.getExpression(), meta.isIsList() ? listValueMap : valueMap);
            value = getCellValueByValueType(meta, v);
        }
        return value;
    }

    /**
     * 根据类型获取具体值
     *
     * @param meta
     * @param value
     * @return
     */
    private static Object getCellValueByValueType(PlaceholderMeta meta, Object value) {
        if (value != null) {
            if (meta.isValueTypeNumber()) {
                if (value.toString().indexOf(".") == -1) {
                    return Long.parseLong(value.toString());
                } else {
                    return new BigDecimal(value.toString()).doubleValue();
                }
            } else if (meta.isValueTypeDate()) {
                // value 应为时间戳
                return DATE_FORMAT.format(value);
            } else if (meta.isValueTypeDateTime()) {
                // value 应为时间戳
                return DATE_TIME_FORMAT.format(value);
            } else {
                return value.toString();
            }
        } else {
            if (meta.isValueTypeNumber()) {
                return 0;
            } else {
                return "";
            }
        }
    }

    /**
     * 将连续的整数构建一个集合,这个集合的起止节点
     *
     * @param numbers
     * @return
     */
    public static List<Integer[]> findScopes(Set<Integer> numbers) {
        List<Integer[]> list = new ArrayList<>();
        if (numbers != null && numbers.size() > 0) {
            List<List<Integer>> ranges = findRanges(new LinkedList<>(numbers));
            // 取范围
            for (List<Integer> range : ranges) {
                if (range.get(1) > range.get(0)) {
                    list.add(new Integer[]{range.get(0), range.get(range.size() - 1)});
                }
            }
        }

        return list;
    }

    /**
     * 将连续的整数构建一个集合
     *
     * @param numbers
     * @return
     */
    public static List<List<Integer>> findRanges(List<Integer> numbers) {
        // 先对数字进行排序
        // Collections.sort(numbers);

        List<List<Integer>> ranges = new LinkedList<>();
        List<Integer> currentRange = new LinkedList<>();
        currentRange.add(numbers.get(0)); // 添加第一个数字到当前范围
        for (int i = 1; i < numbers.size(); i++) {
            int currentNumber = numbers.get(i);
            int lastNumberInRange = currentRange.get(currentRange.size() - 1);

            // 如果当前数字与前一个数字相差为1，则属于同一范围
            if (currentNumber - lastNumberInRange == 1) {
                currentRange.add(currentNumber); // 添加当前数字到当前范围
            } else {
                // 否则，当前范围结束，将当前范围添加到结果集，并开始新的范围
                ranges.add(currentRange);
                currentRange = new LinkedList<>(); // 创建一个新的范围
                currentRange.add(currentNumber); // 添加当前数字到新的范围
            }
        }
        // 不要忘记添加最后一个范围
        ranges.add(currentRange);
        // 如果每个范围只有一个数字，则转换为[num, num]的形式
        for (List<Integer> range : ranges) {
            if (range.size() == 1) {
                range.add(range.get(0)); // 添加相同的数字作为范围的结束
            }
        }

        return ranges;
    }

    /**
     * 获取表格默认字段
     *
     * @return
     */
    public List<String> getDefaultColumns() {
        List<String> columnNames = new ArrayList<>();
        List<ColumnMeta> columnMetaList = metaManager.getDefaultColumn();
        if (columnMetaList != null && columnMetaList.size() > 0) {
            for (ColumnMeta columnMeta : columnMetaList) {
                if (!columnNames.contains(columnMeta.getName())) {
                    columnNames.add(columnMeta.getName());
                }
            }
        }

        return columnNames;
    }

    /**
     * 解析业务数据类型，规则
     *
     * @param rules
     * @return
     */
    public Set<BusinessTypeRuleData> readBusinessTypeRuleData(String rules) {
        Set<BusinessTypeRuleData> typeRuleDataSet = new LinkedHashSet<>();
        if (Strings.isNotBlank(rules)) {
            List<BusinessTypeRuleData> typeRuleDataList = com.alibaba.fastjson.JSON.parseArray(rules, BusinessTypeRuleData.class);
            if (typeRuleDataList != null && typeRuleDataList.size() > 0) {
                typeRuleDataList.sort(new Comparator<BusinessTypeRuleData>() {
                    @Override
                    public int compare(BusinessTypeRuleData o1, BusinessTypeRuleData o2) {
                        return o1.getOrder() - o2.getOrder();
                    }
                });
                for (BusinessTypeRuleData ruleData : typeRuleDataList) {
                    typeRuleDataSet.add(ruleData);
                }
            }
        }

        return typeRuleDataSet;
    }

    /**
     * 导入数据，多值数据处理
     *
     * @param businessDataMapList
     * @return
     */
    public List<Map<String, BusinessData>> handleBusinessDataMultiScene(List<Map<String, BusinessData>> businessDataMapList) {
        List<Map<String, BusinessData>> handleDataMapList = new ArrayList<>();
        Set<Map<String, Object>> multiLoggers = new LinkedHashSet<>();
        if (businessDataMapList != null && businessDataMapList.size() > 0) {
            for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
                // 分类
                Map<String, BusinessData> singleData = new HashMap<>();
                Map<String, BusinessData> multiData = new LinkedHashMap<>();
                Map<String, BusinessData> symData = new HashMap<>();
                int maxLength = 0;
                for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                    BusinessData businessData = businessDataEntry.getValue();
                    BusinessTypeData typeData = businessData.getBusinessTypeData();
                    boolean isMulti = false;
                    if (typeData.isMulti()) {
                        if (businessData.getValue() != null) {
                            try {
                                String[] multiValue = String.valueOf(businessData.getValue()).split(typeData.getMultiSeparator());
                                Map<String, Object> multiLogger = new LinkedHashMap<>();
                                multiLogger.put("index", String.format("y.%s,x.%s", businessData.getYIndex(), businessData.getXIndex()));
                                multiLogger.put("separator", typeData.getMultiSeparator());
                                multiLogger.put("scene", typeData.getMultiScene());
                                multiLogger.put("cellValue", businessData.getValue());
                                multiLogger.put("formatValue", multiValue);
                                multiLoggers.add(multiLogger);
                                if (multiValue != null && multiValue.length > 0) {
                                    for (int i = 0; i < multiValue.length; i++) {
                                        multiValue[i] = Strings.isNotBlank(multiValue[i]) ? multiValue[i].trim() : "";
                                    }
                                    businessData.setMultiValue(multiValue);
                                    if (typeData.isSceneTypeMulti()) {
                                        isMulti = true;
                                        multiData.put(businessDataEntry.getKey(), businessData);
                                    } else if (typeData.isSceneTypeSym()) {
                                        isMulti = true;
                                        symData.put(businessDataEntry.getKey(), businessData);
                                        maxLength = maxLength > multiValue.length ? maxLength : multiValue.length;
                                    }
                                }
                            } catch (Exception ex) {
                                logger.error(ex.getMessage(), ex);
                            }
                        }
                    }
                    if (!isMulti) {
                        singleData.put(businessDataEntry.getKey(), businessData);
                    }
                }
                // 对乘值处理
                List<Map<String, BusinessData>> multiMapList = cartesianProduct(multiData);
                // 对称值处理
                List<Map<String, BusinessData>> symMapList = new ArrayList<>();
                if (!symData.isEmpty()) {
                    for (int i = 0; i < maxLength; i++) {
                        Map<String, BusinessData> symMap = new HashMap<>();
                        for (Map.Entry<String, BusinessData> businessDataEntry : symData.entrySet()) {
                            BusinessData businessData = businessDataEntry.getValue();
                            BusinessData data = new BusinessData();
                            data.setXIndex(businessData.getXIndex());
                            data.setYIndex(businessData.getYIndex());
                            String[] multiValue = businessData.getMultiValue();
                            data.setValue(multiValue[i < multiValue.length ? i : multiValue.length - 1]);
                            data.setPrimevalValue(data.getValue());
                            data.setBusinessTypeData(businessData.getBusinessTypeData());
                            data.setErrorMsgs(businessData.getErrorMsg());
                            symMap.put(businessDataEntry.getKey(), data);
                        }
                        symMapList.add(symMap);
                    }
                }

                List<Map<String, BusinessData>> mergeData = mergeBusinessData(singleData, multiMapList, symMapList);
                handleDataMapList.addAll(mergeData);
            }
        }
        // logger.info(JSON.toJSONString(multiLoggers));

        return handleDataMapList;
    }

    /**
     * 导入数据，多值数据处理
     *
     * @param singleData   单值数据
     * @param multiMapList 多值数据，相乘
     * @param symMapList   多值数据，对称
     * @return
     */
    private List<Map<String, BusinessData>> mergeBusinessData(Map<String, BusinessData> singleData, List<Map<String, BusinessData>> multiMapList, List<Map<String, BusinessData>> symMapList) {
        List<Map<String, BusinessData>> mergeData = new ArrayList<>();
        if (multiMapList != null && multiMapList.size() > 0) {
            if (symMapList != null && symMapList.size() > 0) {
                for (Map<String, BusinessData> multiMap : multiMapList) {
                    for (Map<String, BusinessData> symMap : symMapList) {
                        Map<String, BusinessData> map = new HashMap<>();
                        map.putAll(multiMap);
                        map.putAll(symMap);
                        map.putAll(singleData);
                        mergeData.add(map);
                    }
                }
            } else {
                for (Map<String, BusinessData> map : multiMapList) {
                    map.putAll(singleData);
                    mergeData.add(map);
                }
            }
        } else {
            if (symMapList != null && symMapList.size() > 0) {
                for (Map<String, BusinessData> map : symMapList) {
                    map.putAll(singleData);
                    mergeData.add(map);
                }
            } else {
                mergeData.add(singleData);
            }
        }

        return mergeData;
    }

    /**
     * 导入数据，多值数据处理
     *
     * @param multiData 多值数据，相乘
     * @return
     */
    private List<Map<String, BusinessData>> cartesianProduct(Map<String, BusinessData> multiData) {
        List<Map<String, BusinessData>> mapList = new ArrayList<>();
        Set<String[]> valueSet = new LinkedHashSet<>();
        Set<String> keySet = new LinkedHashSet<>();
        for (Map.Entry<String, BusinessData> map : multiData.entrySet()) {
            keySet.add(map.getKey());
            valueSet.add(map.getValue().getMultiValue());
        }
        Set<String[]> result = cartesianProductHelper(valueSet.toArray(new String[][]{}));
        for (String[] arr : result) {
            Map<String, BusinessData> map = new HashMap<>();
            Iterator<String> iterator = keySet.iterator();
            int count = 0;
            while (iterator.hasNext()) {
                String key = iterator.next();
                BusinessData businessData = multiData.get(key);
                BusinessData data = new BusinessData();
                data.setXIndex(businessData.getXIndex());
                data.setYIndex(businessData.getYIndex());
                data.setValue(arr[count++]);
                data.setPrimevalValue(businessData.getPrimevalValue());
                data.setTransitionValues(businessData.getTransitionValue());
                data.setTransitionValue(data.getValue());
                data.setBusinessTypeData(businessData.getBusinessTypeData());
                data.setErrorMsgs(businessData.getErrorMsg());
                map.put(key, data);
            }
            mapList.add(map);
        }

        return mapList;
    }

    /**
     * 递归
     *
     * @param arrays
     * @return
     */
    private Set<String[]> cartesianProductHelper(String[][] arrays) {
        Set<String[]> result = new LinkedHashSet<>();
        cartesianProductHelper(arrays, 0, new String[arrays.length], result);
        return result;
    }

    /**
     * 递归，计算组合
     *
     * @param arrays
     * @param index
     * @param current
     * @param result
     */
    private void cartesianProductHelper(String[][] arrays, int index, String[] current, Set<String[]> result) {
        if (index == arrays.length) {
            result.add(current.clone());
            return;
        }

        for (String element : arrays[index]) {
            current[index] = element;
            cartesianProductHelper(arrays, index + 1, current, result);
        }
    }

    /**
     * 数据处理
     *
     * @param currentUUID
     * @param businessDataMapList
     * @param priorityMulti       是否优先于多值处理
     * @return
     */
    public List<Map<String, BusinessData>> handleBusinessDataRule(String currentUUID, List<Map<String, BusinessData>> businessDataMapList, boolean priorityMulti) {
        if (businessDataMapList != null && businessDataMapList.size() > 0) {
            // 设置缓存
            List<String> cacheKeys = setCache(currentUUID, businessDataMapList, priorityMulti);
            // 数据处理
            for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
                // 一行业务数据，键值对
                Map<String, Object> valueMap = new HashMap<>();
                for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                    valueMap.put(businessDataEntry.getKey(), businessDataEntry.getValue().getValue());
                }
                // 一行数据，每个列
                for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                    BusinessData businessData = businessDataEntry.getValue();
                    if (businessData.getValue() == null || Strings.isBlank(String.valueOf(businessData.getValue()))) {
                        continue;
                    }
                    BusinessTypeData typeData = businessData.getBusinessTypeData();
                    Set<BusinessTypeRuleData> typeRuleDataSet = typeData.getTypeRuleData();
                    if (typeRuleDataSet != null && typeRuleDataSet.size() > 0) {
                        for (BusinessTypeRuleData ruleData : typeRuleDataSet) {
                            // 执行优先多值 + 优先多值的规则
                            if (priorityMulti != ruleData.isPriority()) {
                                continue;
                            }
                            try {
                                Object newValue = null;
                                String oldValue = String.valueOf(businessData.getValue());
                                if (ruleData.isRuleTypeDeletes()) {
                                    if (Strings.isNotBlank(ruleData.getRule())) {
                                        newValue = oldValue.replaceAll(ruleData.getRule(), "");
                                    } else {
                                        businessData.setErrorMsg("Rule resolution failure。[Deletes] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeReplace()) {
                                    if (Strings.isNotBlank(ruleData.getRule()) && Strings.isNotBlank(ruleData.getGoal())) {
                                        newValue = oldValue.replaceAll(ruleData.getRule(), ruleData.getGoal());
                                    } else {
                                        businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule or Goal is empty！");
                                    }
                                } else if (ruleData.isRuleTypeUpperCase()) {
                                    newValue = oldValue.toUpperCase(Locale.ENGLISH);
                                } else if (ruleData.isRuleTypeLowerCase()) {
                                    newValue = oldValue.toLowerCase(Locale.ENGLISH);
                                } else if (ruleData.isRuleTypeTrim()) {
                                    newValue = oldValue.trim();
                                } else if (ruleData.isRuleTypeExpression()) {
                                    if (Strings.isNotBlank(ruleData.getRule())) {
                                        newValue = JsProvider.executeExpression(ruleData.getRule(), valueMap);
                                    } else {
                                        businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeCheckBox()) {
                                    if (Strings.isNotBlank(ruleData.getRule())) {
                                        Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                                        if (redisValues != null && redisValues.size() > 0) {
                                            String[] oValues = oldValue.split(",");
                                            if (oValues != null && oValues.length > 0) {
                                                Set<String> nValues = new LinkedHashSet<>();
                                                for (String oValue : oValues) {
                                                    String nValue = redisValues.get(oValue);
                                                    if (Strings.isNotBlank(nValue)) {
                                                        nValues.add(nValue);
                                                    }
                                                }
                                                newValue = String.join(",", nValues);
                                            }
                                        }
                                    } else {
                                        businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeDictionary()) {
                                    if (Strings.isNotBlank(ruleData.getRule())) {
                                        Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                                        if (redisValues != null && redisValues.size() > 0) {
                                            newValue = redisValues.get(oldValue);
                                        }
                                    } else {
                                        businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeQueryGoal()) {
                                    String tableName = ruleData.getQueryRuleTable();
                                    List<String> columnNames = ruleData.getQueryRuleColumn();
                                    if (Strings.isNotBlank(tableName) && columnNames != null && columnNames.size() > 0 && Strings.isNotBlank(ruleData.getGoal())) {
                                        Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal()));
                                        if (redisValues != null && redisValues.size() > 0) {
                                            newValue = redisValues.get(oldValue);
                                        }
                                        if (newValue == null) {
                                            newValue = businessData.getValue();
                                        }
                                    } else {
                                        businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                                    }
                                } else if (ruleData.isRuleTypeQueryRule()) {
                                    String tableName = ruleData.getQueryRuleTable();
                                    List<String> columnNames = ruleData.getQueryRuleColumn();
                                    if (Strings.isNotBlank(tableName) && columnNames != null && columnNames.size() > 0 && Strings.isNotBlank(ruleData.getGoal())) {
                                        Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                                        if (redisValues != null && redisValues.size() > 0) {
                                            newValue = redisValues.get(oldValue);
                                        }
                                        if (newValue == null) {
                                            newValue = businessData.getValue();
                                        }
                                    } else {
                                        businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                                    }
                                } else {
                                    newValue = businessData.getValue();
                                }
                                if (newValue != null) {
                                    logger.info(String.format("数据清洗[Y.%s,X.%s], [%s], %s => %s", businessData.getYIndex(), businessData.getXIndex(), ruleData.getType(), businessData.getValue(), newValue));
                                }
                                businessData.setValue(newValue);
                            } catch (Exception ex) {
                                businessData.setErrorMsg("Rule resolution failure。" + JSON.toJSONString(ruleData));
                            }
                        }
                    }
                }
            }
            // 清理缓存
            redisTemplate.delete(cacheKeys);
        }

        return businessDataMapList;
    }

    private List<String> setCache(String currentUUID, List<Map<String, BusinessData>> businessDataMapList, boolean priorityMulti) {
        List<String> cacheList = new ArrayList<>();
        // 类型
        Map<String, BusinessTypeRuleData> ruleDataDict = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataGoal = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataRule = new HashMap<>();
        // 数据解析
        if (businessDataMapList != null && businessDataMapList.size() > 0) {
            for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
                for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                    BusinessData businessData = businessDataEntry.getValue();
                    BusinessTypeData typeData = businessData.getBusinessTypeData();
                    Set<BusinessTypeRuleData> typeRuleDataSet = typeData.getTypeRuleData();
                    if (typeRuleDataSet != null && typeRuleDataSet.size() > 0) {
                        for (BusinessTypeRuleData ruleData : typeRuleDataSet) {
                            // 执行优先多值 + 优先多值的规则
                            if (priorityMulti != ruleData.isPriority()) {
                                continue;
                            }
                            if (ruleData.isRuleTypeDictionary() || ruleData.isRuleTypeCheckBox()) {
                                if (Strings.isNotBlank(ruleData.getRule())) {
                                    String key = String.format("%s:%s", currentUUID, ruleData.getRule());
                                    if (!ruleDataDict.containsKey(key)) {
                                        ruleDataDict.put(key, ruleData);
                                    }
                                }
                            } else if (ruleData.isRuleTypeQueryGoal()) {
                                String tableName = ruleData.getQueryRuleTable();
                                List<String> columnNames = ruleData.getQueryRuleColumn();
                                if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && columnNames.size() > 0) {
                                    String key = String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal());
                                    if (!ruleDataGoal.containsKey(key)) {
                                        ruleDataGoal.put(key, ruleData);
                                    }
                                }
                            } else if (ruleData.isRuleTypeQueryRule()) {
                                String tableName = ruleData.getQueryRuleTable();
                                List<String> columnNames = ruleData.getQueryRuleColumn();
                                if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && columnNames.size() > 0) {
                                    String key = String.format("%s:%s", currentUUID, ruleData.getRule());
                                    if (!ruleDataRule.containsKey(key)) {
                                        ruleDataRule.put(key, ruleData);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // 字典查询
        List<String> dictKeys = setDictRuleRedis(currentUUID, ruleDataDict);
        cacheList.addAll(dictKeys);
        // 目标字段查询
        List<String> goalRedis = setQueryRuleRedis(ruleDataGoal);
        cacheList.addAll(goalRedis);
        // 规则字段查询
        List<String> ruleRedis = setQueryRuleRedis(ruleDataRule);
        cacheList.addAll(ruleRedis);

        return cacheList;
    }

    /**
     * 数据字典缓存
     *
     * @param currentUUID
     * @param ruleDataMap
     * @return
     */
    private List<String> setDictRuleRedis(String currentUUID, Map<String, BusinessTypeRuleData> ruleDataMap) {
        List<String> dictKeys = new ArrayList<>();
        if (ruleDataMap != null && ruleDataMap.size() > 0) {
            // 所有字典编码
            List<String> dictCodes = new ArrayList<>();
            for (Map.Entry<String, BusinessTypeRuleData> ruleDataEntry : ruleDataMap.entrySet()) {
                dictCodes.add(ruleDataEntry.getValue().getRule());
            }

            List<Dict> dictList = new ArrayList<>();
            List<DictItem> dictItemList = new ArrayList<>();
            // 查询
            FilterGroup filter = new FilterGroup();
            filter.addFilter("dictCode", FilterGroup.Operator.in, String.join(",", dictCodes));
            dictList = dao.queryList(Dict.class, filter, null);
            if (dictList != null && dictList.size() > 0) {
                List<String> dictIds = new ArrayList<>();
                for (Dict dict : dictList) {
                    dictIds.add(dict.getId());
                }
                FilterGroup filter1 = new FilterGroup();
                filter1.addFilter("dictId", FilterGroup.Operator.in, String.join(",", dictIds));
                dictItemList = dao.queryList(DictItem.class, filter1, null);
                // 存入缓存
                for (Dict dict : dictList) {
                    String dictKey = String.format("%s:%s", currentUUID, dict.getDictCode());
                    Map<String, String> dictItems = new HashMap<>();
                    if (dictItemList != null && dictItemList.size() > 0) {
                        for (DictItem dictItem : dictItemList) {
                            if (dict.getId().equalsIgnoreCase(dictItem.getDictId())) {
                                dictItems.put(dictItem.getItemName(), dictItem.getItemCode());
                            }
                        }
                        logger.info(dictKey + " - " + dictItems.size());
                        redisTemplate.opsForValue().set(dictKey, dictItems, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        dictKeys.add(dictKey);
                    }
                }
            }
        }

        return dictKeys;
    }

    /**
     * 主键查询，缓存
     *
     * @param ruleDataMap
     * @return
     */
    private List<String> setQueryRuleRedis(Map<String, BusinessTypeRuleData> ruleDataMap) {
        List<String> primaryKeys = new ArrayList<>();
        String gglFormat = "{\"%s\": {\"@fs\": \"%s\"}}";
        try {
            if (ruleDataMap != null && ruleDataMap.size() > 0) {
                for (Map.Entry<String, BusinessTypeRuleData> ruleDataEntry : ruleDataMap.entrySet()) {
                    String key = ruleDataEntry.getKey();
                    BusinessTypeRuleData ruleData = ruleDataEntry.getValue();
                    if (ruleData != null) {
                        Set<String> columnNames = new LinkedHashSet<>();
                        columnNames.add(ruleData.getGoal());
                        columnNames.addAll(ruleData.getQueryRuleColumn());
                        String ggl = String.format(gglFormat, ruleData.getQueryRuleTable(), String.join(",", columnNames));
                        logger.info(key + " - " + ggl);
                        ApiPagedResult page = ruleService.queryForMapList(ggl, false);
                        Map<String, Object> redisValue = pagedResultToMap(page, ruleData.getGoal(), ruleData.getQueryRuleColumn());
                        logger.info(String.format("%s - %s => %s", key, page.getTotal(), (redisValue != null ? redisValue.size() : 0)));
                        redisTemplate.opsForValue().set(key, redisValue, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        primaryKeys.add(key);
                    }
                }
            }
        } catch (Exception ex) {
            logger.info(ex.getMessage(), ex);
        }

        return primaryKeys;
    }

    /**
     * 设置 缓存，数据字典、主键
     *
     * @param currentUUID 当前主键
     * @param tableMeta   元数据
     * @param data        业务数据
     * @return
     */
    public List<String> setCache(String currentUUID, Map<String, List<BusinessMeta>> tableMeta, List<Map<String, BusinessData>> data) {
        List<String> cacheList = new ArrayList<>();
        // 元数据
        Map<String, ConditionMeta> dictMetas = new HashMap<>();
        Map<String, ConditionMeta> primaryMetas = new HashMap<>();
        for (Map.Entry<String, List<BusinessMeta>> metaMap : tableMeta.entrySet()) {
            if (metaMap.getValue() != null && metaMap.getValue().size() > 0) {
                for (BusinessMeta meta : metaMap.getValue()) {
                    ConditionMeta conditionMeta = null;
                    if ((meta.isEvaluationTypeDictionary() || meta.isEvaluationTypeCheckBox()) && Strings.isNotBlank(meta.getDictCode())) {
                        conditionMeta = new ConditionMeta();
                        conditionMeta.setVariable(meta.getVariableValue());
                        conditionMeta.setDictCode(meta.getDictCode());
                    } else if (meta.isEvaluationTypePrimaryKey() && Strings.isNotBlank(meta.getPrimaryValue())) {
                        conditionMeta = new ConditionMeta();
                        conditionMeta.setVariable(meta.getVariableValue());
                        conditionMeta.setTableName(meta.getPrimaryKeyTable());
                        conditionMeta.setColumnNames(meta.getPrimaryKeyColumns());
                        conditionMeta.setGoalName(meta.getPrimaryKeyGoal());
                    }
                    if (conditionMeta != null) {
                        List<String> values = new ArrayList<>();
                        for (Map<String, BusinessData> map : data) {
                            BusinessData businessData = map.get(meta.getVariableValue());
                            if (businessData != null) {
                                values.add(String.valueOf(businessData.getValue()));
                            }
                        }
                        conditionMeta.setValues(values);
                        if (meta.isEvaluationTypeDictionary() || meta.isEvaluationTypeCheckBox()) {
                            String key = String.format("%s:%s", currentUUID, meta.getDictCode());
                            if (!dictMetas.containsKey(key)) {
                                dictMetas.put(key, conditionMeta);
                            }
                        } else if (meta.isEvaluationTypePrimaryKey()) {
                            String key = String.format("%s:%s", currentUUID, meta.getPrimaryValue());
                            if (!primaryMetas.containsKey(key)) {
                                primaryMetas.put(key, conditionMeta);
                            }
                        }
                    }
                }
            }
        }
        dao.setDefaultFilter(true, filterGroup);
        // 数据字典
        List<String> dictKeys = setDictRedis(currentUUID, dictMetas);
        cacheList.addAll(dictKeys);
        // 主键
        List<String> primaryKeys = setPrimaryRedis(primaryMetas);
        cacheList.addAll(primaryKeys);

        return cacheList;
    }

    /**
     * 数据字典缓存
     *
     * @param currentUUID
     * @param dictMetas
     * @return
     */
    private List<String> setDictRedis(String currentUUID, Map<String, ConditionMeta> dictMetas) {
        List<String> dictKeys = new ArrayList<>();
        if (dictMetas != null && dictMetas.size() > 0) {
            Set<String> dictCodes = new LinkedHashSet<>();
            Set<String> dictItemNames = new LinkedHashSet<>();
            for (Map.Entry<String, ConditionMeta> metaEntry : dictMetas.entrySet()) {
                if (metaEntry.getValue() != null) {
                    dictCodes.add(metaEntry.getValue().getDictCode());
                    dictItemNames.addAll(metaEntry.getValue().getValues());
                }
            }

            List<Dict> dictList = new ArrayList<>();
            List<DictItem> dictItemList = new ArrayList<>();
            // 查询
            FilterGroup filter = new FilterGroup();
            filter.addFilter("dictCode", FilterGroup.Operator.in, String.join(",", dictCodes));
            dictList = dao.queryList(Dict.class, filter, null);
            if (dictList != null && dictList.size() > 0) {
                List<String> dictIds = new ArrayList<>();
                for (Dict dict : dictList) {
                    dictIds.add(dict.getId());
                }
                FilterGroup filter1 = new FilterGroup();
                filter1.addFilter("dictId", FilterGroup.Operator.in, String.join(",", dictIds));
                // filter1.addFilter("itemName", FilterGroup.Operator.in, String.join(",", dictItemNames));
                dictItemList = dao.queryList(DictItem.class, filter1, null);
                // 存入缓存
                for (Dict dict : dictList) {
                    String dictKey = String.format("%s:%s", currentUUID, dict.getDictCode());
                    Map<String, String> dictItems = new HashMap<>();
                    if (dictItemList != null && dictItemList.size() > 0) {
                        for (DictItem dictItem : dictItemList) {
                            if (dict.getId().equalsIgnoreCase(dictItem.getDictId())) {
                                dictItems.put(dictItem.getItemName(), dictItem.getItemCode());
                            }
                        }
                        logger.info(dictKey + " - " + dictItems.size());
                        redisTemplate.opsForValue().set(dictKey, dictItems, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        dictKeys.add(dictKey);
                    }
                }
            }
        }

        return dictKeys;
    }

    /**
     * 主键查询，缓存
     *
     * @param primaryMetas
     * @return
     */
    private List<String> setPrimaryRedis(Map<String, ConditionMeta> primaryMetas) {
        List<String> primaryKeys = new ArrayList<>();
        String gglFormat = "{\"%s\": {\"@fs\": \"%s\"}}";
        try {
            if (primaryMetas != null && primaryMetas.size() > 0) {
                for (Map.Entry<String, ConditionMeta> metaEntry : primaryMetas.entrySet()) {
                    String key = metaEntry.getKey();
                    ConditionMeta meta = metaEntry.getValue();
                    if (meta != null) {
                        Set<String> columnNames = new LinkedHashSet<>();
                        columnNames.add(meta.getGoalName());
                        columnNames.addAll(meta.getColumnNames());
                        String ggl = String.format(gglFormat, meta.getTableName(), String.join(",", columnNames));
                        logger.info(key + " - " + ggl);
                        ApiPagedResult page = ruleService.queryForMapList(ggl, false);
                        logger.info(key + " - " + page.getTotal());
                        Map<String, Object> redisValue = pagedResultToMap(page, meta.getGoalName(), meta.getColumnNames());
                        logger.info(String.format("%s - %s => %s", key, page.getTotal(), (redisValue != null ? redisValue.size() : 0)));
                        redisTemplate.opsForValue().set(key, redisValue, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        primaryKeys.add(key);
                    }
                }
            }
        } catch (Exception ex) {
            logger.info(ex.getMessage(), ex);
        }

        return primaryKeys;
    }

    /**
     * 值转化
     *
     * @param page        查询结果
     * @param goalName    目标字段
     * @param columnNames 查询字段
     * @return
     */
    private Map<String, Object> pagedResultToMap(ApiPagedResult page, String goalName, List<String> columnNames) {
        Map<String, Object> redisMap = new HashMap<>();
        if (page != null && page.getData() != null && page.getTotal() > 0) {
            List<Map<String, Object>> mapList = (List<Map<String, Object>>) page.getData();
            for (Map<String, Object> map : mapList) {
                Object goalValue = map.get(goalName);
                if (goalValue != null) {
                    for (String columnName : columnNames) {
                        String value = String.valueOf(map.get(columnName));
                        if (Strings.isNotBlank(value) && !redisMap.containsKey(value)) {
                            redisMap.put(value, goalValue);
                        }
                    }
                }
            }
        }
        return redisMap;
    }

    public Map<String, ColumnMeta> getNullableColumns(Collection<FieldMeta> fieldMetas, List<String> columnNames) {
        Map<String, ColumnMeta> uniqueColumns = new HashMap<>();
        if (fieldMetas != null && fieldMetas.size() > 0) {
            for (FieldMeta fieldMeta : fieldMetas) {
                ColumnMeta meta = fieldMeta.getColumn();
                if (meta != null && Strings.isNotBlank(meta.getFieldName()) && meta.getEnableStatus() == EnableStatusEnum.ENABLED.getCode() && meta.getDelStatus() == DeleteStatusEnum.NO.getCode()) {
                    if (!uniqueColumns.containsKey(meta.getFieldName()) && !columnNames.contains(meta.getName()) && !meta.isNullable()) {
                        uniqueColumns.put(meta.getFieldName(), meta);
                    }
                }
            }
        }

        return uniqueColumns;
    }

    public Map<String, ColumnMeta> getUniqueColumns(Collection<FieldMeta> fieldMetas, List<String> columnNames) {
        Map<String, ColumnMeta> uniqueColumns = new HashMap<>();
        if (fieldMetas != null && fieldMetas.size() > 0) {
            for (FieldMeta fieldMeta : fieldMetas) {
                ColumnMeta meta = fieldMeta.getColumn();
                if (meta != null && Strings.isNotBlank(meta.getFieldName()) && meta.getEnableStatus() == EnableStatusEnum.ENABLED.getCode() && meta.getDelStatus() == DeleteStatusEnum.NO.getCode()) {
                    if (!uniqueColumns.containsKey(meta.getFieldName()) && !columnNames.contains(meta.getName()) && meta.isUniqued()) {
                        uniqueColumns.put(meta.getFieldName(), meta);
                    }
                }
            }
        }

        return uniqueColumns;
    }

    /**
     * 为一只
     *
     * @param currentUUID
     * @param tableName
     * @param uniqueColumns
     * @return
     */
    public List<String> setUniqueRedis(String currentUUID, String tableName, Set<String> uniqueColumns) {
        List<String> uniqueKeys = new ArrayList<>();
        String gglFormat = "{\"%s\": {\"@fs\": \"%s\"}}";
        String key = String.format("%s:%s:%s", currentUUID, tableName, REDIS_UNIQUE_KEY);
        try {
            if (Strings.isNotBlank(tableName) && uniqueColumns.size() > 0) {
                String ggl = String.format(gglFormat, tableName, String.join(",", uniqueColumns));
                ApiPagedResult page = ruleService.queryForMapList(ggl, false);
                Map<String, Set<Object>> redisValue = pageResultToMap(page, uniqueColumns);
                logger.info(String.format("%s - %s => %s", key, page.getTotal(), (redisValue != null ? redisValue.size() : 0)));
                redisTemplate.opsForValue().set(key, redisValue, REDIS_TIME_OUT, TimeUnit.MINUTES);
            }
        } catch (Exception ex) {
            logger.info(ex.getMessage(), ex);
        }

        return uniqueKeys;
    }

    private Map<String, Set<Object>> pageResultToMap(ApiPagedResult page, Set<String> uniqueColumns) {
        Map<String, Set<Object>> redisValue = new HashMap<>();
        if (page != null && page.getData() != null && page.getTotal() > 0) {
            List<Map<String, Object>> mapList = (List<Map<String, Object>>) page.getData();
            for (String fieldName : uniqueColumns) {
                Set<Object> values = new LinkedHashSet<>();
                for (Map<String, Object> map : mapList) {
                    Object value = map.get(fieldName);
                    if (value != null) {
                        values.add(value);
                    }
                }
                redisValue.put(fieldName, values);
            }
        }

        return redisValue;
    }

    /**
     * 数据处理
     *
     * @param currentUUID
     * @param businessDataMapList     业务数据
     * @param businessTypeRuleDataSet 清洗规则
     * @return
     */
    public List<Map<String, BusinessData>> handleBusinessDataRules(String currentUUID, List<Map<String, BusinessData>> businessDataMapList, Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet) {
        if (businessDataMapList == null || businessDataMapList.size() == 0 || businessTypeRuleDataSet == null || businessTypeRuleDataSet.size() == 0) {
            return businessDataMapList;
        }
        // 设置缓存
        List<String> cacheKeys = setTypeRuleCache(currentUUID, businessTypeRuleDataSet);
        // 数据处理
        List<Map<String, BusinessData>> newMapList = handleTypeRules1(currentUUID, businessDataMapList, businessTypeRuleDataSet, 0);
        // 清理缓存
        redisTemplate.delete(cacheKeys);

        return newMapList;
    }

    private List<Map<String, BusinessData>> handleTypeRules1(String currentUUID, List<Map<String, BusinessData>> businessDataMapList, Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet, int startIndex) {
        List<Map<String, BusinessData>> newMapList = new ArrayList<>();
        for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
            List<Map<String, BusinessData>> handleData = new ArrayList<>();
            handleData.add(businessDataMap);
            List<Map<String, BusinessData>> handledData = handleTypeRules(currentUUID, handleData, businessTypeRuleDataSet, startIndex);
            newMapList.addAll(handledData);
        }
        return newMapList;
    }

    private List<Map<String, BusinessData>> handleTypeRules(String currentUUID, List<Map<String, BusinessData>> businessDataMapList, Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet, int startIndex) {
        if (businessDataMapList == null || businessDataMapList.size() == 0) {
            return new ArrayList<>();
        }
        for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
            // 一行业务数据，键值对
            Map<String, Object> valueMap = new HashMap<>();
            for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                valueMap.put(businessDataEntry.getKey(), businessDataEntry.getValue().getValue());
            }
            // 清洗规则
            for (Map<Integer, BusinessTypeRuleData> ruleDataMap : businessTypeRuleDataSet) {
                for (Map.Entry<Integer, BusinessTypeRuleData> ruleDataEntry : ruleDataMap.entrySet()) {
                    if (startIndex > ruleDataEntry.getKey()) {
                        continue;
                    }
                    BusinessTypeRuleData ruleData = ruleDataEntry.getValue();
                    if (ruleData != null) {
                        // 需要清洗的列名
                        Set<String> columnNames = ruleData.getColumnNames();
                        if (columnNames == null || columnNames.size() == 0) {
                            continue;
                        }
                        // 清洗规则
                        if (ruleData.isRuleTypeDeletes() || ruleData.isRuleTypeReplace() || ruleData.isRuleTypeTrim() || ruleData.isRuleTypeUpperCase()
                                || ruleData.isRuleTypeLowerCase() || ruleData.isRuleTypeExpression() || ruleData.isRuleTypeCheckBox()
                                || ruleData.isRuleTypeDictionary() || ruleData.isRuleTypeQueryGoal() || ruleData.isRuleTypeQueryRule()) {
                            typeRuleBaseToColumn(currentUUID, businessDataMap, valueMap, columnNames, ruleData);
                        } else if (ruleData.isRuleTypeMulti() || ruleData.isRuleTypeSym()) {
                            List<Map<String, BusinessData>> multiMapList = typeRuleMultiToColumn(businessDataMap, columnNames, ruleData);
                            List<Map<String, BusinessData>> handleMulti = handleTypeRules1(currentUUID, multiMapList, businessTypeRuleDataSet, ruleDataEntry.getKey() + 1);
                            businessDataMapList.clear();
                            businessDataMapList.addAll(handleMulti);
                            return businessDataMapList;
                        }
                    }
                }
            }
        }

        return businessDataMapList;
    }

    private List<Map<String, BusinessData>> typeRuleMultiToColumn(Map<String, BusinessData> businessDataMap, Set<String> columnNames, BusinessTypeRuleData ruleData) {
        List<Map<String, BusinessData>> handleDataMapList = new ArrayList<>();
        // 分类
        Map<String, BusinessData> singleData = new HashMap<>();
        Map<String, BusinessData> multiData = new LinkedHashMap<>();
        Map<String, BusinessData> symData = new HashMap<>();

        Set<String> dataValues = new LinkedHashSet<>();
        int maxLength = 0;
        for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
            boolean isMulti = false;
            BusinessData businessData = businessDataEntry.getValue();
            if (businessData != null && businessData.getValue() != null && Strings.isNotBlank(String.valueOf(businessData.getValue()))) {
                for (String columnName : columnNames) {
                    if (columnName.equalsIgnoreCase(businessDataEntry.getKey())) {
                        if (Strings.isNotBlank(ruleData.getRule())) {
                            try {
                                String[] multiValue = String.valueOf(businessData.getValue()).split(ruleData.getRule());
                                if (multiValue != null && multiValue.length > 0) {
                                    for (int i = 0; i < multiValue.length; i++) {
                                        multiValue[i] = Strings.isNotBlank(multiValue[i]) ? multiValue[i].trim() : "";
                                    }
                                    businessData.setMultiValue(multiValue);
                                    if (ruleData.isRuleTypeMulti()) {
                                        isMulti = true;
                                        multiData.put(businessDataEntry.getKey(), businessData);
                                    } else if (ruleData.isRuleTypeSym()) {
                                        isMulti = true;
                                        symData.put(businessDataEntry.getKey(), businessData);
                                        maxLength = maxLength > multiValue.length ? maxLength : multiValue.length;
                                        dataValues.add(String.join(",", multiValue));
                                    }
                                    logger.info(String.format("数据清洗[Y.%s,X.%s], [%s], [%s], %s => %s", businessData.getYIndex(), businessData.getXIndex(), columnName, ruleData.getType(), businessData.getValue(), JSON.toJSONString(multiValue)));
                                }
                            } catch (Exception ex) {
                                logger.error(ex.getMessage(), ex);
                            }
                        } else {
                            businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                        }
                    }
                }
            }
            if (!isMulti) {
                BusinessData data = new BusinessData();
                data.setXIndex(businessData.getXIndex());
                data.setYIndex(businessData.getYIndex());
                data.setValue(businessData.getValue());
                data.setPrimevalValue(businessData.getPrimevalValue());
                data.setTransitionValue(businessData.getTransitionValue());
                data.setMultiValue(businessData.getMultiValue());
                data.setBusinessTypeData(businessData.getBusinessTypeData());
                data.setErrorMsg(businessData.getErrorMsg());
                singleData.put(businessDataEntry.getKey(), data);
            }
        }
        if (dataValues.size() > 1 && "AB*CD".equalsIgnoreCase(ruleData.getGoal())) {
            multiData.putAll(symData);
            symData.clear();
        }
        // 对乘值处理
        List<Map<String, BusinessData>> multiMapList = cartesianProduct(multiData);
        // 对称值处理
        List<Map<String, BusinessData>> symMapList = new ArrayList<>();
        if (!symData.isEmpty()) {
            for (int i = 0; i < maxLength; i++) {
                Map<String, BusinessData> symMap = new HashMap<>();
                for (Map.Entry<String, BusinessData> businessDataEntry : symData.entrySet()) {
                    BusinessData businessData = businessDataEntry.getValue();
                    BusinessData data = new BusinessData();
                    data.setXIndex(businessData.getXIndex());
                    data.setYIndex(businessData.getYIndex());
                    String[] multiValue = businessData.getMultiValue();
                    if ("AB:CN".equalsIgnoreCase(ruleData.getGoal())) {
                        data.setValue((i < multiValue.length) ? multiValue[i] : null);
                    } else {
                        data.setValue(multiValue[i < multiValue.length ? i : multiValue.length - 1]);
                    }
                    data.setPrimevalValue(businessData.getPrimevalValue());
                    data.setTransitionValues(businessData.getTransitionValue());
                    data.setTransitionValue(data.getValue());
                    data.setBusinessTypeData(businessData.getBusinessTypeData());
                    data.setErrorMsgs(businessData.getErrorMsg());
                    symMap.put(businessDataEntry.getKey(), data);
                }
                symMapList.add(symMap);
            }
        }
        handleDataMapList = mergeBusinessData(singleData, multiMapList, symMapList);

        return handleDataMapList;
    }

    private void typeRuleBaseToColumn(String currentUUID, Map<String, BusinessData> businessDataMap, Map<String, Object> valueMap, Set<String> columnNames, BusinessTypeRuleData ruleData) {
        for (String columnName : columnNames) {
            BusinessData businessData = businessDataMap.get(columnName);
            if (businessData == null) {
                continue;
            }
            Object newValue = null;
            String oldValue = businessData.getValue() == null ? null : String.valueOf(businessData.getValue());
            if (ruleData.isRuleTypeDeletes()) {
                if (Strings.isNotBlank(ruleData.getRule())) {
                    newValue = Strings.isNotBlank(oldValue) ? oldValue.replaceAll(ruleData.getRule(), "") : "";
                    newValue = Strings.isNotBlank(String.valueOf(newValue)) ? newValue : null;
                } else {
                    businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeReplace()) {
                if (Strings.isNotBlank(ruleData.getRule()) && Strings.isNotBlank(ruleData.getGoal())) {
                    newValue = Strings.isNotBlank(oldValue) ? oldValue.replaceAll(ruleData.getRule(), ruleData.getGoal()) : "";
                    newValue = Strings.isNotBlank(String.valueOf(newValue)) ? newValue : null;
                } else {
                    businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule or Goal is empty！");
                }
            } else if (ruleData.isRuleTypeUpperCase()) {
                newValue = Strings.isNotBlank(oldValue) ? oldValue.toUpperCase(Locale.ENGLISH) : null;
            } else if (ruleData.isRuleTypeLowerCase()) {
                newValue = Strings.isNotBlank(oldValue) ? oldValue.toLowerCase(Locale.ENGLISH) : null;
            } else if (ruleData.isRuleTypeTrim()) {
                newValue = Strings.isNotBlank(oldValue) && Strings.isNotBlank(oldValue.trim()) ? oldValue.trim() : null;
            } else if (ruleData.isRuleTypeExpression()) {
                if (Strings.isNotBlank(ruleData.getRule())) {
                    newValue = JsProvider.executeExpression(ruleData.getRule(), valueMap);
                } else {
                    businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeCheckBox()) {
                if (Strings.isNotBlank(ruleData.getRule())) {
                    Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && redisValues.size() > 0) {
                        String[] oValues = oldValue.split(",");
                        if (oValues != null && oValues.length > 0) {
                            Set<String> nValues = new LinkedHashSet<>();
                            for (String oValue : oValues) {
                                String nValue = redisValues.get(oValue);
                                if (Strings.isNotBlank(nValue)) {
                                    nValues.add(nValue);
                                }
                            }
                            newValue = String.join(",", nValues);
                        }
                    }
                } else {
                    businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeDictionary()) {
                if (Strings.isNotBlank(ruleData.getRule())) {
                    Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && redisValues.size() > 0) {
                        newValue = redisValues.get(oldValue);
                    }
                } else {
                    businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeQueryGoal()) {
                String tableName = ruleData.getQueryRuleTable();
                List<String> colNames = ruleData.getQueryRuleColumn();
                if (Strings.isNotBlank(tableName) && colNames != null && colNames.size() > 0 && Strings.isNotBlank(ruleData.getGoal())) {
                    Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && redisValues.size() > 0) {
                        newValue = redisValues.get(oldValue);
                    }
                } else {
                    businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                }
            } else if (ruleData.isRuleTypeQueryRule()) {
                String tableName = ruleData.getQueryRuleTable();
                List<String> colNames = ruleData.getQueryRuleColumn();
                if (Strings.isNotBlank(tableName) && colNames != null && colNames.size() > 0 && Strings.isNotBlank(ruleData.getGoal())) {
                    Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && redisValues.size() > 0) {
                        newValue = redisValues.get(oldValue);
                    }
                } else {
                    businessData.setErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                }
            }
            newValue = (ruleData.isRetain() && newValue == null) ? businessData.getValue() : newValue;
            if (businessData.getValue() == null || !businessData.getValue().equals(newValue)) {
                businessData.setValue(newValue);
                businessData.setTransitionValue(newValue);
            }
            logger.info(String.format("数据清洗[Y.%s,X.%s], [%s], [%s], %s => %s, (%s)", businessData.getYIndex(), businessData.getXIndex(), columnName, ruleData.getType(), oldValue, newValue, ruleData.getRule()));
        }
    }

    private List<String> setTypeRuleCache(String currentUUID, Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet) {
        List<String> cacheList = new ArrayList<>();
        // 类型
        Map<String, BusinessTypeRuleData> ruleDataDict = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataGoal = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataRule = new HashMap<>();
        // 数据解析
        if (businessTypeRuleDataSet != null && businessTypeRuleDataSet.size() > 0) {
            for (Map<Integer, BusinessTypeRuleData> ruleDataMap : businessTypeRuleDataSet) {
                for (Map.Entry<Integer, BusinessTypeRuleData> ruleDataEntry : ruleDataMap.entrySet()) {
                    BusinessTypeRuleData ruleData = ruleDataEntry.getValue();
                    if (ruleData != null) {
                        if (ruleData.isRuleTypeDictionary() || ruleData.isRuleTypeCheckBox()) {
                            if (Strings.isNotBlank(ruleData.getRule())) {
                                String key = String.format("%s:%s", currentUUID, ruleData.getRule());
                                if (!ruleDataDict.containsKey(key)) {
                                    ruleDataDict.put(key, ruleData);
                                }
                            }
                        } else if (ruleData.isRuleTypeQueryGoal()) {
                            String tableName = ruleData.getQueryRuleTable();
                            List<String> columnNames = ruleData.getQueryRuleColumn();
                            if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && columnNames.size() > 0) {
                                String key = String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal());
                                if (!ruleDataGoal.containsKey(key)) {
                                    ruleDataGoal.put(key, ruleData);
                                }
                            }
                        } else if (ruleData.isRuleTypeQueryRule()) {
                            String tableName = ruleData.getQueryRuleTable();
                            List<String> columnNames = ruleData.getQueryRuleColumn();
                            if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && columnNames.size() > 0) {
                                String key = String.format("%s:%s", currentUUID, ruleData.getRule());
                                if (!ruleDataRule.containsKey(key)) {
                                    ruleDataRule.put(key, ruleData);
                                }
                            }
                        }
                    }
                }
            }
        }
        // 字典查询
        List<String> dictKeys = setDictRuleRedis(currentUUID, ruleDataDict);
        cacheList.addAll(dictKeys);
        // 目标字段查询
        List<String> goalRedis = setQueryRuleRedis(ruleDataGoal);
        cacheList.addAll(goalRedis);
        // 规则字段查询
        List<String> ruleRedis = setQueryRuleRedis(ruleDataRule);
        cacheList.addAll(ruleRedis);

        return cacheList;
    }

    public static void bottomLayerOfTree(List<ExportColumn> columns, List<ExportColumn> target) {
        for (ExportColumn exportColumn : columns) {
            if (exportColumn.getChildren() != null && exportColumn.getChildren().size() > 0) {
                bottomLayerOfTree(exportColumn.getChildren(), target);
            } else {
                target.add(exportColumn);
            }
        }
    }

    public static void cellRangeAddress(int startCol, int startRow, List<ExportColumn> columns, List<ExportColumn> target) {
        for (int i = 0; i < columns.size(); i++) {
            ExportColumn column = columns.get(i);
            column.setFirstRow(startRow);
            column.setLastRow(column.getFirstRow() + column.getDepth() - 1);
            column.setFirstCol(i > 0 ? (columns.get(i - 1).getLastCol() + 1) : startCol);
            column.setLastCol(column.getFirstCol() + column.getBreadth() - 1);
            if (column.getChildren() != null && column.getChildren().size() > 0) {
                cellRangeAddress(column.getFirstCol(), column.getLastRow() + 1, column.getChildren(), target);
            }
            target.add(column);
        }
    }

}
