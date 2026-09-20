package cn.geelato.search.lucene.sync;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.search.api.SearchDocument;
import cn.geelato.search.lucene.config.GeelatoSearchProperties.DomainConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 回表加载器：按域配置读取主行 + 子实体全部行，组装完整文档。
 *
 * <p>不做增量 diff——事件只提供定位信息（主键/外键），文档始终由回表全量构建，
 * 简化一致性（幂等、乱序到达安全）。列名来自元数据（配置时已校验存在），
 * 参数一律绑定，不拼接值。
 */
@Slf4j
public class DocumentLoader {

    private static final String FIELD_TENANT = "tenantCode";
    private static final String FIELD_DEL_STATUS = "delStatus";
    private static final String FIELD_UPDATE_AT = "updateAt";

    private final JdbcTemplate jdbcTemplate;
    private final MetaManager metaManager = MetaManager.singleInstance();

    public DocumentLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 加载主实体文档（主行 + 子实体聚合）。
     *
     * @return 文档；主行不存在（未提交竞态或已物理删除）返回 null
     */
    public SearchDocument load(DomainConfig domain, String docId) {
        List<Map<String, Object>> rows = queryMainRows(domain, List.of(docId));
        if (rows.isEmpty()) {
            return null;
        }
        SearchDocument doc = new SearchDocument(docId);
        populateFromMainRow(doc, domain, rows.get(0));
        for (DomainConfig.ChildConfig child : domain.getChildren()) {
            loadChildInto(doc, child);
        }
        return doc;
    }

    /**
     * 批量加载文档（全量重建/对账用）：主行一次 IN 批查，每个子实体一次 IN 批查，
     * 往返次数为 O(批数) 而非 O(行数)——百万级重建的 DB 访问从百万次点查降为数千次批量读，
     * 且单条 SELECT 的语句快照比逐行点查的一致性更好。
     *
     * @return 主键 → 文档；主行缺失（已物理删除）的主键不出现在结果中
     */
    public Map<String, SearchDocument> loadBatch(DomainConfig domain, List<String> docIds) {
        if (docIds == null || docIds.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> rows = queryMainRows(domain, docIds);
        Map<String, SearchDocument> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String docId = String.valueOf(row.get(pkResultKey(domain)));
            SearchDocument doc = new SearchDocument(docId);
            populateFromMainRow(doc, domain, row);
            result.put(docId, doc);
        }
        if (result.isEmpty()) {
            return result;
        }
        for (DomainConfig.ChildConfig child : domain.getChildren()) {
            loadChildBatchInto(result, child);
        }
        return result;
    }

    /** 主行 IN 批查（列清单与单行 load 一致：主键 + 过滤列 + 编号列）。 */
    private List<Map<String, Object>> queryMainRows(DomainConfig domain, List<String> docIds) {
        EntityMeta em = metaManager.getByEntityName(domain.getMainEntity());
        String pkCol = em.getColumnName(domain.getPkField());
        List<String> selectCols = new ArrayList<>();
        selectCols.add(pkCol);
        addFilterColumn(selectCols, em, FIELD_TENANT);
        addFilterColumn(selectCols, em, FIELD_DEL_STATUS);
        for (String field : domain.getFields()) {
            selectCols.add(em.getColumnName(field));
        }
        String sql = "SELECT " + String.join(",", selectCols) + " FROM ob." + em.getTableName()
                + " WHERE " + pkCol + " IN (" + placeholders(docIds.size()) + ")";
        return jdbcTemplate.queryForList(sql, docIds.toArray());
    }

    private void populateFromMainRow(SearchDocument doc, DomainConfig domain, Map<String, Object> row) {
        EntityMeta em = metaManager.getByEntityName(domain.getMainEntity());
        doc.setTenantCode(asString(row.get(columnKey(em, FIELD_TENANT))));
        doc.setDelStatus(asString(row.get(columnKey(em, FIELD_DEL_STATUS))));
        for (String field : domain.getFields()) {
            String value = asString(row.get(columnKey(em, field)));
            if (value != null && !value.isEmpty()) {
                doc.addFieldValue(field, value);
            }
        }
    }

    /** 子实体批量聚合：一次 fk IN 批查后按外键分组写入对应主文档的多值字段。 */
    private void loadChildBatchInto(Map<String, SearchDocument> docs, DomainConfig.ChildConfig child) {
        EntityMeta cem = metaManager.getByEntityName(child.getEntity());
        String fkCol = cem.getColumnName(child.getFkField());
        List<String> selectCols = new ArrayList<>();
        selectCols.add(fkCol);
        for (String field : child.getFields()) {
            selectCols.add(cem.getColumnName(field));
        }
        List<String> docIds = new ArrayList<>(docs.keySet());
        String sql = "SELECT " + String.join(",", selectCols) + " FROM " + cem.getTableName()
                + " WHERE " + fkCol + " IN (" + placeholders(docIds.size()) + ")";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, docIds.toArray());
        for (Map<String, Object> row : rows) {
            SearchDocument doc = docs.get(String.valueOf(row.get(fkCol)));
            if (doc == null) {
                continue;
            }
            for (String field : child.getFields()) {
                String value = asString(row.get(columnKey(cem, field)));
                if (value != null && !value.isEmpty()) {
                    doc.addFieldValue(field, value);
                }
            }
        }
    }

    private static String placeholders(int count) {
        StringBuilder sb = new StringBuilder(count * 2);
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append('?');
        }
        return sb.toString();
    }

    /** 主键在结果集中的 key（驱动按列名返回；主键列名即 key）。 */
    private String pkResultKey(DomainConfig domain) {
        EntityMeta em = metaManager.getByEntityName(domain.getMainEntity());
        return em.getColumnName(domain.getPkField());
    }

    /** 子实体事件定位主文档：读子行外键值（行已物理删除时返回 null）。 */
    public String loadChildFk(DomainConfig domain, DomainConfig.ChildConfig child, String childPkValue) {
        EntityMeta cem = metaManager.getByEntityName(child.getEntity());
        String childPkCol = cem.getColumnName("id");
        String fkCol = cem.getColumnName(child.getFkField());
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + fkCol + " FROM " + cem.getTableName() + " WHERE " + childPkCol + " = ?",
                childPkValue);
        if (rows.isEmpty()) {
            return null;
        }
        Object fk = rows.get(0).get(fkCol);
        return fk == null ? null : String.valueOf(fk);
    }

    /** reindex 游标分页：按主键升序取 afterId 之后的一批主键。 */
    public List<String> scanIds(DomainConfig domain, String afterId, int limit) {
        EntityMeta em = metaManager.getByEntityName(domain.getMainEntity());
        String pkCol = em.getColumnName(domain.getPkField());
        String sql = "SELECT " + pkCol + " FROM " + "ob."+ em.getTableName();
        if (afterId != null) {
            sql += " WHERE " + pkCol + " > ?";
            sql += " ORDER BY " + pkCol + " LIMIT " + limit;
            return jdbcTemplate.queryForList(sql, String.class, afterId);
        }
        sql += " ORDER BY " + pkCol + " LIMIT " + limit;
        return jdbcTemplate.queryForList(sql, String.class);
    }

    /**
     * 对账水位扫描：update_at 在水位之后的主键（子实体变更返回外键去重值）。
     * 以主键为游标稳定分页（pkField/pkCol 升序 + pk > afterId）。
     */
    public List<String> scanIdsByUpdateAt(DomainConfig domain, String entityName, String fkField,
                                          java.sql.Timestamp watermark, String afterId, int limit) {
        EntityMeta em = metaManager.getByEntityName(entityName);
        String pkCol = em.getColumnName(fkField == null ? domain.getPkField() : fkField);
        String updateAtCol = em.getColumnName(FIELD_UPDATE_AT);
        String select = fkField == null ? pkCol : "DISTINCT " + pkCol;
        String sql = "SELECT " + select + " FROM " + em.getTableName()
                + " WHERE " + updateAtCol + " >= ? AND " + pkCol + " > ?"
                + " ORDER BY " + pkCol + " LIMIT " + limit;
        return jdbcTemplate.queryForList(sql, String.class, watermark, afterId == null ? "" : afterId);
    }

    /** 主表可见行数（对账 count 比对用，不滤软删——文档包含软删行并靠 delStatus 过滤）。 */
    public long countMainRows(DomainConfig domain) {
        EntityMeta em = metaManager.getByEntityName(domain.getMainEntity());
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + em.getTableName(), Long.class);
        return count == null ? 0 : count;
    }

    private void loadChildInto(SearchDocument doc, DomainConfig.ChildConfig child) {
        String mainFkValue = doc.getId();
        EntityMeta cem = metaManager.getByEntityName(child.getEntity());
        String fkCol = cem.getColumnName(child.getFkField());
        List<String> selectCols = new ArrayList<>();
        selectCols.add(fkCol);
        for (String field : child.getFields()) {
            selectCols.add(cem.getColumnName(field));
        }
        String sql = "SELECT " + String.join(",", selectCols) + " FROM "
                + cem.getTableName() + " WHERE " + fkCol + " = ?";
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, mainFkValue);
        for (Map<String, Object> row : rows) {
            for (String field : child.getFields()) {
                String value = asString(row.get(columnKey(cem, field)));
                if (value != null && !value.isEmpty()) {
                    doc.addFieldValue(field, value);
                }
            }
        }
    }

    private void addFilterColumn(List<String> selectCols, EntityMeta em, String fieldName) {
        if (em.containsField(fieldName)) {
            selectCols.add(em.getColumnName(fieldName));
        }
    }

    /** 结果集键：MySQL 驱动按列别名/列名返回；列名即 key。 */
    private String columnKey(EntityMeta em, String fieldName) {
        return em.getColumnName(fieldName);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
