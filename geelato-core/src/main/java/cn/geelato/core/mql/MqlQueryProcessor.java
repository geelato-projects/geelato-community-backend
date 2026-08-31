package cn.geelato.core.mql;

import cn.geelato.core.Fn;
import cn.geelato.core.enums.ViewTypeEnum;
import cn.geelato.core.meta.EntityType;
import cn.geelato.core.mql.command.QueryCommand;
import cn.geelato.core.mql.execute.BoundPageSql;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.meta.model.view.ViewMeta;
import cn.geelato.core.sql.SqlManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * MQL 查询统一处理器：封装从 MQL JSON 到 BoundPageSql 的完整链路。
 * <p>
 * 这是平台查询的<strong>唯一公共入口</strong>。实际执行（{@code RuleService}）、
 * 模拟/解释/测试（Playground 的 explain/execute）都调本类，确保走相同方法、相同分支，
 * 改 core 时三者同时生效。
 * <p>
 * 处理步骤（与原 RuleService.queryForMapList 前半段完全一致）：
 * <ol>
 *   <li>{@link #extractPfAndSerialize}：从 MQL JSON 抽取 {@code @pf}（视图模板参数）并从 JSON 移除；
 *       经 {@link #process(String, Map)} 入口时，先与外部传入参数合并（外部优先）再进入后续步骤</li>
 *   <li>{@link MetaQLManager#generateQuerySql}：解析 JSON 为 QueryCommand（含 SPI 注入器）</li>
 *   <li>{@link #applyViewTemplateParams}：将 @pf 参数设到 command（仅虚拟视图 VIRTUAL 生效）</li>
 *   <li>{@link #processQueryCommandFunctions}：处理 {@code $fn.now} 等函数变量</li>
 *   <li>{@link SqlManager#generatePageQuerySql}：生成 BoundPageSql（SQL + count）</li>
 * </ol>
 *
 * @author geelato
 */
public class MqlQueryProcessor {

    private static final String VARS_FN = "$fn";
    private static final String PF_KEY = "@pf";

    private final MetaQLManager gqlManager = MetaQLManager.singleInstance();
    private final SqlManager sqlManager = SqlManager.singleInstance();
    private final MetaManager metaManager = MetaManager.singleInstance();

    private static final MqlQueryProcessor INSTANCE = new MqlQueryProcessor();

    public static MqlQueryProcessor getInstance() {
        return INSTANCE;
    }

    private MqlQueryProcessor() {
    }

    /**
     * MQL 处理结果。
     */
    public static class ProcessedQuery {
        private final QueryCommand command;
        private final BoundPageSql boundPageSql;
        private final Map<String, Map<String, Object>> paramsByEntity;

        public ProcessedQuery(QueryCommand command, BoundPageSql boundPageSql,
                              Map<String, Map<String, Object>> paramsByEntity) {
            this.command = command;
            this.boundPageSql = boundPageSql;
            this.paramsByEntity = paramsByEntity;
        }

        public QueryCommand getCommand() { return command; }
        public BoundPageSql getBoundPageSql() { return boundPageSql; }
        public Map<String, Map<String, Object>> getParamsByEntity() { return paramsByEntity; }
    }

    /**
     * 完整处理：MQL JSON → QueryCommand → BoundPageSql（含 count）。
     * <p>
     * 这是实际执行、模拟、解释、测试共用的唯一入口。依次执行 extractPf → parse → applyViewTemplateParams
     * → processQueryCommandFunctions → generatePageQuerySql 全部五步。
     *
     * @param mqlJson 原始 MQL JSON（可含 @pf）
     * @return 处理结果（含 command 和 boundPageSql）
     */
    public ProcessedQuery process(String mqlJson) {
        return process(mqlJson, null);
    }

    /**
     * 完整处理（支持外部注入 @pf 参数）：MQL JSON → QueryCommand → BoundPageSql（含 count）。
     * <p>
     * 适用于调用方（如 /meta/list 的 Controller）已预先抽取 @pf 并从 JSON 移除的场景：
     * 此时 mqlJson 中已无 @pf，由 externalParamsByEntity 提供视图模板参数。
     * mqlJson 中仍含 @pf 时两者合并，外部参数优先（同 key 覆盖）。
     *
     * @param mqlJson                MQL JSON（可含 @pf，也可为已移除 @pf 的净化 JSON）
     * @param externalParamsByEntity 外部视图模板参数（实体名 → 参数），可为 null
     * @return 处理结果（含 command、boundPageSql 和合并后的参数）
     */
    public ProcessedQuery process(String mqlJson, Map<String, Map<String, Object>> externalParamsByEntity) {
        JSONObject root = JSON.parseObject(mqlJson);
        Map<String, Map<String, Object>> paramsByEntity = new HashMap<>();
        String cleanGql = extractPfAndSerialize(root, paramsByEntity);
        if (externalParamsByEntity != null && !externalParamsByEntity.isEmpty()) {
            externalParamsByEntity.forEach((entityName, params) -> {
                if (params == null) {
                    return;
                }
                paramsByEntity.merge(entityName, new HashMap<>(params), (extracted, external) -> {
                    extracted.putAll(external);
                    return extracted;
                });
            });
        }
        QueryCommand command = gqlManager.generateQuerySql(cleanGql);
        applyViewTemplateParams(command, paramsByEntity);
        processQueryCommandFunctions(command);
        BoundPageSql boundPageSql = sqlManager.generatePageQuerySql(command);
        return new ProcessedQuery(command, boundPageSql, paramsByEntity);
    }

    /**
     * 从 MQL JSON 根对象中抽取 @pf（视图模板参数），并从 JSON 中移除。
     * 支持多实体（每个实体的 @pf 分别抽取）。
     *
     * @param root           MQL JSON 根对象（会被修改——移除 @pf 键）
     * @param paramsByEntity 输出：实体名 → 视图模板参数
     * @return 移除 @pf 后的 JSON 字符串
     */
    public String extractPfAndSerialize(JSONObject root, Map<String, Map<String, Object>> paramsByEntity) {
        if (root == null || root.isEmpty()) {
            return root == null ? "{}" : root.toJSONString();
        }
        root.forEach((entityName, value) -> {
            if (!(value instanceof JSONObject entityBody)) {
                return;
            }
            JSONObject pf = entityBody.getJSONObject(PF_KEY);
            if (pf != null) {
                paramsByEntity.put(entityName, new HashMap<>(pf));
                entityBody.remove(PF_KEY);
            }
        });
        return root.toJSONString();
    }

    /**
     * 将视图模板参数设到 QueryCommand（仅对虚拟视图 VIRTUAL 生效）。
     * <p>
     * 虚拟视图不落库（见 MetaDdlService：虚拟视图无需创建），查询时由 view_construct
     * 动态构造派生表，@pf 模板参数因此可注入；其他视图（DEFAULT/COMPLEX/CUSTOM，或无
     * 视图元数据的物理 DB 视图）已被物化或不含模板，收到非空 @pf 参数时抛出
     * {@link ViewTemplateParamException}，不静默丢弃。
     */
    public void applyViewTemplateParams(QueryCommand command, Map<String, Map<String, Object>> paramsByEntity) {
        if (command == null || paramsByEntity == null || paramsByEntity.isEmpty()) {
            return;
        }
        Map<String, Object> params = paramsByEntity.get(command.getEntityName());
        if (params == null || params.isEmpty()) {
            return;
        }
        EntityMeta entityMeta = metaManager.getByEntityName(command.getEntityName());
        if (entityMeta == null || entityMeta.getTableMeta() == null) {
            throw new ViewTemplateParamException(command.getEntityName(), params.keySet(), "实体元数据缺失");
        }
        if (EntityType.View != entityMeta.getEntityType()) {
            throw new ViewTemplateParamException(command.getEntityName(), params.keySet(), "该实体不是视图实体");
        }
        ViewMeta viewMeta = entityMeta.getViewMeta(entityMeta.getTableName());
        if (viewMeta == null || !StringUtils.hasText(viewMeta.getViewType())
                || !ViewTypeEnum.VIRTUAL.getCode().equalsIgnoreCase(viewMeta.getViewType())) {
            String reason = viewMeta == null ? "该视图无平台视图元数据（物理DB视图）"
                    : "视图类型为" + viewMeta.getViewType();
            throw new ViewTemplateParamException(command.getEntityName(), params.keySet(), reason);
        }
        command.setViewTemplateParams(params);
    }

    /**
     * 统一处理 QueryCommand 中 where 参数的函数变量，如 $fn.now / $fn.nowDate / $fn.nowDateTime。
     */
    public void processQueryCommandFunctions(QueryCommand command) {
        if (command == null || command.getWhere() == null || command.getWhere().getParams() == null) {
            return;
        }
        Map<String, Object> params = command.getWhere().getParams();
        params.forEach((key, value) -> {
            if (value != null) {
                String valStr = value.toString();
                if (valStr.startsWith(VARS_FN)) {
                    String fnName = valStr.substring(VARS_FN.length() + 1);
                    String newValue;
                    switch (fnName) {
                        case "now", "nowDateTime" -> newValue = Fn.nowDateTime();
                        case "nowDate" -> newValue = Fn.nowDate();
                        default -> newValue = null;
                    }
                    if (command.getWhere().getFilters() != null) {
                        command.getWhere().getFilters().stream()
                                .filter(filter -> value.equals(filter.getValue()))
                                .forEach(filter -> filter.setValue(newValue));
                    }
                    params.replace(key, newValue);
                }
            }
        });
    }
}
