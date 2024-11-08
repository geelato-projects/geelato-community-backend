package cn.geelato.web.platform.m;

import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.orm.Dao;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.m.base.service.RuleService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
        Map<String, Object> params = this.getQueryParameters(elementType, this.request);
        return this.getFilterGroup(params, operatorMap);
    }

    @Deprecated
    public FilterGroup getFilterGroup(Class elementType, HttpServletRequest request, Map<String, List<String>> operatorMap) throws ParseException {
        Map<String, Object> params = this.getQueryParameters(elementType, request);
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


    @Override
    public void afterPropertiesSet() throws Exception {
    }
}
