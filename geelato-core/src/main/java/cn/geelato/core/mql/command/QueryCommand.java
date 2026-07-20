package cn.geelato.core.mql.command;

import cn.geelato.core.mql.filter.FilterGroup;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.Setter;

import org.apache.commons.collections.map.HashedMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Setter
@Getter
public class QueryCommand extends BaseCommand<QueryCommand> {

    private boolean queryForList = false;
    private int pageNum = -1;
    private int pageSize = -1;
    private HashedMap alias = new HashedMap();
    private String selectSql;
    private String groupBy;
    private String orderBy;
    private FilterGroup having;
    private String havingSql;
    private String ACL;
    private Map<String, Object> viewTemplateParams;
    private String tableAlias;
    private List<QueryJoin> joins = new ArrayList<>();
    private List<QuerySelectExpr> selectExprs = new ArrayList<>();

    protected String[] foreignFields;

    public QueryCommand() {
        setCommandType(CommandType.Query);
    }

    public boolean isPagingQuery() {
        return pageNum > 0 && pageSize > 0;
    }

    /**
     * 查询的规范化签名（未哈希）。
     * <p>
     * 仅描述"这个查询查什么"，覆盖所有影响结果集的维度，不涉及任何缓存概念。
     * 上层（缓存、日志、去重等）可基于此派生各自的 key/fingerprint。
     * </p>
     * <p>
     * 设计要点：
     * <ul>
     *   <li>无状态纯计算，每次调用都反映当前字段值，不存在"何时计算"的时机问题</li>
     *   <li>fields 排序、viewTemplateParams 用 TreeMap，保证相同查询产生相同签名</li>
     *   <li>where/having 递归调用 {@link FilterGroup#signatureString()}，规避 HashMap 无序</li>
     * </ul>
     */
    public String signatureString() {
        StringBuilder raw = new StringBuilder();
        append(raw, "cmd", Objects.toString(getCommandType(), "null"));
        append(raw, "list", queryForList);
        append(raw, "pg", pageNum);
        append(raw, "sz", pageSize);
        append(raw, "acl", ACL);
        append(raw, "fields", arraySig(getFields()));
        append(raw, "where", getWhere() == null ? "null" : getWhere().signatureString());
        append(raw, "owhere", Objects.toString(getOriginalWhere(), "null"));
        append(raw, "sel", Objects.toString(selectSql, "null"));
        append(raw, "grp", Objects.toString(groupBy, "null"));
        append(raw, "ord", Objects.toString(orderBy, "null"));
        append(raw, "alias", alias == null ? "{}" : JSON.toJSONString(alias));
        append(raw, "having", having == null ? "null" : having.signatureString());
        append(raw, "pf", mapSig(viewTemplateParams));
        append(raw, "join", joins == null ? "null" : JSON.toJSONString(joins));
        append(raw, "selExpr", selectExprs == null ? "null" : JSON.toJSONString(selectExprs));
        append(raw, "tAlias", Objects.toString(tableAlias, "null"));
        return raw.toString();
    }

    private static void append(StringBuilder sb, String k, Object v) {
        sb.append(k).append('=').append(v).append('&');
    }

    /** 数组签名：null → "null"；非空 → 排序后逗号拼接 */
    private static String arraySig(String[] arr) {
        if (arr == null) {
            return "null";
        }
        String[] copy = arr.clone();
        java.util.Arrays.sort(copy);
        return String.join(",", copy);
    }

    /** Map 签名：null → "null"；非空 → TreeMap 按 key 排序后 toString */
    private static String mapSig(Map<String, Object> m) {
        if (m == null || m.isEmpty()) {
            return "null";
        }
        return new java.util.TreeMap<>(m).toString();
    }
}
