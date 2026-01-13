package cn.geelato.web.platform.srv.platform;

import cn.geelato.core.SessionCtx;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.core.orm.Dao;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.User;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.DateUtils;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.platform.service.RuleService;
import cn.geelato.web.platform.srv.security.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@ApiRestController("/bizData")
@Slf4j
public class BizDataController extends BaseController {

    @Autowired
    @Qualifier("dynamicDao")
    protected Dao dynamicDao;
    private final UserService userService;
    private final MetaManager metaManager = MetaManager.singleInstance();

    @Autowired
    public BizDataController(UserService userService, RuleService ruleService) {
        this.userService = userService;
        this.ruleService = ruleService;
    }

    /**
     * 批量变更数据归属者（creator / creatorName）
     *
     * @param entityIdMap key 为实体名称，value 为主键ID列表
     * @param owner       目标归属用户ID
     */
    @RequestMapping(value = "/changeOwner", method = RequestMethod.POST)
    public ApiResult<Map<String, Integer>> changeOwner(@RequestBody Map<String, List<String>> entityIdMap,
                                                       @RequestParam String owner) {
        try {
            if (entityIdMap == null || entityIdMap.isEmpty()) {
                return ApiResult.fail("参数不能为空");
            }
            if (Strings.isBlank(owner)) {
                return ApiResult.fail("owner不能为空");
            }

            User targetUser = userService.getModel(User.class, owner);
            if (targetUser == null) {
                return ApiResult.fail("未找到指定的用户");
            }

            Map<String, Integer> result = new HashMap<>();
            String currentUserId = SecurityContext.getCurrentUser().getUserId();
            boolean isAdmin = SecurityContext.isAdmin();
            StringBuilder denyMsg = new StringBuilder();

            for (Map.Entry<String, List<String>> entry : entityIdMap.entrySet()) {
                String entityName = entry.getKey();
                List<String> pkList = entry.getValue();
                if (Strings.isBlank(entityName) || pkList == null || pkList.isEmpty()) {
                    continue;
                }
                if (!metaManager.containsEntity(entityName)) {
                    log.warn("未找到实体：{}", entityName);
                    continue;
                }

                EntityMeta em = metaManager.getByEntityName(entityName);
                String catalog = em.getCatalog();
                String en = em.getEntityName();
                String tn = em.getTableMeta() != null ? em.getTableMeta().getTableName() : null;
                if ("platform".equalsIgnoreCase(catalog)
                        || (en != null && en.toLowerCase(Locale.ROOT).startsWith("platform"))
                        || (tn != null && tn.toLowerCase(Locale.ROOT).startsWith("platform"))) {
                    log.warn("禁止修改平台类实体：{}", entityName);
                    continue;
                }
                String connectId = em.getTableMeta() != null ? em.getTableMeta().getConnectId() : null;
                if (Strings.isNotBlank(connectId)) {
                    switchDbByConnectId(connectId);
                }

                String tableName = em.getTableMeta() != null ? em.getTableMeta().dbTableName() : em.getEntityName();
                String pkColumn = em.getId() != null ? em.getId().getColumnName() : "id";

                if (!isAdmin) {
                    if (!em.containsField("creator")) {
                        String msg = String.format("实体[%s]不包含creator字段，无法移交", entityName);
                        log.warn(msg);
                        if (!denyMsg.isEmpty()) {
                            denyMsg.append("; ");
                        }
                        denyMsg.append(msg);
                        continue;
                    }
                }

                Set<String> allowedIds = new HashSet<>();
                if (isAdmin) {
                    allowedIds.addAll(pkList);
                } else {
                    String creatorColumn = em.getColumnName("creator");
                    StringBuilder sel = new StringBuilder();
                    sel.append("SELECT ").append(pkColumn).append(", ").append(creatorColumn)
                            .append(" FROM ").append(tableName)
                            .append(" WHERE ").append(pkColumn).append(" IN (")
                            .append(String.join(",", Collections.nCopies(pkList.size(), "?")))
                            .append(")");
                    List<Map<String, Object>> rows = dynamicDao.getJdbcTemplate().queryForList(sel.toString(), pkList.toArray());
                    Set<String> deniedIds = new HashSet<>();
                    Set<String> requestedIds = new HashSet<>(pkList);
                    for (Map<String, Object> row : rows) {
                        Object idVal = row.get(pkColumn);
                        Object creatorVal = row.get(creatorColumn);
                        String idStr = idVal != null ? idVal.toString() : null;
                        String creatorStr = creatorVal != null ? creatorVal.toString() : null;
                        if (idStr != null && currentUserId.equals(creatorStr)) {
                            allowedIds.add(idStr);
                        } else if (idStr != null) {
                            deniedIds.add(idStr);
                        }
                    }
                    for (String rid : requestedIds) {
                        if (!allowedIds.contains(rid)) {
                            deniedIds.add(rid);
                        }
                    }
                    if (!deniedIds.isEmpty()) {
                        String msg = String.format("实体[%s]非所有者ID：%s", entityName, String.join(",", deniedIds));
                        log.warn(msg);
                        if (!denyMsg.isEmpty()) {
                            denyMsg.append("; ");
                        }
                        denyMsg.append(msg);
                        continue;
                    }
                }

                // 仅更新实体中存在的字段
                List<String> setClauses = new ArrayList<>();
                List<Object> params = new ArrayList<>();

                if (em.containsField("creator")) {
                    setClauses.add(em.getColumnName("creator") + " = ?");
                    params.add(targetUser.getId());
                }
                if (em.containsField("creatorName")) {
                    setClauses.add(em.getColumnName("creatorName") + " = ?");
                    params.add(targetUser.getName());
                }

                if (setClauses.isEmpty()) {
                    log.warn("实体不包含可更新字段(creator/creatorName)：{}", entityName);
                    continue;
                }

                StringBuilder sql = new StringBuilder();
                sql.append("UPDATE ").append(tableName).append(" SET ")
                        .append(String.join(", ", setClauses))
                        .append(" WHERE ").append(pkColumn).append(" IN (");
                String placeholders = String.join(",", Collections.nCopies(allowedIds.size(), "?"));
                sql.append(placeholders).append(")");

                params.addAll(allowedIds);

                int updated = dynamicDao.getJdbcTemplate().update(sql.toString(), params.toArray());
                result.put(entityName, updated);
            }

            if (!denyMsg.isEmpty()) {
                return ApiResult.fail("你不是数据所有者，无法移交：" + denyMsg);
            } else {
                return ApiResult.success(result);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
