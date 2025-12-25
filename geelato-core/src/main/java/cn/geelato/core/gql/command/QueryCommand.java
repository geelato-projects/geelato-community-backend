package cn.geelato.core.gql.command;

import cn.geelato.core.gql.filter.FilterGroup;
import com.alibaba.fastjson2.JSON;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.map.HashedMap;

import java.util.Objects;

@Setter
@Getter
public class QueryCommand extends BaseCommand<QueryCommand> {

    private String cacheKey;
    private boolean queryForList = false;
    private int pageNum = -1;
    private int pageSize = -1;
    private HashedMap alias = new HashedMap();
    private String selectSql;
    private String groupBy;
    private String orderBy;
    private FilterGroup having;
    private String ACL;
    public QueryCommand() {
        generateCacheKey();
        setCommandType(CommandType.Query);
    }

    private void generateCacheKey() {
        String rawKey = Objects.toString(getCommandType(), "null") + "|" +
                Objects.toString(queryForList, "false") + "|" +
                Objects.toString(pageNum, "-1") + "|" +
                Objects.toString(pageSize, "-1") + "|" +
                Objects.toString(ACL, "null") + "|" +
                Objects.toString(selectSql, "null") + "|" +
                Objects.toString(groupBy, "null") + "|" +
                Objects.toString(orderBy, "null") + "|" +
                (Objects.isNull(alias) ? "{}" : JSON.toJSONString(alias)) + "|" +
                (Objects.isNull(having) ? "null" : JSON.toJSONString(having)) + "|";
        this.cacheKey = DigestUtils.md5Hex(rawKey).toUpperCase();
    }

    public boolean isPagingQuery() {
        return pageNum > 0 && pageSize > 0;
    }
}
