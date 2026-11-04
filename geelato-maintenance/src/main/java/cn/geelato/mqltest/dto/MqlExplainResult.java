package cn.geelato.mqltest.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * MQL explain（dry-run）结果。
 * <p>
 * 包含解析后的 AST 信息、生成的 SQL、参数数组、类型数组、count SQL。
 */
@Getter
@Setter
public class MqlExplainResult {
    /** 实体名 */
    private String entityName;
    /** 生成的查询 SQL（含 ? 占位符） */
    private String sql;
    /** 参数数组（与 ? 一一对应） */
    private Object[] params;
    /** 参数类型数组（java.sql.Types） */
    private int[] types;
    /** count SQL（分页时） */
    private String countSql;
    /** 是否分页查询 */
    private boolean pagingQuery;
    /** 选择的字段 */
    private String[] fields;
    /** 排序 */
    private String orderBy;
    /** 分组 */
    private String groupBy;
    /** 页码 */
    private int pageNum;
    /** 每页条数 */
    private int pageSize;
    /** 错误信息（解析/生成失败时） */
    private String error;
    /** 是否成功 */
    private boolean success = true;
    /** 完整的 AST 快照（序列化 QueryCommand 关键字段） */
    private Map<String, Object> ast;

    /**
     * 解析失败时构造
     */
    public static MqlExplainResult fail(String error) {
        MqlExplainResult r = new MqlExplainResult();
        r.setSuccess(false);
        r.setError(error);
        return r;
    }
}
