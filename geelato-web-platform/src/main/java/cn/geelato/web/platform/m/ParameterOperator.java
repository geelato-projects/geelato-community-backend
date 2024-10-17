package cn.geelato.web.platform.m;

import cn.geelato.core.gql.parser.PageQueryRequest;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.util.Strings;

import java.lang.reflect.Field;
import java.util.*;

@SuppressWarnings("rawtypes")
public class ParameterOperator extends RequestOperator {

    protected Map<String, Object> getQueryParameters(Class elementType) {
        return getQueryParameters(elementType,this.request);
    }
    @Deprecated
    protected Map<String, Object> getQueryParameters(Class elementType, HttpServletRequest request) {
        Map<String, Object> queryParamsMap = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            Set<String> fieldNames = getClassFieldNames(elementType);
            if (fieldNames.contains(entry.getKey())) {
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
}
