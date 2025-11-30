package cn.geelato.web.platform.srv.model;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.User;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.constants.MediaTypes;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.platform.service.MetaDdlService;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import cn.geelato.core.meta.MetaManager;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

/**
 * 原样导入控制器（保留外部ID）
 *
 * 路径前缀：/model/table/import
 * 功能：接收原始JSON（包含table、columns、checks），校验各记录id必填；
 * 使用命令式保存并设置forceId以保留外部主键，同时覆盖审计字段为当前环境当前用户与当前时间；
 * 最后根据entityName创建/更新数据库表结构与检查约束。
 */
@ApiRestController("/model/table/import")
@Slf4j
public class DevTableImportController extends BaseController {
    @Autowired
    @Qualifier("primaryDao")
    protected Dao dao;
    protected RuleService ruleService;
    @Resource
    private MetaDdlService metaDdlService;
    private final MetaManager metaManager = MetaManager.singleInstance();

    /**
     * 校验导入JSON中所有记录均包含id（table、columns、checks）
     *
     * @param table   表对象
     * @param columns 字段数组
     * @param checks  检查数组，可为空
     * @return 通过返回null，失败返回错误信息
     */
    private String validateIdsRequired(JSONObject table, JSONArray columns, JSONArray checks) {
        String tableId = table.getString("id");
        if (StringUtils.isEmpty(tableId)) {
            return "table记录缺少id，导入终止";
        }
        for (int i = 0; i < columns.size(); i++) {
            JSONObject c = columns.getJSONObject(i);
            if (StringUtils.isEmpty(c.getString("id"))) {
                return "columns记录存在缺少id的项，导入终止";
            }
        }
        if (checks != null) {
            for (int i = 0; i < checks.size(); i++) {
                JSONObject ck = checks.getJSONObject(i);
                if (StringUtils.isEmpty(ck.getString("id"))) {
                    return "checks记录存在缺少id的项，导入终止";
                }
            }
        }
        return null;
    }

    /**
     * 校验导入JSON中所有记录均不包含id（table、columns、checks）
     *
     * @param table   表对象
     * @param columns 字段数组
     * @param checks  检查数组，可为空
     * @return 通过返回null，失败返回错误信息
     */
    private String validateIdsForbidden(JSONObject table, JSONArray columns, JSONArray checks) {
        if (StringUtils.isNotEmpty(table.getString("id"))) {
            return "table记录不应包含id，导入终止";
        }
        for (int i = 0; i < columns.size(); i++) {
            JSONObject c = columns.getJSONObject(i);
            if (StringUtils.isNotEmpty(c.getString("id"))) {
                return "columns记录存在id，不符合新建导入";
            }
        }
        if (checks != null) {
            for (int i = 0; i < checks.size(); i++) {
                JSONObject ck = checks.getJSONObject(i);
                if (StringUtils.isNotEmpty(ck.getString("id"))) {
                    return "checks记录存在id，不符合新建导入";
                }
            }
        }
        return null;
    }

    /**
     * 复制JSON对象为Map并移除id字段，同时保留原始值（后续由normalize步骤统一数值化布尔型）
     *
     * @param src 源JSON对象
     * @return 去除id后的字段映射
     */
    private Map<String, Object> copyFieldsWithoutId(JSONObject src) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (String key : src.keySet()) {
            if (!"id".equals(key)) {
                m.put(key, src.get(key));
            }
        }
        return m;
    }

    /**
     * 填充审计字段为当前环境（用户/租户/组织/时间）
     *
     * @param m    目标Map
     * @param user 当前用户
     * @param now  当前时间
     */
    private void applyAudit(Map<String, Object> m, User user, Date now) {
        m.put("createAt", now);
        m.put("updateAt", now);
        m.put("creator", SessionCtx.getUserId());
        m.put("creatorName", SessionCtx.getUserName());
        m.put("updater", SessionCtx.getUserId());
        m.put("updaterName", SessionCtx.getUserName());
        m.put("tenantCode", SessionCtx.getCurrentTenantCode());
        m.put("buId", user == null ? null : user.getBuId());
        m.put("deptId", user == null ? null : user.getDeptId());
    }

    /**
     * 构建以 platform_dev_table 为根的保存结构，子实体使用 #platform_dev_column / #platform_dev_table_check
     * 并执行审计字段填充与布尔数值化（true/false -> 1/0）
     *
     * @param table       表对象
     * @param columns     字段数组
     * @param checks      检查数组
     * @param preserveId  是否保留外部主键（使用 forceId）
     * @param user        当前用户
     * @param now         当前时间
     * @return 根保存结构的JSON字符串
     */
    private String buildRootJson(JSONObject table, JSONArray columns, JSONArray checks, boolean preserveId, User user, Date now) {
        JSONObject root = new JSONObject();
        JSONObject t = new JSONObject();
        Map<String, Object> tableMap = copyFieldsWithoutId(table);
        applyAudit(tableMap, user, now);
        if (preserveId) {
            tableMap.put("forceId", table.getString("id"));
        }
        t.putAll(tableMap);

        JSONArray cs = new JSONArray();
        for (int i = 0; i < columns.size(); i++) {
            JSONObject c = columns.getJSONObject(i);
            Map<String, Object> m = copyFieldsWithoutId(c);
            applyAudit(m, user, now);
            if (preserveId) {
                m.put("forceId", c.getString("id"));
            }
            cs.add(new JSONObject(m));
        }
        if (!cs.isEmpty()) {
            t.put("#platform_dev_column", cs);
        }

        JSONArray ks = new JSONArray();
        if (checks != null) {
            for (int i = 0; i < checks.size(); i++) {
                JSONObject ck = checks.getJSONObject(i);
                Map<String, Object> m = copyFieldsWithoutId(ck);
                applyAudit(m, user, now);
                if (preserveId) {
                    m.put("forceId", ck.getString("id"));
                }
                ks.add(new JSONObject(m));
            }
        }
        if (!ks.isEmpty()) {
            t.put("#platform_dev_table_check", ks);
        }

        normalizeBooleans(t);
        root.put("platform_dev_table",t);
        // 打印root
        log.info("gql root: {}", root.toJSONString());
        return root.toJSONString();
    }

    /**
     * 将布尔型及其字符串表示统一转换为数值（1/0），并递归处理对象或数组
     *
     * @param v 输入值
     * @return 规范化后的值
     */
    private Object normalizeValue(Object v) {
        if (v instanceof Boolean) {
            return ((Boolean) v) ? 1 : 0;
        } else if (v instanceof String) {
            String s = ((String) v).toLowerCase();
            if ("true".equals(s)) {
                return 1;
            } else if ("false".equals(s)) {
                return 0;
            }
            return v;
        } else if (v instanceof JSONObject) {
            normalizeBooleans((JSONObject) v);
            return v;
        } else if (v instanceof JSONArray) {
            JSONArray arr = (JSONArray) v;
            for (int i = 0; i < arr.size(); i++) {
                arr.set(i, normalizeValue(arr.get(i)));
            }
            return arr;
        }
        return v;
    }

    /**
     * 遍历对象属性，统一布尔型与其字符串表示为数值（1/0）
     *
     * @param obj 待处理对象
     */
    private void normalizeBooleans(JSONObject obj) {
        for (String key : obj.keySet()) {
            obj.put(key, normalizeValue(obj.get(key)));
        }
    }

    /**
     * 原样导入（保留外部ID）
     *
     * 路径：POST /model/table/import/preserveId
     * 请求体：原始JSON字符串
     * 要求：table/columns/checks 所有记录必须包含 id
     * 行为：校验 → 构建命令树（保留forceId）→ 规则保存 → 可选DDL创建/更新 → 刷新缓存 → 返回计数
     * 参数：ddl（默认true）是否在导入成功后立即创建/更新数据库表结构
     */
    @RequestMapping(value = {"/preserveId"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<?> importRaw(@RequestBody String bodyJson, @RequestParam(value = "ddl", defaultValue = "true") boolean ddl) {
        try {
            JSONObject root = JSON.parseObject(bodyJson);
            JSONObject table = root.getJSONObject("table");
            JSONArray columns = root.getJSONArray("columns");
            JSONArray checks = root.getJSONArray("checks");

            if (table == null || columns == null) {
                return ApiResult.fail("JSON缺少必要的table或columns节点");
            }
            String err = validateIdsRequired(table, columns, checks);
            if (err != null) {
                return ApiResult.fail(err);
            }

            User user = SessionCtx.getCurrentUser();
            Date now = new Date();
            String gql = buildRootJson(table, columns, checks, true, user, now);
            log.info("importRaw preserveId gql root: {}", gql);
            ruleService.save("model_table_import_preserveId", gql);

            String entityName = table.getString("entityName");
            if (StringUtils.isNotEmpty(entityName)) {
                if(ddl){
                    metaDdlService.createOrUpdateTableByEntityName(dao, entityName, false);
                }
                metaManager.refreshDBMeta(entityName);
            }

            cn.geelato.core.meta.model.entity.EntityMeta em = metaManager.getByEntityName(entityName);
            int tablesCount = 1;
            int columnsCount = em != null && em.getFieldMetas() != null ? em.getFieldMetas().size() : 0;
            int checksCount = em != null && em.getTableChecks() != null ? em.getTableChecks().size() : 0;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tables", tablesCount);
            result.put("columns", columnsCount);
            result.put("checks", checksCount);
            return ApiResult.success(result, "原样导入完成");
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ApiResult.fail(ex.getMessage());
        }
    }

    /**
     * 新建导入（不包含外部ID）
     *
     * 路径：POST /model/table/import/create
     * 请求体：原始JSON字符串
     * 要求：table/columns/checks 所有记录禁止包含 id
     * 行为：校验 → 构建命令树（不设置forceId）→ 规则保存 → 可选DDL创建/更新 → 刷新缓存 → 返回计数
     * 参数：ddl（默认true）是否在导入成功后立即创建/更新数据库表结构
     */
    @RequestMapping(value = {"/create"}, method = {RequestMethod.POST}, produces = MediaTypes.APPLICATION_JSON_UTF_8)
    @ResponseBody
    public ApiResult<?> importCreate(@RequestBody String bodyJson, @RequestParam(value = "ddl", defaultValue = "true") boolean ddl) {
        try {
            JSONObject root = JSON.parseObject(bodyJson);
            JSONObject table = root.getJSONObject("table");
            JSONArray columns = root.getJSONArray("columns");
            JSONArray checks = root.getJSONArray("checks");

            if (table == null || columns == null) {
                return ApiResult.fail("JSON缺少必要的table或columns节点");
            }
            String err = validateIdsForbidden(table, columns, checks);
            if (err != null) {
                return ApiResult.fail(err);
            }

            User user = SessionCtx.getCurrentUser();
            Date now = new Date();
            String gql = buildRootJson(table, columns, checks, false, user, now);
            ruleService.save("model_table_import_create", gql);

            String entityName = table.getString("entityName");
            if (StringUtils.isNotEmpty(entityName)) {
                if(ddl){
                    metaDdlService.createOrUpdateTableByEntityName(dao, entityName, false);
                }
                metaManager.refreshDBMeta(entityName);
            }

            cn.geelato.core.meta.model.entity.EntityMeta em = metaManager.getByEntityName(entityName);
            int tablesCount = 1;
            int columnsCount = em != null && em.getFieldMetas() != null ? em.getFieldMetas().size() : 0;
            int checksCount = em != null && em.getTableChecks() != null ? em.getTableChecks().size() : 0;
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tables", tablesCount);
            result.put("columns", columnsCount);
            result.put("checks", checksCount);
            return ApiResult.success(result, "新建导入完成");
        } catch (Exception ex) {
            log.error(ex.getMessage(), ex);
            return ApiResult.fail(ex.getMessage());
        }
    }

    /**
     * 通过Spring框架的@Autowired注解自动注入RuleService对象
     * 这个方法主要用于设置RuleService对象，以便在类内部使用
     *
     * @param ruleService 规则服务对象，用于执行规则操作
     */
    @Autowired
    protected void setRuleService(RuleService ruleService) {
        this.ruleService = ruleService;
    }
}
