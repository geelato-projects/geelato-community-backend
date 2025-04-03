package cn.geelato.web.platform.m.excel.service;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.field.FieldMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.core.script.js.JsProvider;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.exception.file.FileException;
import cn.geelato.web.platform.m.base.entity.Dict;
import cn.geelato.web.platform.m.base.entity.DictItem;
import cn.geelato.web.platform.m.base.service.RuleService;
import cn.geelato.web.platform.m.excel.entity.*;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
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
 */
@Component
@Slf4j
public class ExcelCommonUtils {
    public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DateUtils.DATE);
    public static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat(DateUtils.DATETIME);
    public static final Pattern CELL_META_PATTERN = Pattern.compile("\\$\\{[\\\u4e00-\\\u9fa5,\\w,\\.\\(\\)\\（\\）]+\\}");
    public static final Pattern ROW_META_PATTERN = Pattern.compile("\\$\\{rowMeta\\.[\\w,\\.,\\=]+\\}");
    private static final int REDIS_TIME_OUT = 60;
    private static final int GGL_QUERY_TOTAL = 10000;
    private static final String REDIS_UNIQUE_KEY = "uniques";
    private final FilterGroup filterGroup = new FilterGroup().addFilter(ColumnDefault.DEL_STATUS_FIELD, String.valueOf(ColumnDefault.DEL_STATUS_VALUE));
    private final MetaManager metaManager = MetaManager.singleInstance();
    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;
    @Autowired
    protected RuleService ruleService;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 为空时抛出异常
     * <p>
     * 检查给定的对象是否为空，如果为空则抛出指定的异常。
     *
     * @param object        要检查的对象
     * @param fileException 如果对象为空时，要抛出的异常
     * @param <T>           异常的类型，必须是FileException或其子类的实例
     * @throws T 如果对象为空，则抛出指定的异常
     */
    public static <T extends FileException> void notNull(@Nullable Object object, T fileException) throws T {
        if (object == null) {
            throw fileException;
        }
    }

    /**
     * 将字符串转换为数值
     * <p>
     * 将给定的字符串转换为相应的数值类型。如果字符串是数字格式的（包括整数和小数），则将其转换为相应的数值类型（Long或Double）。
     *
     * @param value 待转换的字符串
     * @return 转换后的数值对象，如果字符串不是有效的数字格式，则返回null
     */
    public static Object stringToNumber(String value) {
        Object cellValue = null;
        if (Strings.isNotBlank(value) && value.matches("-?\\d+(\\.\\d+)?")) {
            if (!value.contains(".")) {
                cellValue = Long.parseLong(value);
            } else {
                cellValue = new BigDecimal(value).doubleValue();
            }
        }
        return cellValue;
    }

    /**
     * 合并唯一约束的交集
     * <p>
     * 根据提供的单元格元数据列表、值映射和值列表，合并具有唯一约束的交集。
     *
     * @param cellMetaList 单元格元数据列表，包含每个单元格的元数据
     * @param valueMap     值映射，将单元格值映射到其他数据
     * @param valueList    值列表，包含多行数据
     * @return 返回合并后的唯一约束交集列表，如果没有唯一约束或唯一约束的交集为空，则返回null
     */
    public static List<List<Integer>> getMergeUniqueScope(List<CellMeta> cellMetaList, Map valueMap, List<Map> valueList) {
        List<List<List<Integer>>> limitSetMap = new LinkedList<>();
        int uniqueNum = 0; // 唯一约束的数据量
        for (CellMeta cellMeta : cellMetaList) {
            if (cellMeta.getPlaceholderMeta().isIsMerge() && cellMeta.getPlaceholderMeta().isIsUnique()) {
                uniqueNum += 1;
                // 获取数据相同的行
                List<List<Integer>> integerSet = getIntegerSet(cellMeta, valueMap, valueList);
                if (!integerSet.isEmpty()) {
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
                if (!intersection.isEmpty()) {
                    result.add(intersection);
                }
            }
        }

        return result;
    }

    /**
     * 获取数据相同的行
     * <p>
     * 根据给定的单元格元数据、值映射和值列表，找出值相同地行并返回它们的索引集合。
     *
     * @param cellMeta  单元格元数据对象，包含关于如何解析单元格值的信息
     * @param valueMap  值映射，用于解析单元格值时的上下文数据
     * @param valueList 包含单元格值的列表，每个元素都是一个包含单元格值的映射
     * @return 返回一个列表，每个元素都是一个整数列表，表示值相同地行的索引集合
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
        return integerSet;
    }

    /**
     * 获取单元格值
     * <p>
     * 根据提供的元数据、值映射表和列表值映射表，获取单元格的值。
     *
     * @param meta         元数据对象，包含计算模式、变量名、常量值或表达式等信息
     * @param valueMap     值映射表，用于存储非列表类型的变量及其对应的值
     * @param listValueMap 列表值映射表，用于存储列表类型的变量及其对应的值
     * @return 返回单元格的值，如果无法获取到值，则返回一个空字符串
     */
    public static Object getCellValue(PlaceholderMeta meta, Map valueMap, Map listValueMap) {
        Object value = "";
        // 不是列表，且是变更
        if (meta.isValueComputeModeVar()) {
            if (meta.isIsList()) {
                Object v = listValueMap.get(meta.getVar());
                value = getCellValueByValueType(meta, v);
            } else {
                if (meta.getVar() != null && !meta.getVar().trim().isEmpty()) {
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
     * <p>
     * 根据给定的元数据（PlaceholderMeta）和值（value），根据元数据中指定的类型返回相应的值。
     *
     * @param meta  元数据对象，包含值的类型信息
     * @param value 要处理的原始值
     * @return 根据元数据指定的类型处理后的值
     */
    private static Object getCellValueByValueType(PlaceholderMeta meta, Object value) {
        if (value != null) {
            if (meta.isValueTypeNumber()) {
                if (!value.toString().contains(".")) {
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
     * 构建连续整数集合的起止节点
     * <p>
     * 该方法接受一个整数集合作为输入，将集合中的连续整数划分为多个范围，并返回每个范围的起止节点。
     *
     * @param numbers 输入的整数集合
     * @return 返回每个连续整数范围的起止节点集合，每个范围用一个包含两个整数的数组表示，分别为范围的起始值和结束值
     */
    public static List<Integer[]> findScopes(Set<Integer> numbers) {
        List<Integer[]> list = new ArrayList<>();
        if (numbers != null && !numbers.isEmpty()) {
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
     * <p>
     * 将输入的整数列表转换为一个包含连续整数范围的列表。每个范围以两个元素的列表表示，
     * 第一个元素为范围的起始值，第二个元素为范围的结束值。
     *
     * @param numbers 输入的整数列表
     * @return 返回包含连续整数范围的列表，其中每个范围都以两个元素的列表表示
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

    public static void bottomLayerOfTree(List<ExportColumn> columns, List<ExportColumn> target) {
        for (ExportColumn exportColumn : columns) {
            if (exportColumn.getChildren() != null && !exportColumn.getChildren().isEmpty()) {
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
            if (column.getChildren() != null && !column.getChildren().isEmpty()) {
                cellRangeAddress(column.getFirstCol(), column.getLastRow() + 1, column.getChildren(), target);
            }
            target.add(column);
        }
    }

    /**
     * 获取表格默认字段
     * <p>
     * 从元数据中获取表格的默认字段列表。
     *
     * @return 返回包含表格默认字段名称的列表
     */
    public List<String> getDefaultColumns() {
        List<String> columnNames = new ArrayList<>();
        List<ColumnMeta> columnMetaList = metaManager.getDefaultColumn();
        if (columnMetaList != null && !columnMetaList.isEmpty()) {
            for (ColumnMeta columnMeta : columnMetaList) {
                if (!columnNames.contains(columnMeta.getName())) {
                    columnNames.add(columnMeta.getName());
                }
            }
        }

        return columnNames;
    }

    /**
     * 解析业务数据类型规则
     * <p>
     * 根据提供的规则字符串，解析并返回业务数据类型规则的集合。
     *
     * @param rules 规则字符串，包含业务数据类型规则的JSON数组
     * @return 返回业务数据类型规则的集合
     */
    public Set<BusinessTypeRuleData> readBusinessTypeRuleData(String rules) {
        Set<BusinessTypeRuleData> typeRuleDataSet = new LinkedHashSet<>();
        if (Strings.isNotBlank(rules)) {
            List<BusinessTypeRuleData> typeRuleDataList = com.alibaba.fastjson.JSON.parseArray(rules, BusinessTypeRuleData.class);
            if (typeRuleDataList != null && !typeRuleDataList.isEmpty()) {
                typeRuleDataList.sort(Comparator.comparingInt(BusinessTypeRuleData::getOrder));
                typeRuleDataSet.addAll(typeRuleDataList);
            }
        }

        return typeRuleDataSet;
    }

    /**
     * 导入数据，处理多值数据
     * <p>
     * 该方法用于处理包含多值数据的业务数据列表。根据数据的不同类型（单值、多值、对称值）进行分类处理，并生成处理后的数据列表。
     *
     * @param businessDataMapList 包含业务数据的列表，每个元素是一个包含业务数据键值对的映射
     * @return 返回处理后的业务数据列表，每个元素是一个包含业务数据键值对的映射
     */
    public List<Map<String, BusinessData>> handleBusinessDataMultiScene(List<Map<String, BusinessData>> businessDataMapList) {
        List<Map<String, BusinessData>> handleDataMapList = new ArrayList<>();
        Set<Map<String, Object>> multiLoggers = new LinkedHashSet<>();
        if (businessDataMapList != null && !businessDataMapList.isEmpty()) {
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
                                if (multiValue.length > 0) {
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
                                        maxLength = Math.max(maxLength, multiValue.length);
                                    }
                                }
                            } catch (Exception ex) {
                                log.error(ex.getMessage(), ex);
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
                            data.addAllErrorMsg(businessData.getErrorMsg());
                            symMap.put(businessDataEntry.getKey(), data);
                        }
                        symMapList.add(symMap);
                    }
                }

                List<Map<String, BusinessData>> mergeData = mergeBusinessData(singleData, multiMapList, symMapList);
                handleDataMapList.addAll(mergeData);
            }
        }
        return handleDataMapList;
    }

    /**
     * 导入数据，处理多值数据
     * <p>
     * 将单值数据、多值相乘数据和多值对称数据进行合并处理。
     *
     * @param singleData   单值数据，包含业务数据的Map集合
     * @param multiMapList 多值相乘数据，每个Map代表一组多值数据
     * @param symMapList   多值对称数据，每个Map代表一组多值数据
     * @return 返回合并后的业务数据列表，每个元素都是一个包含业务数据的Map
     */
    private List<Map<String, BusinessData>> mergeBusinessData(Map<String, BusinessData> singleData, List<Map<String, BusinessData>> multiMapList, List<Map<String, BusinessData>> symMapList) {
        List<Map<String, BusinessData>> mergeData = new ArrayList<>();
        if (multiMapList != null && !multiMapList.isEmpty()) {
            if (symMapList != null && !symMapList.isEmpty()) {
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
            if (symMapList != null && !symMapList.isEmpty()) {
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
     * 导入数据，处理多值数据
     * <p>
     * 对传入的多值数据进行笛卡尔积运算，生成所有可能的组合。
     *
     * @param multiData 包含多值数据的映射，其中键为业务数据类型的标识，值为包含多值的BusinessData对象
     * @return 返回所有可能的组合，每个组合是一个映射，包含键为业务数据类型标识，值为对应的BusinessData对象的列表
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
                data.addAllErrorMsg(businessData.getErrorMsg());
                map.put(key, data);
            }
            mapList.add(map);
        }

        return mapList;
    }

    /**
     * 递归计算笛卡尔积
     * <p>
     * 使用递归方法计算给定二维字符串数组的笛卡尔积。
     *
     * @param arrays 二维字符串数组，用于计算笛卡尔积
     * @return 返回包含所有笛卡尔积结果的集合，每个元素都是一个字符串数组
     */
    private Set<String[]> cartesianProductHelper(String[][] arrays) {
        Set<String[]> result = new LinkedHashSet<>();
        cartesianProductHelper(arrays, 0, new String[arrays.length], result);
        return result;
    }

    /**
     * 递归计算组合
     * <p>
     * 使用递归方法计算给定字符串数组的笛卡尔积（组合）。
     *
     * @param arrays  输入的字符串数组，每个数组代表一个维度
     * @param index   当前递归的维度索引
     * @param current 当前维度的元素组合数组
     * @param result  存储计算结果的集合，结果以字符串数组的形式存储
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
     * <p>
     * 根据业务规则处理输入的业务数据列表。
     *
     * @param currentUUID         当前操作的唯一标识符
     * @param businessDataMapList 包含业务数据的列表，每个元素是一个映射，键为业务数据类型的标识，值为对应的BusinessData对象
     * @param priorityMulti       是否优先处理多值数据
     * @return 返回处理后的业务数据列表，每个元素是一个包含业务数据键值对的映射
     */
    public List<Map<String, BusinessData>> handleBusinessDataRule(String currentUUID, List<Map<String, BusinessData>> businessDataMapList, boolean priorityMulti) {
        if (businessDataMapList != null && !businessDataMapList.isEmpty()) {
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
                    if (typeRuleDataSet != null && !typeRuleDataSet.isEmpty()) {
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
                                        businessData.addErrorMsg("Rule resolution failure。[Deletes] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeReplace()) {
                                    if (Strings.isNotBlank(ruleData.getRule()) && Strings.isNotBlank(ruleData.getGoal())) {
                                        newValue = oldValue.replaceAll(ruleData.getRule(), ruleData.getGoal());
                                    } else {
                                        businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule or Goal is empty！");
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
                                        businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeCheckBox()) {
                                    if (Strings.isNotBlank(ruleData.getRule())) {
                                        Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                                        if (redisValues != null && !redisValues.isEmpty()) {
                                            String[] oValues = oldValue.split(",");
                                            if (oValues.length > 0) {
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
                                        businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeDictionary()) {
                                    if (Strings.isNotBlank(ruleData.getRule())) {
                                        Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                                        if (redisValues != null && !redisValues.isEmpty()) {
                                            newValue = redisValues.get(oldValue);
                                        }
                                    } else {
                                        businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                                    }
                                } else if (ruleData.isRuleTypeQueryGoal()) {
                                    String tableName = ruleData.getQueryRuleTable();
                                    List<String> columnNames = ruleData.getQueryRuleColumn();
                                    if (Strings.isNotBlank(tableName) && columnNames != null && !columnNames.isEmpty() && Strings.isNotBlank(ruleData.getGoal())) {
                                        Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal()));
                                        if (redisValues != null && !redisValues.isEmpty()) {
                                            newValue = redisValues.get(oldValue);
                                        }
                                        if (newValue == null) {
                                            newValue = businessData.getValue();
                                        }
                                    } else {
                                        businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                                    }
                                } else if (ruleData.isRuleTypeQueryRule()) {
                                    String tableName = ruleData.getQueryRuleTable();
                                    List<String> columnNames = ruleData.getQueryRuleColumn();
                                    if (Strings.isNotBlank(tableName) && columnNames != null && !columnNames.isEmpty() && Strings.isNotBlank(ruleData.getGoal())) {
                                        Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                                        if (redisValues != null && !redisValues.isEmpty()) {
                                            newValue = redisValues.get(oldValue);
                                        }
                                        if (newValue == null) {
                                            newValue = businessData.getValue();
                                        }
                                    } else {
                                        businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                                    }
                                } else {
                                    newValue = businessData.getValue();
                                }
                                businessData.setValue(newValue);
                            } catch (Exception ex) {
                                businessData.addErrorMsg("Rule resolution failure。" + JSON.toJSONString(ruleData));
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
        // 类型
        Map<String, BusinessTypeRuleData> ruleDataDict = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataGoal = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataRule = new HashMap<>();
        // 数据解析
        if (businessDataMapList != null && !businessDataMapList.isEmpty()) {
            for (Map<String, BusinessData> businessDataMap : businessDataMapList) {
                for (Map.Entry<String, BusinessData> businessDataEntry : businessDataMap.entrySet()) {
                    BusinessData businessData = businessDataEntry.getValue();
                    BusinessTypeData typeData = businessData.getBusinessTypeData();
                    Set<BusinessTypeRuleData> typeRuleDataSet = typeData.getTypeRuleData();
                    if (typeRuleDataSet != null && !typeRuleDataSet.isEmpty()) {
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
                                if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && !columnNames.isEmpty()) {
                                    String key = String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal());
                                    if (!ruleDataGoal.containsKey(key)) {
                                        ruleDataGoal.put(key, ruleData);
                                    }
                                }
                            } else if (ruleData.isRuleTypeQueryRule()) {
                                String tableName = ruleData.getQueryRuleTable();
                                List<String> columnNames = ruleData.getQueryRuleColumn();
                                if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && !columnNames.isEmpty()) {
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
        List<String> cacheList = new ArrayList<>(dictKeys);
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
     * <p>
     * 将数据字典信息缓存到Redis中，以便于后续快速访问。
     *
     * @param currentUUID 当前操作的唯一标识符，用于构建缓存的键
     * @param ruleDataMap 包含业务规则数据的映射，其中键为规则的唯一标识，值为对应的业务规则数据对象
     * @return 返回缓存的键列表，用于后续可能的缓存清理操作
     */
    private List<String> setDictRuleRedis(String currentUUID, Map<String, BusinessTypeRuleData> ruleDataMap) {
        List<String> dictKeys = new ArrayList<>();
        if (ruleDataMap != null && !ruleDataMap.isEmpty()) {
            // 所有字典编码
            List<String> dictCodes = new ArrayList<>();
            for (Map.Entry<String, BusinessTypeRuleData> ruleDataEntry : ruleDataMap.entrySet()) {
                dictCodes.add(ruleDataEntry.getValue().getRule());
            }

            List<Dict> dictList;
            List<DictItem> dictItemList;
            // 查询
            FilterGroup filter = new FilterGroup();
            filter.addFilter("dictCode", FilterGroup.Operator.in, String.join(",", dictCodes));
            dictList = dao.queryList(Dict.class, filter, null);
            if (dictList != null && !dictList.isEmpty()) {
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
                    if (dictItemList != null && !dictItemList.isEmpty()) {
                        for (DictItem dictItem : dictItemList) {
                            if (dict.getId().equalsIgnoreCase(dictItem.getDictId())) {
                                dictItems.put(dictItem.getItemName(), dictItem.getItemCode());
                            }
                        }
                        redisTemplate.opsForValue().set(dictKey, dictItems, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        dictKeys.add(dictKey);
                    }
                }
            }
        }

        return dictKeys;
    }

    /**
     * 主键查询并缓存结果
     * <p>
     * 根据提供的业务规则数据映射，执行主键查询，并将查询结果缓存到Redis中，以便于后续快速访问。
     *
     * @param ruleDataMap 包含业务规则数据的映射，其中键为规则的唯一标识，值为对应的业务规则数据对象
     * @return 返回缓存的键列表，用于后续可能的缓存清理操作
     */
    private List<String> setQueryRuleRedis(Map<String, BusinessTypeRuleData> ruleDataMap) {
        List<String> primaryKeys = new ArrayList<>();
        String gglFormat = "{\"%s\": {\"@fs\": \"%s\"}}";
        try {
            if (ruleDataMap != null && !ruleDataMap.isEmpty()) {
                for (Map.Entry<String, BusinessTypeRuleData> ruleDataEntry : ruleDataMap.entrySet()) {
                    String key = ruleDataEntry.getKey();
                    BusinessTypeRuleData ruleData = ruleDataEntry.getValue();
                    if (ruleData != null) {
                        Set<String> columnNames = new LinkedHashSet<>();
                        columnNames.add(ruleData.getGoal());
                        columnNames.addAll(ruleData.getQueryRuleColumn());
                        String ggl = String.format(gglFormat, ruleData.getQueryRuleTable(), String.join(",", columnNames));
                        ApiPagedResult<List<Map<String, Object>>> page = ruleService.queryForMapList(ggl, false);
                        Map<String, Object> redisValue = pagedResultToMap(page, ruleData.getGoal(), ruleData.getQueryRuleColumn());
                        redisTemplate.opsForValue().set(key, redisValue, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        primaryKeys.add(key);
                    }
                }
            }
        } catch (Exception ex) {
            log.info(ex.getMessage(), ex);
        }

        return primaryKeys;
    }

    /**
     * 设置缓存，包括数据字典和主键查询结果的缓存
     * <p>
     * 根据提供的当前主键、元数据和业务数据，将相关数据字典和主键查询结果缓存到Redis中，以便于后续快速访问。
     *
     * @param currentUUID 当前主键，用于构建缓存的键
     * @param tableMeta   元数据，包含业务数据的字段信息和评估类型等
     * @param data        业务数据，包含需要缓存的相关数据
     * @return 返回缓存的键列表，用于后续可能的缓存清理操作
     */
    public List<String> setCache(String currentUUID, Map<String, List<BusinessMeta>> tableMeta, List<Map<String, BusinessData>> data) {
        // 元数据
        Map<String, ConditionMeta> dictMetas = new HashMap<>();
        Map<String, ConditionMeta> primaryMetas = new HashMap<>();
        for (Map.Entry<String, List<BusinessMeta>> metaMap : tableMeta.entrySet()) {
            if (metaMap.getValue() != null && !metaMap.getValue().isEmpty()) {
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
        List<String> cacheList = new ArrayList<>(dictKeys);
        // 主键
        List<String> primaryKeys = setPrimaryRedis(primaryMetas);
        cacheList.addAll(primaryKeys);

        return cacheList;
    }

    /**
     * 数据字典缓存
     * <p>
     * 将数据字典信息缓存到Redis中，以便于后续快速访问。
     *
     * @param currentUUID 当前操作的唯一标识符，用于构建缓存的键
     * @param dictMetas   包含数据字典元数据的映射，键为缓存键，值为对应的条件元数据对象
     * @return 返回缓存的键列表，用于后续可能的缓存清理操作
     */
    private List<String> setDictRedis(String currentUUID, Map<String, ConditionMeta> dictMetas) {
        List<String> dictKeys = new ArrayList<>();
        if (dictMetas != null && !dictMetas.isEmpty()) {
            Set<String> dictCodes = new LinkedHashSet<>();
            // Set<String> dictItemNames = new LinkedHashSet<>();
            for (Map.Entry<String, ConditionMeta> metaEntry : dictMetas.entrySet()) {
                if (metaEntry.getValue() != null) {
                    dictCodes.add(metaEntry.getValue().getDictCode());
                    // dictItemNames.addAll(metaEntry.getValue().getValues());
                }
            }

            List<Dict> dictList;
            List<DictItem> dictItemList;
            // 查询
            FilterGroup filter = new FilterGroup();
            filter.addFilter("dictCode", FilterGroup.Operator.in, String.join(",", dictCodes));
            dictList = dao.queryList(Dict.class, filter, null);
            if (dictList != null && !dictList.isEmpty()) {
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
                    if (dictItemList != null && !dictItemList.isEmpty()) {
                        for (DictItem dictItem : dictItemList) {
                            if (dict.getId().equalsIgnoreCase(dictItem.getDictId())) {
                                dictItems.put(dictItem.getItemName(), dictItem.getItemCode());
                            }
                        }
                        redisTemplate.opsForValue().set(dictKey, dictItems, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        dictKeys.add(dictKey);
                    }
                }
            }
        }

        return dictKeys;
    }

    /**
     * 主键查询并缓存结果
     * <p>
     * 根据提供的条件元数据映射，执行主键查询，并将查询结果缓存到Redis中，以便于后续快速访问。
     *
     * @param primaryMetas 包含主键查询条件元数据的映射，键为缓存键，值为对应的条件元数据对象
     * @return 返回缓存的键列表，用于后续可能的缓存清理操作
     */
    private List<String> setPrimaryRedis(Map<String, ConditionMeta> primaryMetas) {
        List<String> primaryKeys = new ArrayList<>();
        String gglFormat = "{\"%s\": {\"@fs\": \"%s\"}}";
        try {
            if (primaryMetas != null && !primaryMetas.isEmpty()) {
                for (Map.Entry<String, ConditionMeta> metaEntry : primaryMetas.entrySet()) {
                    String key = metaEntry.getKey();
                    ConditionMeta meta = metaEntry.getValue();
                    if (meta != null) {
                        Set<String> columnNames = new LinkedHashSet<>();
                        columnNames.add(meta.getGoalName());
                        columnNames.addAll(meta.getColumnNames());
                        String ggl = String.format(gglFormat, meta.getTableName(), String.join(",", columnNames));
                        ApiPagedResult<List<Map<String, Object>>> page = ruleService.queryForMapList(ggl, false);
                        Map<String, Object> redisValue = pagedResultToMap(page, meta.getGoalName(), meta.getColumnNames());
                        redisTemplate.opsForValue().set(key, redisValue, REDIS_TIME_OUT, TimeUnit.MINUTES);
                        primaryKeys.add(key);
                    }
                }
            }
        } catch (Exception ex) {
            log.info(ex.getMessage(), ex);
        }

        return primaryKeys;
    }

    /**
     * 将分页查询结果转换为映射
     * <p>
     * 将ApiPagedResult对象中的查询结果转换为Map<String, Object>类型，其中键为查询字段的值，值为目标字段的值。
     *
     * @param page        分页查询结果对象
     * @param goalName    目标字段名称，用于从查询结果中提取对应的值
     * @param columnNames 查询字段名称列表，用于构建映射的键
     * @return 返回转换后的映射，键为查询字段的值，值为目标字段的值
     */
    private Map<String, Object> pagedResultToMap(ApiPagedResult<?> page, String goalName, List<String> columnNames) {
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
        if (fieldMetas != null && !fieldMetas.isEmpty()) {
            for (FieldMeta fieldMeta : fieldMetas) {
                ColumnMeta meta = fieldMeta.getColumn();
                if (meta != null && Strings.isNotBlank(meta.getFieldName()) && meta.getEnableStatus() == ColumnDefault.ENABLE_STATUS_VALUE && meta.getDelStatus() == ColumnDefault.DEL_STATUS_VALUE) {
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
        if (fieldMetas != null && !fieldMetas.isEmpty()) {
            for (FieldMeta fieldMeta : fieldMetas) {
                ColumnMeta meta = fieldMeta.getColumn();
                if (meta != null && Strings.isNotBlank(meta.getFieldName()) && meta.getEnableStatus() == ColumnDefault.ENABLE_STATUS_VALUE && meta.getDelStatus() == ColumnDefault.DEL_STATUS_VALUE) {
                    if (!uniqueColumns.containsKey(meta.getFieldName()) && !columnNames.contains(meta.getName()) && meta.isUniqued()) {
                        uniqueColumns.put(meta.getFieldName(), meta);
                    }
                }
            }
        }

        return uniqueColumns;
    }

    /**
     * 设置唯一性约束缓存
     * <p>
     * 根据提供的表名和唯一性字段，将表中满足唯一性约束的数据缓存到Redis中，以便于后续快速访问。
     *
     * @param currentUUID   当前操作的唯一标识符，用于构建缓存的键
     * @param tableName     表名，指定要缓存数据的表
     * @param uniqueColumns 唯一性字段集合，指定表中哪些字段的组合值具有唯一性
     * @return 返回缓存的键列表，但由于当前实现中未添加任何键到列表中，因此该列表始终为空
     */
    public List<String> setUniqueRedis(String currentUUID, String tableName, Set<String> uniqueColumns) {
        List<String> uniqueKeys = new ArrayList<>();
        String gglFormat = "{\"%s\": {\"@fs\": \"%s\"}}";
        String key = String.format("%s:%s:%s", currentUUID, tableName, REDIS_UNIQUE_KEY);
        try {
            if (Strings.isNotBlank(tableName) && !uniqueColumns.isEmpty()) {
                String ggl = String.format(gglFormat, tableName, String.join(",", uniqueColumns));
                ApiPagedResult<List<Map<String, Object>>> page = ruleService.queryForMapList(ggl, false);
                Map<String, Set<Object>> redisValue = pageResultToMap(page, uniqueColumns);
                redisTemplate.opsForValue().set(key, redisValue, REDIS_TIME_OUT, TimeUnit.MINUTES);
            }
        } catch (Exception ex) {
            log.info(ex.getMessage(), ex);
        }

        return uniqueKeys;
    }

    private Map<String, Set<Object>> pageResultToMap(ApiPagedResult<?> page, Set<String> uniqueColumns) {
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
     * <p>
     * 根据提供的业务数据和清洗规则，对业务数据进行处理。
     *
     * @param currentUUID             当前操作的唯一标识符
     * @param businessDataMapList     业务数据列表，每个元素是一个包含业务数据键值对的映射
     * @param businessTypeRuleDataSet 清洗规则集合，每个元素是一个包含业务类型规则数据的映射
     * @return 返回处理后的业务数据列表，每个元素是一个包含业务数据键值对的映射
     */
    public List<Map<String, BusinessData>> handleBusinessDataRules(String currentUUID, List<Map<String, BusinessData>> businessDataMapList, Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet) {
        if (businessDataMapList == null || businessDataMapList.isEmpty() || businessTypeRuleDataSet == null || businessTypeRuleDataSet.isEmpty()) {
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
        if (businessDataMapList == null || businessDataMapList.isEmpty()) {
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
                        if (columnNames == null || columnNames.isEmpty()) {
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
        List<Map<String, BusinessData>> handleDataMapList;
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
                                if (multiValue.length > 0) {
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
                                        maxLength = Math.max(maxLength, multiValue.length);
                                        dataValues.add(String.join(",", multiValue));
                                    }
                                }
                            } catch (Exception ex) {
                                log.error(ex.getMessage(), ex);
                            }
                        } else {
                            businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                        }
                    }
                }
            }
            if (!isMulti) {
                BusinessData data = new BusinessData();
                assert businessData != null;
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
                    data.addAllErrorMsg(businessData.getErrorMsg());
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
                    businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeReplace()) {
                if (Strings.isNotBlank(ruleData.getRule()) && Strings.isNotBlank(ruleData.getGoal())) {
                    newValue = Strings.isNotBlank(oldValue) ? oldValue.replaceAll(ruleData.getRule(), ruleData.getGoal()) : "";
                    newValue = Strings.isNotBlank(String.valueOf(newValue)) ? newValue : null;
                } else {
                    businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule or Goal is empty！");
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
                    businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeCheckBox()) {
                if (Strings.isNotBlank(ruleData.getRule())) {
                    Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && !redisValues.isEmpty()) {
                        String[] oValues = oldValue.split(",");
                        if (oValues.length > 0) {
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
                    businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeDictionary()) {
                if (Strings.isNotBlank(ruleData.getRule())) {
                    Map<String, String> redisValues = (Map<String, String>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && !redisValues.isEmpty()) {
                        newValue = redisValues.get(oldValue);
                    }
                } else {
                    businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is empty！");
                }
            } else if (ruleData.isRuleTypeQueryGoal()) {
                String tableName = ruleData.getQueryRuleTable();
                List<String> colNames = ruleData.getQueryRuleColumn();
                if (Strings.isNotBlank(tableName) && colNames != null && !colNames.isEmpty() && Strings.isNotBlank(ruleData.getGoal())) {
                    Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && !redisValues.isEmpty()) {
                        newValue = redisValues.get(oldValue);
                    }
                } else {
                    businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                }
            } else if (ruleData.isRuleTypeQueryRule()) {
                String tableName = ruleData.getQueryRuleTable();
                List<String> colNames = ruleData.getQueryRuleColumn();
                if (Strings.isNotBlank(tableName) && colNames != null && !colNames.isEmpty() && Strings.isNotBlank(ruleData.getGoal())) {
                    Map<String, Object> redisValues = (Map<String, Object>) redisTemplate.opsForValue().get(String.format("%s:%s", currentUUID, ruleData.getRule()));
                    if (Strings.isNotBlank(oldValue) && redisValues != null && !redisValues.isEmpty()) {
                        newValue = redisValues.get(oldValue);
                    }
                } else {
                    businessData.addErrorMsg("Rule resolution failure。[" + ruleData.getType() + "] Rule is Error Or Goal is Empty！");
                }
            }
            newValue = (ruleData.isRetain() && newValue == null) ? businessData.getValue() : newValue;
            if (businessData.getValue() == null || !businessData.getValue().equals(newValue)) {
                businessData.setValue(newValue);
                businessData.setTransitionValue(newValue);
            }
        }
    }

    private List<String> setTypeRuleCache(String currentUUID, Set<Map<Integer, BusinessTypeRuleData>> businessTypeRuleDataSet) {
        // 类型
        Map<String, BusinessTypeRuleData> ruleDataDict = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataGoal = new HashMap<>();
        Map<String, BusinessTypeRuleData> ruleDataRule = new HashMap<>();
        // 数据解析
        if (businessTypeRuleDataSet != null && !businessTypeRuleDataSet.isEmpty()) {
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
                            if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && !columnNames.isEmpty()) {
                                String key = String.format("%s:%s,%s", currentUUID, ruleData.getRule(), ruleData.getGoal());
                                if (!ruleDataGoal.containsKey(key)) {
                                    ruleDataGoal.put(key, ruleData);
                                }
                            }
                        } else if (ruleData.isRuleTypeQueryRule()) {
                            String tableName = ruleData.getQueryRuleTable();
                            List<String> columnNames = ruleData.getQueryRuleColumn();
                            if (Strings.isNotBlank(ruleData.getGoal()) && Strings.isNotBlank(tableName) && columnNames != null && !columnNames.isEmpty()) {
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
        List<String> cacheList = new ArrayList<>(dictKeys);
        // 目标字段查询
        List<String> goalRedis = setQueryRuleRedis(ruleDataGoal);
        cacheList.addAll(goalRedis);
        // 规则字段查询
        List<String> ruleRedis = setQueryRuleRedis(ruleDataRule);
        cacheList.addAll(ruleRedis);

        return cacheList;
    }

}
