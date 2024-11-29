package cn.geelato.web.platform.m;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.m.base.service.RuleService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author geemeta
 */
@SuppressWarnings("all")
public class BaseController extends ParameterOperator implements InitializingBean {

    private final SimpleDateFormat SDF_DATE = new SimpleDateFormat(DateUtils.DATE);
    private final SimpleDateFormat SDF_DATE_START = new SimpleDateFormat(DateUtils.DATESTART);
    private final SimpleDateFormat SDF_DATE_FINISH = new SimpleDateFormat(DateUtils.DATEFINISH);
    protected Dao dao;
    protected RuleService ruleService;
    protected HttpServletResponse response;

    @Autowired
    protected void setHttpServletRequest(HttpServletRequest httpServletRequest) {
        this.request = httpServletRequest;
    }

    @Autowired
    protected void setDao(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    @Autowired
    protected void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /**
     * 在每个子类方法调用之前先调用
     * 设置request,response,session这三个对象
     */
    @ModelAttribute
    public void setReqAndRes(HttpServletRequest request, HttpServletResponse response) {
        this.response = response;
    }


    /**
     * 根据接口传递的参数，构建查询条件
     */
    public FilterGroup getFilterGroup(Class elementType, Map<String, List<String>> operatorMap) throws ParseException {
        return this.getFilterGroup(elementType, this.request, operatorMap);
    }

    @Deprecated
    public FilterGroup getFilterGroup(Class elementType, HttpServletRequest request, Map<String, List<String>> operatorMap) throws ParseException {
        Map<String, Object> params = this.getQueryParameters(elementType, request, false);
        return this.getFilterGroup(params, operatorMap);
    }

    /**
     * 构建查询条件
     */
    private FilterGroup getFilterGroup(Map<String, Object> params, Map<String, List<String>> operatorMap) throws ParseException {
        FilterGroup filterGroup = new FilterGroup();
        if (params != null && !params.isEmpty()) {
            if (operatorMap != null && !operatorMap.isEmpty()) {
                // 排除查询
                List<String> excludes = operatorMap.get("excludes");
                if (excludes != null && !excludes.isEmpty()) {
                    for (String list : excludes) {
                        if (params.get(list) != null) {
                            params.remove(list);
                        }
                    }
                }
                // 不等于查询
                List<String> isNulls = operatorMap.get("isNulls");
                if (isNulls != null && !isNulls.isEmpty()) {
                    for (String list : isNulls) {
                        Object value = params.get(list);
                        if (value != null && "NULL".equalsIgnoreCase(String.valueOf(value))) {
                            filterGroup.addFilter(list, FilterGroup.Operator.nil, String.valueOf(1));
                            params.remove(list);
                        }
                    }
                }
                // 模糊查询
                List<String> contains = operatorMap.get("contains");
                if (contains != null && !contains.isEmpty()) {
                    for (String list : contains) {
                        if (params.get(list) != null && Strings.isNotBlank(String.valueOf(params.get(list)))) {
                            filterGroup.addFilter(list, FilterGroup.Operator.contains, String.valueOf(params.get(list)));
                            params.remove(list);
                        }
                    }
                }
                // 存在于列表查询
                List<String> consists = operatorMap.get("consists");
                if (consists != null && !consists.isEmpty()) {
                    for (String list : consists) {
                        if (params.get(list) != null && Strings.isNotBlank(String.valueOf(params.get(list)))) {
                            filterGroup.addFilter(list, FilterGroup.Operator.in, String.valueOf(params.get(list)));
                            params.remove(list);
                        }
                    }
                }
                // 时间查询
                List<String> intervals = operatorMap.get("intervals");
                if (intervals != null && !intervals.isEmpty()) {
                    for (String list : intervals) {
                        Object value = params.get(list);
                        if (value == null || Strings.isBlank(String.valueOf(value))) {
                            continue;
                        }
                        String[] times = null;
                        // 2024-08-01,2024-08-08; 2024-08-01,; ,2024-08-08
                        if (value instanceof String) {
                            times = String.valueOf(value).split(",");
                            if (times.length == 2) {
                                if (Strings.isNotBlank(times[0])) {
                                    filterGroup.addFilter(list, FilterGroup.Operator.gte, SDF_DATE_START.format(SDF_DATE.parse(times[0])));
                                }
                                if (Strings.isNotBlank(times[1])) {
                                    filterGroup.addFilter(list, FilterGroup.Operator.lte, SDF_DATE_FINISH.format(SDF_DATE.parse(times[1])));
                                }
                                params.remove(list);
                            }
                        }
                        if (value.getClass().isArray()) {
                            times = (String[]) value;
                            if (times.length == 2 && Strings.isNotBlank(times[0]) && Strings.isNotBlank(times[1])) {
                                filterGroup.addFilter(list, FilterGroup.Operator.gte, SDF_DATE_START.format(SDF_DATE.parse(times[0])));
                                filterGroup.addFilter(list, FilterGroup.Operator.lte, SDF_DATE_FINISH.format(SDF_DATE.parse(times[1])));
                                params.remove(list);
                            }
                        }
                    }
                }
            }
            // 对等查询
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (entry.getValue() != null && Strings.isNotBlank(entry.getValue().toString())) {
                    filterGroup.addFilter(entry.getKey(), entry.getValue().toString());
                }
            }
        }

        return filterGroup;
    }

    /**
     * 获取指定元素类型的过滤组。
     *
     * @param elementType 元素类型
     * @return 指定元素类型的过滤组
     * @throws ParseException 如果解析过程中发生错误，则抛出该异常
     */
    public FilterGroup getFilterGroup(Class elementType) throws ParseException {
        return this.getFilterGroup(elementType, this.request);
    }

    @Deprecated
    public FilterGroup getFilterGroup(Class elementType, HttpServletRequest request) throws ParseException {
        Map<String, Object> params = this.getQueryParameters(elementType, request, true);
        return this.getFilterGroup(params);
    }

    public FilterGroup getFilterGroup(Class elementType, Map<String, Object> requestBodyMap, boolean isOperation) throws ParseException {
        Map<String, Object> params = this.getQueryParameters(elementType, requestBodyMap, isOperation);
        return this.getFilterGroup(params);
    }

    /**
     * 构建查询条件
     */
    private FilterGroup getFilterGroup(Map<String, Object> params) throws ParseException {
        FilterGroup filterGroup = new FilterGroup();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                Map<String, String> keyMap = this.formatMapKey(entry.getKey());
                String key = keyMap.get("key");
                if (Strings.isBlank(key)) {
                    throw new RuntimeException("不支持的查询条件：" + entry.getKey());
                }
                String operator = keyMap.get("operator");
                // value 不为空，“”也算空
                if (entry.getValue() == null || Strings.isBlank(entry.getValue().toString())) {
                    continue;
                }
                if (Strings.isNotBlank(operator)) {
                    if (FilterGroup.Operator.bt.getText().equals(operator)) {
                        if (entry.getValue() instanceof String) {
                            String[] values = entry.getValue().toString().split(",");
                            if (values.length == 2 && Strings.isNotBlank(values[0]) && Strings.isNotBlank(values[1])) {
                                filterGroup.addFilter(key, FilterGroup.Operator.bt, JSON.toJSONString(values));
                            }
                        } else if (entry.getValue() instanceof JSONArray) {
                            JSONArray values = (JSONArray) entry.getValue();
                            if (values.size() == 2 && Strings.isNotBlank(values.getString(0)) && Strings.isNotBlank(values.getString(1))) {
                                filterGroup.addFilter(key, FilterGroup.Operator.bt, JSON.toJSONString(entry.getValue()));
                            }
                        }
                    } else if (FilterGroup.Operator.nil.getText().equals(operator)) {
                        filterGroup.addFilter(key, FilterGroup.Operator.nil, Strings.isNotBlank(entry.getValue().toString()) ? "1" : null);
                    } else {
                        boolean isOp = false;
                        for (FilterGroup.Operator op : FilterGroup.Operator.values()) {
                            if (op.getText().equals(operator)) {
                                filterGroup.addFilter(key, op, entry.getValue().toString());
                                isOp = true;
                                break;
                            }
                        }
                        if (!isOp) {
                            throw new RuntimeException("不支持的运算符：" + operator);
                        }
                    }
                } else {
                    filterGroup.addFilter(key, entry.getValue().toString());
                }
            }
        }

        return filterGroup;
    }

    private Map<String, String> formatMapKey(String key) {
        Map<String, String> map = new HashMap<>();
        map.put("key", key);
        if (Strings.isNotBlank(key) && key.contains(ParameterOperator.OPERATOR_SEPARATOR)) {
            int index = key.lastIndexOf(ParameterOperator.OPERATOR_SEPARATOR);
            map.put("key", key.substring(0, index));
            if (key.length() > index + 1) {
                map.put("operator", key.substring(index + 1));
            }
        }
        return map;
    }


    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
