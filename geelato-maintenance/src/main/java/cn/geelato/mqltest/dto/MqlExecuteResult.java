package cn.geelato.mqltest.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * 单次 MQL 查询的真实执行结果（不做比对）。
 */
@Getter
@Setter
public class MqlExecuteResult {
    /** 实体名 */
    private String entityName;
    /** 生成的 SQL */
    private String sql;
    /** 参数数组 */
    private Object[] params;
    /** 参数类型数组 */
    private int[] types;
    /** count SQL（分页时） */
    private String countSql;
    /** 是否分页 */
    private boolean pagingQuery;
    /** 真实执行返回的行 */
    private List<Map<String, Object>> rows;
    /** 返回行数 */
    private int rowCount;
    /** 是否成功 */
    private boolean success = true;
    /** 错误信息 */
    private String error;
    /** 耗时（毫秒） */
    private long elapsedMs;

    public static MqlExecuteResult fail(String error) {
        MqlExecuteResult r = new MqlExecuteResult();
        r.setSuccess(false);
        r.setError(error);
        return r;
    }
}
