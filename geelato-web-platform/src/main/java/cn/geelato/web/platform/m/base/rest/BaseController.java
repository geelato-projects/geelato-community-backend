package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.gql.parser.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.orm.Dao;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.m.base.service.RuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.lang.reflect.Field;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


/**
 * @author geemeta
 */
@SuppressWarnings("rawtypes")
public class BaseController implements InitializingBean {

    private final SimpleDateFormat SDF_DATE = new SimpleDateFormat(DateUtils.DATE);
    private final SimpleDateFormat SDF_DATE_START = new SimpleDateFormat(DateUtils.DATESTART);
    private final SimpleDateFormat SDF_DATE_FINISH = new SimpleDateFormat(DateUtils.DATEFINISH);
    protected Dao dao;
    protected RuleService ruleService;
    /**
     * 创建session、Request、Response等对象
     */
    protected HttpServletRequest request;
    protected HttpServletResponse response;
    protected HttpSession session;

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
        this.request = request;
        this.response = response;
        this.session = request.getSession(true);
    }

    /**
     * 构建分页查询条件，设置默认排序
     *
     */
    public PageQueryRequest getPageQueryParameters(HttpServletRequest request, String defaultOrder) {
        PageQueryRequest pageQueryRequest = getPageQueryParameters(request);
        if (Strings.isNotBlank(pageQueryRequest.getOrderBy())) {
            pageQueryRequest.setOrderBy(defaultOrder);
        }
        return pageQueryRequest;
    }

    /**
     * 构建分页查询条件
     *
     */
    public PageQueryRequest getPageQueryParameters(HttpServletRequest request) {
        PageQueryRequest queryRequest = new PageQueryRequest();
        int pageNum = Strings.isNotBlank(request.getParameter("current")) ? Integer.parseInt(request.getParameter("current")) : -1;
        queryRequest.setPageNum(pageNum);
        int pageSize = Strings.isNotBlank(request.getParameter("pageSize")) ? Integer.parseInt(request.getParameter("pageSize")) : -1;
        queryRequest.setPageSize(pageSize);
        String orderBy = Strings.isNotBlank(request.getParameter("order")) ? String.valueOf(request.getParameter("order")) : "";
        orderBy = orderBy.replaceAll("\\|", " ");
        queryRequest.setOrderBy(orderBy);

        return queryRequest;
    }

    /**
     * 根据接口传递的参数，构建查询条件
     *
     */
    public FilterGroup getFilterGroup(Class elementType, HttpServletRequest request, Map<String, List<String>> operatorMap) throws ParseException {
        Map<String, Object> params = this.getQueryParameters(elementType, request);
        return this.getFilterGroup(params, operatorMap);
    }

    /**
     * 构建查询条件
     */
    public FilterGroup getFilterGroup(Map<String, Object> params, Map<String, List<String>> operatorMap) throws ParseException {
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
     * 获取接口参数，根据对象清理
     *
     */
    public Map<String, Object> getQueryParameters(Class elementType, HttpServletRequest request) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Set<String> fieldNames = getClassFieldNames(elementType);
            if (fieldNames.contains(entry.getKey())) {
                List<String> values = List.of(entry.getValue());
                if (values.size() == 1) {
                    queryParamsMap.put(entry.getKey(), values.get(0));
                } else {
                    queryParamsMap.put(entry.getKey(), values.toArray(new String[values.size()]));
                }
            }
        }


        return queryParamsMap;
    }

    /**
     * 获取接口参数
     *
     */
    public Map<String, Object> getQueryParameters(HttpServletRequest request) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            List<String> values = List.of(entry.getValue());
            if (values.size() == 1) {
                queryParamsMap.put(entry.getKey(), values.get(0));
            } else {
                queryParamsMap.put(entry.getKey(), values.toArray(new String[0]));
            }
        }

        return queryParamsMap;
    }

    /**
     * 获取对象拥有的属性
     *
     */
    private Set<String> getClassFieldNames(Class elementType) {
        Set<String> fieldNameList = new HashSet<>();
        List<Field> fieldsList = getClassFields(elementType);
        for (Field field : fieldsList) {
            fieldNameList.add(field.getName());
        }
        return fieldNameList;
    }

    /**
     * 获取对象拥有的属性
     *
     */
    private List<Field> getClassFields(Class elementType) {
        List<Field> fieldsList = new ArrayList<>();
        while (elementType != null) {
            Field[] declaredFields = elementType.getDeclaredFields();
            fieldsList.addAll(Arrays.asList(declaredFields));
            elementType = elementType.getSuperclass();
        }

        return fieldsList;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
