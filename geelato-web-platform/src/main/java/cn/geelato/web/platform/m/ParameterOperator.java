package cn.geelato.web.platform.m;

import cn.geelato.core.gql.parser.PageQueryRequest;
import cn.geelato.web.platform.utils.GqlUtil;
import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;

import java.lang.reflect.Field;
import java.util.*;

@SuppressWarnings("rawtypes")
public class ParameterOperator extends RequestOperator {
    public static final String OPERATOR_SEPARATOR = "|";

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
        if (Strings.isNotBlank(pageQueryRequest.getOrderBy())) {
            pageQueryRequest.setOrderBy(defaultOrder);
        }
        return pageQueryRequest;
    }


    protected PageQueryRequest getPageQueryParameters() {
        return getPageQueryParameters(this.request);
    }

    @Deprecated
    protected PageQueryRequest getPageQueryParameters(HttpServletRequest request) {
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

    protected Map<String, Object> getRequestBody() {
        Map<String, Object> requestBodyMap = new LinkedHashMap<>();
        try {
            String requestBody = GqlUtil.resolveGql(this.request);
            requestBodyMap = JSON.parseObject(requestBody, Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return requestBodyMap;
    }

    @Deprecated
    protected PageQueryRequest getPageQueryParameters(Map<String, Object> requestBodyMap) {
        PageQueryRequest queryRequest = new PageQueryRequest();
        if (requestBodyMap != null) {
            Object current = requestBodyMap.get("current");
            queryRequest.setPageNum(current == null || Strings.isBlank(current.toString()) ? 1 : Integer.parseInt(current.toString()));
            Object pageSize = requestBodyMap.get("pageSize");
            queryRequest.setPageSize(pageSize == null || Strings.isBlank(pageSize.toString()) ? 10 : Integer.parseInt(pageSize.toString()));
            String orderBy = requestBodyMap.get("order") == null ? "" : requestBodyMap.get("order").toString();
            orderBy = orderBy.replaceAll("\\|", " ");
            queryRequest.setOrderBy(orderBy);
        }
        return queryRequest;
    }

    protected PageQueryRequest getPageQueryParameters(Map<String, Object> requestBodyMap, String defaultOrder) {
        PageQueryRequest pageQueryRequest = getPageQueryParameters(requestBodyMap);
        if (Strings.isNotBlank(pageQueryRequest.getOrderBy())) {
            pageQueryRequest.setOrderBy(defaultOrder);
        }
        return pageQueryRequest;
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
