package cn.geelato.ide.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.orm.Dao;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.User;
import cn.geelato.ide.entity.IdeScript;
import cn.geelato.ide.entity.IdeScriptLanguage;
import cn.geelato.ide.entity.IdeScriptStatus;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * IDE 脚本业务服务。
 * <p>
 * 承载核心生产级能力：
 * <ul>
 *   <li>乐观锁：update 必须比对 version，冲突时抛 {@link IdeOptimisticLockException}</li>
 *   <li>文件哈希：每次内容变更重算 sha256，供文件↔DB 同步冲突检测</li>
 *   <li>租户隔离：复用 {@link SessionCtx#getCurrentTenantCode()}</li>
 *   <li>状态机：DRAFT → PUBLISHED → ARCHIVED</li>
 * </ul>
 *
 * @author geelato
 */
@Service
@Slf4j
public class IdeScriptService {

    @Autowired
    private BaseService baseService;

    @Autowired
    private IdeWasmStorage wasmStorage;

    /**
     * 按 code 取单个脚本（含内容）。
     *
     * @param code 业务编码
     * @return 脚本实体；不存在返回 null
     */
    public IdeScript getByCode(String code) {
        if (Strings.isBlank(code)) {
            return null;
        }
        Dao dao = baseService.dao;
        dao.setDefaultFilter(true, baseService.filterGroup);
        IdeScript script = dao.queryForObject(IdeScript.class, "code", code);
        // wasm 字节码从 OSS/本地按需加载为 base64（列表查询不触发，只在单条详情时加载）
        populateWasmBase64(script);
        return script;
    }

    /**
     * 按 id 取单个脚本。
     */
    public IdeScript getById(String id) {
        if (Strings.isBlank(id)) {
            return null;
        }
        Dao dao = baseService.dao;
        dao.setDefaultFilter(true, baseService.filterGroup);
        IdeScript script = dao.queryForObject(IdeScript.class, id);
        populateWasmBase64(script);
        return script;
    }

    /**
     * 按 code 取脚本（不加载 wasm 字节码，列表/批量场景用）。
     */
    public IdeScript getByCodeLite(String code) {
        if (Strings.isBlank(code)) {
            return null;
        }
        Dao dao = baseService.dao;
        dao.setDefaultFilter(true, baseService.filterGroup);
        return dao.queryForObject(IdeScript.class, "code", code);
    }

    /**
     * 若脚本是 wasm 且有 objectName，从存储加载字节码转 base64 填入（供 HTTP 传输）。
     */
    private void populateWasmBase64(IdeScript script) {
        if (script == null) {
            return;
        }
        if (IdeScriptLanguage.WASM.equals(script.getLanguage()) && Strings.isNotBlank(script.getWasmObjectName())) {
            script.setWasmBinaryBase64(wasmStorage.loadAsBase64(script.getWasmObjectName()));
        }
    }

    /**
     * 创建脚本。code 租户内唯一，重复抛 IllegalArgumentException。
     * 字段约束：language 必须 js/python/wasm；status 强制 DRAFT。
     */
    public IdeScript create(IdeScript script) {
        validateForCreate(script);
        // 默认值
        if (Strings.isBlank(script.getLanguage())) {
            script.setLanguage(IdeScriptLanguage.JS);
        }
        script.setStatus(IdeScriptStatus.DRAFT);
        if (Strings.isBlank(script.getEnvScope())) {
            script.setEnvScope("dev");
        }
        if (script.getVersion() == null) {
            script.setVersion(1);
        }
        // wasm base64 → OSS/本地存储，DB 只存 objectName
        storeWasmIfNeeded(script, null);
        // 哈希
        script.setFileHash(computeHash(script));
        script.setId(generateId());
        return baseService.createModel(script);
    }

    /**
     * 更新脚本（乐观锁）。
     *
     * @param code        业务编码
     * @param newFields   待更新字段
     * @param clientVersion 客户端读到的版本号
     * @return 更新后的脚本
     * @throws IdeOptimisticLockException 版本不一致
     */
    public IdeScript update(String code, IdeScript newFields, Integer clientVersion) {
        IdeScript existing = getByCodeLite(code);
        if (existing == null) {
            throw new IllegalArgumentException("脚本不存在: " + code);
        }
        // 乐观锁校验
        if (clientVersion == null || !clientVersion.equals(existing.getVersion())) {
            throw new IdeOptimisticLockException(existing.getVersion(), clientVersion);
        }
        // 应用变更
        applyUpdate(existing, newFields);
        validateForUpdate(existing);
        // wasm base64 → OSS/本地存储；若更换了字节码，删旧的
        storeWasmIfNeeded(existing, existing.getWasmObjectName());
        existing.setFileHash(computeHash(existing));
        // 用条件 SQL 更新（version = version + 1 where version = clientVersion）
        int affected = updateWithOptimisticLock(existing, clientVersion);
        if (affected == 0) {
            // 并发场景：刚刚被别人改了
            throw new IdeOptimisticLockException(existing.getVersion(), clientVersion);
        }
        return getByCode(code);
    }

    /**
     * 逻辑删除（del_status = 1）。
     */
    public void delete(String code) {
        IdeScript existing = getByCodeLite(code);
        if (existing == null) {
            throw new IllegalArgumentException("脚本不存在: " + code);
        }
        // 删除 wasm 字节码（OSS/本地），失败不阻断
        if (Strings.isNotBlank(existing.getWasmObjectName())) {
            wasmStorage.deleteQuietly(existing.getWasmObjectName());
        }
        baseService.isDeleteModel(existing);
    }

    /**
     * 发布脚本：DRAFT → PUBLISHED。
     */
    public IdeScript publish(String code) {
        IdeScript existing = getByCode(code);
        if (existing == null) {
            throw new IllegalArgumentException("脚本不存在: " + code);
        }
        if (!IdeScriptStatus.DRAFT.equals(existing.getStatus())) {
            throw new IllegalStateException("仅 DRAFT 状态可发布，当前: " + existing.getStatus());
        }
        Dao dao = baseService.dao;
        String sql = "UPDATE ide_script SET status = ?, update_at = ?, updater = ?, updater_name = ?, version = version + 1 WHERE id = ? AND del_status = 0";
        int affected = dao.getJdbcTemplate().update(sql,
                IdeScriptStatus.PUBLISHED, new Date(),
                SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getUserId() : null,
                SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getUserName() : null,
                existing.getId());
        if (affected == 0) {
            throw new IllegalStateException("发布失败，可能已被修改");
        }
        return getByCode(code);
    }

    /**
     * 列表查询（含分页）。
     */
    public Map<String, Object> list(Map<String, Object> params, int page, int size) {
        Dao dao = baseService.dao;
        dao.setDefaultFilter(true, baseService.filterGroup);
        Map<String, Object> result = new HashMap<>();
        // 总数
        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM ide_script WHERE del_status = 0");
        StringBuilder listSql = new StringBuilder("SELECT id, code, name, group_name, language, file_hash, version, status, env_scope, description, tenant_code, app_id, creator, creator_name, create_at, updater, updater_name, update_at FROM ide_script WHERE del_status = 0");
        List<Object> sqlParams = new java.util.ArrayList<>();
        appendFilters(countSql, listSql, params, sqlParams);
        Long total = dao.nativeQueryForObject(countSql.toString(), sqlParams.toArray(), Long.class);
        listSql.append(" ORDER BY update_at DESC LIMIT ? OFFSET ?");
        sqlParams.add(size);
        sqlParams.add((long) size * (page - 1));
        List<Map<String, Object>> list = dao.nativeQueryForMapList(listSql.toString(), sqlParams.toArray());
        result.put("list", list);
        result.put("total", total == null ? 0L : total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    /**
     * 分组列表（脚本树用）。
     */
    public List<Map<String, Object>> groups() {
        Dao dao = baseService.dao;
        String tenantCode = SessionCtx.getCurrentTenantCode();
        StringBuilder sql = new StringBuilder(
                "SELECT COALESCE(group_name, '') AS group_name, COUNT(*) AS count FROM ide_script WHERE del_status = 0");
        List<Object> params = new java.util.ArrayList<>();
        if (Strings.isNotBlank(tenantCode)) {
            sql.append(" AND tenant_code = ?");
            params.add(tenantCode);
        }
        sql.append(" GROUP BY COALESCE(group_name, '') ORDER BY group_name");
        return dao.nativeQueryForMapList(sql.toString(), params.toArray());
    }

    // ======================================================================
    //                           内部 helper
    // ======================================================================

    private void validateForCreate(IdeScript script) {
        if (Strings.isBlank(script.getCode())) {
            throw new IllegalArgumentException("code 不能为空");
        }
        if (!script.getCode().matches("^[A-Za-z][A-Za-z0-9_]{0,127}$")) {
            throw new IllegalArgumentException("code 必须以字母开头，仅含字母数字下划线，长度 1-128");
        }
        if (Strings.isBlank(script.getName())) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (Strings.isNotBlank(script.getLanguage()) && !IdeScriptLanguage.isValid(script.getLanguage())) {
            throw new IllegalArgumentException("language 仅支持: js / python / wasm");
        }
        // code 唯一性
        if (getByCode(script.getCode()) != null) {
            throw new IllegalArgumentException("code 已存在: " + script.getCode());
        }
    }

    private void validateForUpdate(IdeScript script) {
        if (Strings.isBlank(script.getName())) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (Strings.isNotBlank(script.getLanguage()) && !IdeScriptLanguage.isValid(script.getLanguage())) {
            throw new IllegalArgumentException("language 仅支持: js / python / wasm");
        }
    }

    private void applyUpdate(IdeScript existing, IdeScript newFields) {
        if (Strings.isNotBlank(newFields.getName())) {
            existing.setName(newFields.getName());
        }
        if (newFields.getGroupName() != null) {
            existing.setGroupName(newFields.getGroupName());
        }
        if (Strings.isNotBlank(newFields.getLanguage())) {
            existing.setLanguage(newFields.getLanguage());
        }
        if (newFields.getContent() != null) {
            existing.setContent(newFields.getContent());
        }
        if (newFields.getWasmBinaryBase64() != null) {
            existing.setWasmBinaryBase64(newFields.getWasmBinaryBase64());
        }
        if (Strings.isNotBlank(newFields.getEnvScope())) {
            existing.setEnvScope(newFields.getEnvScope());
        }
        if (newFields.getDescription() != null) {
            existing.setDescription(newFields.getDescription());
        }
        if (newFields.getDefaultParams() != null) {
            existing.setDefaultParams(newFields.getDefaultParams());
        }
    }

    /**
     * 若是 wasm 且传了 base64 字节码，存到 OSS/本地，DB 只存 objectName。
     *
     * @param script        脚本实体（会被设置 wasmObjectName）
     * @param oldObjectName 旧的 objectName（更换字节码时删除旧文件，可为 null）
     */
    private void storeWasmIfNeeded(IdeScript script, String oldObjectName) {
        if (!IdeScriptLanguage.WASM.equals(script.getLanguage())) {
            return;
        }
        if (Strings.isBlank(script.getWasmBinaryBase64())) {
            // 没传新字节码，保留旧 objectName
            if (Strings.isNotBlank(oldObjectName)) {
                script.setWasmObjectName(oldObjectName);
            }
            return;
        }
        // 传了新字节码，存存储
        byte[] bytes = Base64.getDecoder().decode(script.getWasmBinaryBase64());
        String objectName = wasmStorage.save(bytes, script.getCode());
        script.setWasmObjectName(objectName);
        // 删旧文件（若有）
        if (Strings.isNotBlank(oldObjectName) && !oldObjectName.equals(objectName)) {
            wasmStorage.deleteQuietly(oldObjectName);
        }
    }

    /**
     * 条件更新 SQL（乐观锁）。
     * 返回受影响行数：1=成功，0=版本不一致。
     */
    private int updateWithOptimisticLock(IdeScript script, Integer expectedVersion) {
        Dao dao = baseService.dao;
        User user = SecurityContext.getCurrentUser();
        String userId = user != null ? user.getUserId() : null;
        String userName = user != null ? user.getUserName() : null;
        StringBuilder sql = new StringBuilder(
                "UPDATE ide_script SET name=?, group_name=?, language=?, content=?, wasm_object_name=?, file_hash=?, " +
                        "env_scope=?, description=?, default_params=?, update_at=?, updater=?, updater_name=?, " +
                        "version = version + 1 WHERE id = ? AND version = ? AND del_status = 0");
        return dao.getJdbcTemplate().update(sql.toString(),
                script.getName(),
                script.getGroupName(),
                script.getLanguage(),
                script.getContent(),
                script.getWasmObjectName(),
                script.getFileHash(),
                script.getEnvScope(),
                script.getDescription(),
                script.getDefaultParams(),
                new Date(), userId, userName,
                script.getId(),
                expectedVersion);
    }

    private String computeHash(IdeScript script) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String input = script.getLanguage() + ":" +
                    (script.getContent() == null ? "" : script.getContent());
            // wasm 的字节码在 OSS，hash 基于 objectName（DB 唯一源）；不拉字节码算 hash（避免每次 IO）
            if (IdeScriptLanguage.WASM.equals(script.getLanguage())
                    && Strings.isNotBlank(script.getWasmObjectName())) {
                input = input + ":wasm=" + script.getWasmObjectName();
            }
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("计算文件哈希失败", e);
        }
    }

    private void appendFilters(StringBuilder countSql, StringBuilder listSql, Map<String, Object> params, List<Object> sqlParams) {
        if (params == null || params.isEmpty()) {
            // 默认按当前租户过滤
            String tc = SessionCtx.getCurrentTenantCode();
            if (Strings.isNotBlank(tc)) {
                countSql.append(" AND tenant_code = ?");
                listSql.append(" AND tenant_code = ?");
                sqlParams.add(tc);
            }
            return;
        }
        // 租户
        String tc = params.get("tenant_code") != null ? params.get("tenant_code").toString() : SessionCtx.getCurrentTenantCode();
        if (Strings.isNotBlank(tc)) {
            countSql.append(" AND tenant_code = ?");
            listSql.append(" AND tenant_code = ?");
            sqlParams.add(tc);
        }
        for (Map.Entry<String, Object> e : params.entrySet()) {
            String k = e.getKey();
            if (e.getValue() == null || "tenant_code".equals(k) || "page".equals(k) || "size".equals(k)) {
                continue;
            }
            String column = camelToSnake(k);
            if (e.getValue() instanceof String && ((String) e.getValue()).contains("%")) {
                countSql.append(" AND ").append(column).append(" LIKE ?");
                listSql.append(" AND ").append(column).append(" LIKE ?");
                sqlParams.add(e.getValue());
            } else {
                countSql.append(" AND ").append(column).append(" = ?");
                listSql.append(" AND ").append(column).append(" = ?");
                sqlParams.add(e.getValue());
            }
        }
    }

    private static String camelToSnake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < camel.length(); i++) {
            char c = camel.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    sb.append('_');
                }
                sb.append(Character.toLowerCase(c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String generateId() {
        // 19 位数字 ID（与平台其他实体风格一致）
        return String.valueOf(System.currentTimeMillis() * 1000 + (UUID.randomUUID().hashCode() & 0x3FF));
    }
}
