package cn.geelato.web.platform.srv;

import cn.geelato.core.mql.parser.InvalidPageParamException;
import cn.geelato.core.mql.parser.PageQueryRequest;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;

@SuppressWarnings("rawtypes")
public class ParameterOperator extends RequestOperator {
    protected static final String OPERATOR_SEPARATOR = "|";

    protected final int DEFAULT_PAGE_NUM = 1;
    protected final int DEFAULT_PAGE_SIZE = 10;
    protected static final String DEFAULT_ORDER_BY = "seq_no ASC,update_at DESC";

    /**
     * 分页参数兼容别名，规范名为首位，历史名按序兜底：
     * 页码 pageNum / current（旧 POST body）/ page；页大小 pageSize / limit；排序 orderBy / order（旧 POST body）。
     */
    private static final String[] PAGE_NUM_PARAM_NAMES = {"pageNum", "current", "page"};
    private static final String[] PAGE_SIZE_PARAM_NAMES = {"pageSize", "limit"};
    private static final String[] ORDER_BY_PARAM_NAMES = {"orderBy", "order"};
    /** 单页大小上限，超出直接拒绝（硬失败），防止恶意超大页拖垮查询。 */
    private static final int MAX_PAGE_SIZE = 1000;


    protected Map<String, Object> getQueryParameters(Class elementType) {
        return getQueryParameters(elementType, false);
    }

    protected Map<String, Object> getQueryParameters(Class elementType, boolean isOperation) {
        return getQueryParameters(elementType, this.request, isOperation);
    }

    @Deprecated
    protected Map<String, Object> getQueryParameters(Class elementType, HttpServletRequest request, boolean isOperation) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Set<String> fieldNames = getClassFieldNames(elementType);
            String key = getParameterMapKey(entry.getKey(), isOperation);
            if (fieldNames.contains(key)) {
                List<String> values = List.of(entry.getValue());
                if (values.size() == 1) {
                    queryParamsMap.put(entry.getKey(), values.get(0));
                } else {
                    queryParamsMap.put(entry.getKey(), values.toArray(new String[0]));
                }
            }
        }
        return queryParamsMap;
    }

    protected Map<String, Object> getQueryParameters() {
        return getQueryParameters(this.request);
    }

    @Deprecated
    protected Map<String, Object> getQueryParameters(HttpServletRequest request) {
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

    protected PageQueryRequest getPageQueryParameters(String defaultOrder) {
        return getPageQueryParameters(this.request, defaultOrder);
    }

    protected PageQueryRequest getPageQueryParameters(HttpServletRequest request, String defaultOrder) {
        PageQueryRequest pageQueryRequest = getPageQueryParameters(request);
        if (Strings.isBlank(pageQueryRequest.getOrderBy())) {
            pageQueryRequest.setOrderBy(defaultOrder);
        }
        return pageQueryRequest;
    }


    protected PageQueryRequest getPageQueryParameters() {
        return getPageQueryParameters(this.request);
    }

    @Deprecated
    protected PageQueryRequest getPageQueryParameters(HttpServletRequest request) {
        // 未传分页参数时保持 pageNum=-1：QueryCommand.hasPagination() 以 >0 判定，-1 即不分页全量查询
        PageQueryRequest queryRequest = new PageQueryRequest();
        String[] pageNum = findParam(request, PAGE_NUM_PARAM_NAMES);
        queryRequest.setPageNum(pageNum == null ? -1 : requirePageNum(pageNum));
        String[] pageSize = findParam(request, PAGE_SIZE_PARAM_NAMES);
        queryRequest.setPageSize(pageSize == null ? -1 : requirePageSize(pageSize));
        String[] orderBy = findParam(request, ORDER_BY_PARAM_NAMES);
        queryRequest.setOrderBy(normalizeOrderBy(orderBy == null ? null : orderBy[1]));

        return queryRequest;
    }

    protected Map getRequestBody() {
        Map requestBodyMap;
        try {
            StringBuilder stringBuilder = new StringBuilder();
            String str;
            try (BufferedReader br = request.getReader()) {
                if (br != null) {
                    while ((str = br.readLine()) != null) {
                        stringBuilder.append(str);
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException("无法读取消息体");
            }
            String requestBody = stringBuilder.toString();
            requestBodyMap = JSON.parseObject(requestBody, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return requestBodyMap;
    }

    protected PageQueryRequest getPageQueryParameters(Map<String, Object> requestBodyMap) {
        PageQueryRequest queryRequest = new PageQueryRequest();
        if (requestBodyMap != null) {
            String[] pageNum = findParam(requestBodyMap, PAGE_NUM_PARAM_NAMES);
            queryRequest.setPageNum(pageNum == null ? DEFAULT_PAGE_NUM : requirePageNum(pageNum));
            String[] pageSize = findParam(requestBodyMap, PAGE_SIZE_PARAM_NAMES);
            queryRequest.setPageSize(pageSize == null ? DEFAULT_PAGE_SIZE : requirePageSize(pageSize));
            String[] orderBy = findParam(requestBodyMap, ORDER_BY_PARAM_NAMES);
            queryRequest.setOrderBy(normalizeOrderBy(orderBy == null ? null : orderBy[1]));
        }
        return queryRequest;
    }

    protected PageQueryRequest getPageQueryParameters(Map<String, Object> requestBodyMap, String defaultOrder) {
        PageQueryRequest pageQueryRequest = getPageQueryParameters(requestBodyMap);
        if (Strings.isBlank(pageQueryRequest.getOrderBy())) {
            pageQueryRequest.setOrderBy(defaultOrder);
        }
        return pageQueryRequest;
    }

    /**
     * 按声明顺序返回首个出现且非空的参数（[0]=参数名，[1]=参数值），用于别名解析与错误提示。
     */
    private String[] findParam(Map<String, Object> params, String[] names) {
        for (String name : names) {
            Object value = params.get(name);
            if (value != null && Strings.isNotBlank(value.toString())) {
                return new String[]{name, value.toString().trim()};
            }
        }
        return null;
    }

    private String[] findParam(HttpServletRequest request, String[] names) {
        for (String name : names) {
            String value = request.getParameter(name);
            if (Strings.isNotBlank(value)) {
                return new String[]{name, value.trim()};
            }
        }
        return null;
    }

    private int requirePageNum(String[] pageNum) {
        int value = parseInt(pageNum);
        if (value < 1) {
            throw new InvalidPageParamException(String.format("分页参数 %s=%s 必须为正整数", pageNum[0], pageNum[1]));
        }
        return value;
    }

    private int requirePageSize(String[] pageSize) {
        int value = parseInt(pageSize);
        if (value < 1) {
            throw new InvalidPageParamException(String.format("分页参数 %s=%s 必须为正整数", pageSize[0], pageSize[1]));
        }
        if (value > MAX_PAGE_SIZE) {
            throw new InvalidPageParamException(String.format("分页参数 %s=%s 超出上限 %d", pageSize[0], pageSize[1], MAX_PAGE_SIZE));
        }
        return value;
    }

    private int parseInt(String[] param) {
        try {
            return Integer.parseInt(param[1]);
        } catch (NumberFormatException e) {
            throw new InvalidPageParamException(String.format("分页参数 %s=%s 不是有效整数", param[0], param[1]));
        }
    }

    private String normalizeOrderBy(String orderBy) {
        return orderBy == null ? "" : orderBy.replaceAll("\\|", " ").trim();
    }

    @Deprecated
    protected Map<String, Object> getQueryParameters(Class elementType, Map<String, Object> requestBodyMap, boolean isOperation) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        if (requestBodyMap != null) {
            for (Map.Entry<String, Object> entry : requestBodyMap.entrySet()) {
                Set<String> fieldNames = getClassFieldNames(elementType);
                String key = getParameterMapKey(entry.getKey(), isOperation);
                if (fieldNames.contains(key)) {
                    queryParamsMap.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return queryParamsMap;
    }

    private Set<String> getClassFieldNames(Class elementType) {
        Set<String> fieldNameList = new HashSet<>();
        List<Field> fieldsList = getClassFields(elementType);
        for (Field field : fieldsList) {
            fieldNameList.add(field.getName());
        }
        return fieldNameList;
    }

    private List<Field> getClassFields(Class elementType) {
        List<Field> fieldsList = new ArrayList<>();
        while (elementType != null) {
            Field[] declaredFields = elementType.getDeclaredFields();
            fieldsList.addAll(Arrays.asList(declaredFields));
            elementType = elementType.getSuperclass();
        }
        return fieldsList;
    }

    private String getParameterMapKey(String key, boolean isOperation) {
        if (isOperation && Strings.isNotBlank(key) && key.contains(OPERATOR_SEPARATOR)) {
            return key.substring(0, key.lastIndexOf(OPERATOR_SEPARATOR));
        }
        return key;
    }
}
