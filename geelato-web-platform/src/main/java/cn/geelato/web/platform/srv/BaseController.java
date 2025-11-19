package cn.geelato.web.platform.srv;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.core.orm.Dao;
import cn.geelato.datasource.DynamicDataSourceHolder;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.github.pagehelper.PageHelper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.util.Strings;
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
public class BaseController extends ParameterOperator {

    private final SimpleDateFormat SDF_DATE = new SimpleDateFormat(DateUtils.DATE);
    private final SimpleDateFormat SDF_DATE_START = new SimpleDateFormat(DateUtils.DATESTART);
    private final SimpleDateFormat SDF_DATE_FINISH = new SimpleDateFormat(DateUtils.DATEFINISH);

    @Autowired
    @Qualifier("primaryDao")
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
     * 支持多种格式的请求头参数，优先级为 appId > AppId > App-Id
     */
    public String getAppId() {
        if (this.request != null) {
            String appId = this.request.getHeader("appId");
            if (appId != null) {
                return appId;
            }
            
            appId = this.request.getHeader("AppId");
            if (appId != null) {
                return appId;
            }
            
            return this.request.getHeader("App-Id");
        }
        return null;
    }

    /**
     * 获取请求中的或上下文中的租户代码
     * 支持多种格式的请求头参数，优先级为 tenantCode > TenantCode > Tenant-Code
     */
    public String getTenantCode() {
        String tenantCode = null;
        if (this.request != null) {
            tenantCode = this.request.getHeader("tenantCode");
            if (tenantCode != null) {
                return tenantCode;
            }
            
            tenantCode = this.request.getHeader("TenantCode");
            if (tenantCode != null) {
                return tenantCode;
            }
            
            tenantCode = this.request.getHeader("Tenant-Code");
        }
        return Strings.isNotBlank(tenantCode) ? tenantCode : SessionCtx.getCurrentTenantCode();
    }

    /**
     * 获取请求头参数
     * @param headerName 请求头名称
     * @return 请求头值
     */
    protected String getHeader(String headerName) {
        if (this.request != null) {
            return this.request.getHeader(headerName);
        }
        return null;
    }

    /**
     * 切换数据库
     *
     * @param connectId 数据库连接ID
     */
    public void switchDbByConnectId(String connectId) {
        if (Strings.isBlank(connectId)) {
            throw new IllegalArgumentException("数据连接不能为空");
        }
        DynamicDataSourceHolder.setDataSourceKey(connectId);
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
     * 根据请求体快速构建查询参数（从FilterGroup提取有效字段到Map）。
     *
     * @param elementType 元素类型
     * @param requestBodyMap 请求体参数
     * @param isOperation 是否为操作参数（启用数据权限）
     * @return 查询参数Map
     * @throws ParseException 解析异常
     */
    protected Map<String, Object> buildQueryParams(Class elementType, Map<String, Object> requestBodyMap, boolean isOperation) throws ParseException {
        FilterGroup filterGroup = this.getFilterGroup(elementType, requestBodyMap, isOperation);
        return toParams(filterGroup);
    }

    /**
     * 将FilterGroup转换为简单的参数Map（仅包含非空的字段和值）。
     *
     * @param filterGroup 过滤条件组
     * @return 查询参数Map
     */
    protected Map<String, Object> toParams(FilterGroup filterGroup) {
        Map<String, Object> params = new HashMap<>();
        if (filterGroup != null && filterGroup.getFilters() != null) {
            for (FilterGroup.Filter filter : filterGroup.getFilters()) {
                if (filter.getValue() != null && !filter.getValue().trim().isEmpty()) {
                    params.put(filter.getField(), filter.getValue());
                }
            }
        }
        return params;
    }
    
    /**
     * 启动分页查询
     * 直接调用 PageHelper.startPage 方法，使用从请求中获取的分页参数
     */
    protected void startPage() {
        PageHelper.startPage(getPageNum(), getPageSize());
    }
    
    /**
     * 获取当前页码
     * 
     * @return 当前页码
     */
    protected int getPageNum() {
        Map<String, Object> requestBodyMap = getRequestBody();
        PageQueryRequest pageRequest = getPageQueryParameters(requestBodyMap);
        return pageRequest.getPageNum();
    }
    
    /**
     * 获取每页大小
     * 
     * @return 每页大小
     */
    protected int getPageSize() {
        Map<String, Object> requestBodyMap = getRequestBody();
        PageQueryRequest pageRequest = getPageQueryParameters(requestBodyMap);
        return pageRequest.getPageSize();
    }

    /**
     * 获取指定元素类型的过滤组。
     *
     * @param elementType 元素类型
     * @return 指定元素类型的过滤组
     * @throws ParseException 如果解析过程中发生错误，则抛出该异常
     */
    /**
     * 将请求参数转换为FilterGroup对象
     * 
     * @param params 请求参数
     * @return FilterGroup对象
     * @throws ParseException 解析异常
     */
    private FilterGroup getFilterGroup(Map<String, Object> params) throws ParseException {
        FilterGroup filterGroup = new FilterGroup();
        if (params == null || params.isEmpty()) {
            return filterGroup;
        }

        for (Map.Entry<String, Object> entry : params.entrySet()) {
            Map<String, String> keyMap = this.formatMapKey(entry.getKey());
            String key = keyMap.get("key");
            if (Strings.isBlank(key)) {
                throw new RuntimeException("不支持的查询条件：" + entry.getKey());
            }
            
            String operator = keyMap.get("operator");
            Object value = entry.getValue();
            
            // 处理空值情况
            if (value == null || Strings.isBlank(value.toString())) {
                if (FilterGroup.Operator.nil.getText().equals(operator)) {
                    filterGroup.addFilter(key, FilterGroup.Operator.nil, "1");
                }
                continue;
            }
            
            // 处理有操作符的情况
            if (Strings.isNotBlank(operator)) {
                processOperatorFilter(filterGroup, key, operator, value);
            } else {
                // 无操作符，使用默认等于操作符
                filterGroup.addFilter(key, value.toString());
            }
        }

        return filterGroup;
    }
    
    /**
     * 处理带操作符的过滤条件
     * 
     * @param filterGroup 过滤组
     * @param key 字段名
     * @param operator 操作符
     * @param value 值
     */
    private void processOperatorFilter(FilterGroup filterGroup, String key, String operator, Object value) {
        // 处理区间操作符
        if (FilterGroup.Operator.bt.getText().equals(operator)) {
            processBetweenOperator(filterGroup, key, value);
            return;
        }
        
        // 处理空值操作符
        if (FilterGroup.Operator.nil.getText().equals(operator)) {
            String filterValue = Strings.isNotBlank(value.toString()) ? "1" : null;
            filterGroup.addFilter(key, FilterGroup.Operator.nil, filterValue);
            return;
        }
        
        // 处理其他操作符
        boolean isValidOperator = false;
        for (FilterGroup.Operator op : FilterGroup.Operator.values()) {
            if (op.getText().equals(operator)) {
                filterGroup.addFilter(key, op, value.toString());
                isValidOperator = true;
                break;
            }
        }
        
        if (!isValidOperator) {
            throw new RuntimeException("不支持的运算符：" + operator);
        }
    }
    
    /**
     * 处理区间操作符
     * 
     * @param filterGroup 过滤组
     * @param key 字段名
     * @param value 值
     */
    private void processBetweenOperator(FilterGroup filterGroup, String key, Object value) {
        if (value instanceof String) {
            String[] values = value.toString().split(",");
            if (values.length == 2 && Strings.isNotBlank(values[0]) && Strings.isNotBlank(values[1])) {
                filterGroup.addFilter(key, FilterGroup.Operator.bt, JSON.toJSONString(values));
            }
        } else if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            if (values.size() == 2 && Strings.isNotBlank(values.getString(0)) && Strings.isNotBlank(values.getString(1))) {
                filterGroup.addFilter(key, FilterGroup.Operator.bt, JSON.toJSONString(value));
            }
        }
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

}
