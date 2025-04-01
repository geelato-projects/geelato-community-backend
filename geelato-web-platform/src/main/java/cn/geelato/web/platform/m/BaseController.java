package cn.geelato.web.platform.m;

import cn.geelato.core.SessionCtx;
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

    /**
     * 通过Spring框架的@Autowired注解自动注入HttpServletRequest对象
     * 这个方法主要用于设置HttpServletRequest对象，以便在类内部使用
     *
     * @param httpServletRequest Servlet请求对象，用于获取请求相关的信息
     */
    @Autowired
    protected void setHttpServletRequest(HttpServletRequest httpServletRequest) {
        this.request = httpServletRequest;
    }

    /**
     * 通过Spring框架的@Autowired注解自动注入Dao对象
     * 这个方法主要用于设置Dao对象，以便在类内部使用
     *
     * @param dao 数据访问对象，用于执行数据库操作
     */
    @Autowired
    protected void setDao(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    /**
     * 通过Spring框架的@Autowired注解自动注入RuleService对象
     * 这个方法主要用于设置RuleService对象，以便在类内部使用
     *
     * @param ruleService 规则服务对象，用于执行规则操作
     */
    @Autowired
    protected void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /**
     * 通过Spring框架的@ModelAttribute注解自动注入HttpServletResponse对象
     * 这个方法主要用于设置HttpServletResponse对象，以便在类内部使用
     *
     * @param response Servlet响应对象，用于获取响应相关的信息
     */
    @ModelAttribute
    public void setReqAndRes(HttpServletRequest request, HttpServletResponse response) {
        this.response = response;
    }

    /**
     * 获取请求中的应用ID
     */
    public String getAppId() {
        if (this.request != null) {
            return this.request.getHeader("App-Id");
        }
        return null;
    }

    /**
     * 获取请求中的或上下文中的租户代码
     */
    public String getTenantCode() {
        String tenantCode = null;
        if (this.request != null) {
            return this.request.getHeader("Tenant-Code");
        }
        return Strings.isNotBlank(tenantCode) ? tenantCode : SessionCtx.getCurrentTenantCode();
    }

    /**
     * 获取指定元素类型的过滤组。
     *
     * @param elementType 元素类型
     * @return 指定元素类型的过滤组
     * @throws ParseException 如果解析过程中发生错误，则抛出该异常
     */
    public FilterGroup getFilterGroup(Class elementType) throws ParseException {
        Map<String, Object> params = this.getQueryParameters(elementType, this.request, true);
        return this.getFilterGroup(params);
    }

    /**
     * 获取指定元素类型的过滤组，并设置请求体参数。
     *
     * @param elementType    元素类型
     * @param requestBodyMap 请求体参数
     * @param isOperation    是否为操作参数
     * @return 指定元素类型的过滤组
     * @throws ParseException 如果解析过程中发生错误，则抛出该异常
     */
    public FilterGroup getFilterGroup(Class elementType, Map<String, Object> requestBodyMap, boolean isOperation) throws ParseException {
        Map<String, Object> params = this.getQueryParameters(elementType, requestBodyMap, isOperation);
        return this.getFilterGroup(params);
    }

    /**
     * 获取指定元素类型的过滤组。
     *
     * @param elementType 元素类型
     * @return 指定元素类型的过滤组
     * @throws ParseException 如果解析过程中发生错误，则抛出该异常
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
                    // nil为null
                    if (FilterGroup.Operator.nil.getText().equals(operator)) {
                        filterGroup.addFilter(key, FilterGroup.Operator.nil, "1");
                    }
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

    /**
     * 格式化查询参数的键。
     *
     * @param key 查询参数的键
     * @return 格式化后的查询参数键
     */
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
