package cn.geelato.search.api;

import java.util.ArrayList;
import java.util.List;

/**
 * 平台中立的检索查询：多关键词 OR × 多字段任意位置包含（contains），
 * 叠加过滤字段。语义与数据库函数 {@code gfn_fuzzymatch} 的多词 OR 对齐——
 * 关键词为字面量（不含正则元字符，该判定由路由侧完成，见 FuzzymatchSupport）。
 */
public class SearchQuery {

    /** 关键词列表（已按原函数清洗规则拆分；任一命中即匹配）。 */
    private List<String> terms = new ArrayList<>();

    /** 参与检索的字段名列表。 */
    private List<String> fields = new ArrayList<>();

    /** 租户过滤（null 表示不过滤）。 */
    private String tenantCode;

    /** 软删状态过滤（null 表示不过滤）。 */
    private String delStatus;

    /** 应用过滤（null 表示不过滤）。 */
    private String appId;

    /** id 集合上限；命中超过上限返回 truncated=true（路由侧应放弃路由，不得截断使用）。 */
    private int maxIds = 50000;

    public List<String> getTerms() {
        return terms;
    }

    public void setTerms(List<String> terms) {
        this.terms = terms;
    }

    public List<String> getFields() {
        return fields;
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public String getDelStatus() {
        return delStatus;
    }

    public void setDelStatus(String delStatus) {
        this.delStatus = delStatus;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public int getMaxIds() {
        return maxIds;
    }

    public void setMaxIds(int maxIds) {
        this.maxIds = maxIds;
    }
}
