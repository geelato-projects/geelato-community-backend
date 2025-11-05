package cn.geelato.core.gql.filter;


import cn.geelato.core.meta.model.parser.FunctionParser;
import com.alibaba.fastjson2.JSONArray;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The filter operator (comparison).
 * The supported operators are:
 * "eq" (equal to), "neq" (not equal to),
 * "lt" (less than), "lte" (less than or equal to),
 * "gt" (greater than),"gte" (greater than or equal to),
 * "startswith", "endswith", "contains".
 * The last three are supported only for string fields.
 *
 * @author geemeta
 */
public class FilterGroup {

    @Setter
    @Getter
    private Logic logic = Logic.and;
    private List<Filter> filters;
    @Getter
    private HashMap<String, Object> params;
    private int renameIndex = 1;

    @Setter
    private List<FilterGroup> childFilterGroup;
    public FilterGroup(){
    }
    public FilterGroup(Logic logic){
        this.logic=logic;
    }

    public List<Filter> getFilters() {
        if (filters == null) {
            filters = new ArrayList<>();
        }
        return filters;
    }

    /**
     * 如果filter中的field存在同名时，自动重命名
     *
     */
    public FilterGroup addFilter(Filter filter) {
        if (this.filters == null) {
            this.filters = new ArrayList<>();
            this.params = new HashMap<>();
        }
        this.filters.add(filter);
        if (params.containsKey(filter.getField())) {
            filter.setField(filter.getField());
            renameIndex++;
        }
        params.put(filter.getField() + renameIndex, filter.getValue());
        return this;
    }

    public FilterGroup addFilter(String field, Operator operator, String value) {
        addFilter(new Filter(field, operator, value));
        return this;
    }

    public FilterGroup addFilter(String field, String value) {
        addFilter(new Filter(field, Operator.eq, value));
        return this;
    }

    public List<FilterGroup> getChildFilterGroup() {
        if(childFilterGroup==null){
            childFilterGroup=new ArrayList<>();
        }
        return childFilterGroup;
    }

    public static class Filter {
        public Filter(String field, Operator operator, String value) {
            this.setField(field);
            this.setOperator(operator);
            this.setValue(value);
        }

        @Getter
        private String field;
        @Getter
        private Operator operator;
        @Getter
        private String value;

        private Object[] arrayValue;
        /**
         * -- GETTER --
         *  基于setField的值，若为tableName.fieldName，则为true
         */
        @Getter
        private boolean isRefField;
        @Getter
        private String refEntityName;

        @Getter
        private FilterFieldType filterFieldType;
        /**
         */
        public Filter setField(String field) {
            if (StringUtils.hasText(field) && field.contains(".") && !FunctionParser.isFunction(field)) {
                String[] arrays = field.split("\\.");
                this.isRefField = true;
                this.field = arrays[1];
                this.refEntityName = arrays[0];
            } else {
                this.isRefField = false;
                this.field = field;
                this.refEntityName = "";
            }

            if(FunctionParser.isFunction(field)){
                this.filterFieldType=FilterFieldType.Function;
            }else{
                this.filterFieldType=FilterFieldType.Normal;
            }
            return this;
        }

        public Filter setOperator(Operator operator) {
            this.operator = operator;
            return this;
        }

        /**
         * 一般用于in查询
         * @return 值的数组格式
         */
        public Object[] getValueAsArray() {
            if (arrayValue != null) {
                return arrayValue;
            }
            // in 查询时，value格式为数组的字符串格式，需进行转换 ["1","2",...]
            if (value.startsWith("[") && value.endsWith("]")) {
                JSONArray ary = JSONArray.parse(value);
                arrayValue = ary.toArray();
            } else {
                // 同时也支持非标准数组的格式："1","2",...，即少了符号：[]
                arrayValue = value.split(",");
            }
            return arrayValue;
        }

        public Filter setValue(String value) {
            this.value = value;
            this.arrayValue = null;
            return this;
        }

    }
    public enum FilterFieldType {
        Normal,
        Function
    }
    @Getter
    public enum Operator {
        eq("eq"),
        neq("neq"),
        lt("lt"),
        lte("lte"),
        gt("gt"),
        gte("gte"),
        startWith("startwith"),
        endWith("endwith"),

        contains("contains"),
        in("in"),
        notin("nin"),
        nil("nil"),
        bt("bt"),


        fis("fis");



        private final String text;

        Operator(String text) {
            this.text = text;
        }

        private static final Map<String, Operator> stringToEnum = new HashMap<String, Operator>();
        @Getter
        private static String operatorStrings = null;

        static {
            StringBuilder sb = new StringBuilder();
            for (Operator operator : values()) {
                stringToEnum.put(operator.toString(), operator);
                sb.append(operator);
                sb.append(",");
            }
            if (!sb.isEmpty()) {
                sb.setLength(sb.length() - 1);
            }
            operatorStrings = sb.toString();
        }

        public static boolean contains(String symbol) {
            return stringToEnum.containsKey(symbol);
        }

        public static Operator fromString(String symbol) {
            return stringToEnum.get(symbol.toLowerCase());
        }

        @Override
        public String toString() {
            return text;
        }
    }

    @Getter
    public enum Logic {
        or("or"), and("and");
        private final String text;
        Logic(String text) {
            this.text = text;
        }

        private static final Map<String, Logic> stringToEnum = new HashMap<String, Logic>();
        static {
            for (Logic logic : values()) {
                stringToEnum.put(logic.toString(), logic);
            }
        }
        public static Logic fromString(String symbol) {
            return stringToEnum.get(symbol.toLowerCase());
        }
    }


}
