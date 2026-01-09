package cn.geelato.web.platform.srv;

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

    protected final String PAGE_NUM_PARAM = "current";
    protected final String PAGE_SIZE_PARAM = "pageSize";
    protected final String ORDER_BY_PARAM = "order";
    protected final int DEFAULT_PAGE_NUM = 1;
    protected final int DEFAULT_PAGE_SIZE = 10;
    protected static final String DEFAULT_ORDER_BY = "seq_no ASC,update_at DESC";


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
        int pageNum = Strings.isNotBlank(request.getParameter(PAGE_NUM_PARAM)) ?
                Integer.parseInt(request.getParameter(PAGE_NUM_PARAM)) : -1;
        queryRequest.setPageNum(pageNum);
        int pageSize = Strings.isNotBlank(request.getParameter(PAGE_SIZE_PARAM)) ?
                Integer.parseInt(request.getParameter(PAGE_SIZE_PARAM)) : -1;
        queryRequest.setPageSize(pageSize);
        String orderBy = Strings.isNotBlank(request.getParameter(ORDER_BY_PARAM)) ?
                String.valueOf(request.getParameter(ORDER_BY_PARAM)) : "";
        orderBy = orderBy.replaceAll("\\|", " ");
        queryRequest.setOrderBy(orderBy);

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
            Object current = requestBodyMap.get(PAGE_NUM_PARAM);
            queryRequest.setPageNum(current == null || Strings.isBlank(current.toString()) ?
                    DEFAULT_PAGE_NUM : Integer.parseInt(current.toString()));
            Object pageSize = requestBodyMap.get(PAGE_SIZE_PARAM);
            queryRequest.setPageSize(pageSize == null || Strings.isBlank(pageSize.toString()) ?
                    DEFAULT_PAGE_SIZE : Integer.parseInt(pageSize.toString()));
            String orderBy = requestBodyMap.get(ORDER_BY_PARAM) == null
                    ? "" : requestBodyMap.get(ORDER_BY_PARAM).toString();
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
