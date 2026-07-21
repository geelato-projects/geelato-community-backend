package cn.geelato.web.platform.srv.security.service;

import cn.geelato.core.mql.filter.FilterGroup;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.User;
import cn.geelato.meta.UserDelegateMap;
import cn.geelato.web.platform.srv.platform.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 用户委托关系服务
 * <p>
 * 维护用户之间的委托/助理关系。一行表达「委托人 - 代理人在某个业务范围(scope)上的委托」，
 * 即同一委托人为同一代理人配置多个业务范围时存为多行。
 * <p>
 * 业务唯一性：委托人(user_id) + 代理人(delegate_user_id) + 业务范围(scope) 三者组合不重复。
 * 创建/更新时自动回填姓名、代理人英文名。
 * 其他业务模块消费时，直接查询本表（按 user_id + enable_status=1 + scope 过滤）即可。
 *
 * @author geelato
 */
@Component
@Slf4j
public class UserDelegateMapService extends BaseService {
    @Autowired
    private UserService userService;

    /**
     * 批量创建委托关系
     * <p>
     * 入参 form 的 scope 可为逗号分隔的多个业务范围（如 "periodic_report,todo"），
     * 本方法会按 scope 拆分为多行，逐条做唯一性校验后插入；已存在的组合会被跳过。
     * 若 scope 为空，则插入一条 scope 为空（表示全部业务）的记录。
     *
     * @param form 委托关系对象（scope 可为多值串）
     * @return 实际插入的委托关系列表
     */
    public List<UserDelegateMap> createModels(UserDelegateMap form) {
        validateForm(form);
        User user = userService.getModel(User.class, form.getUserId());
        User delegateUser = userService.getModel(User.class, form.getDelegateUserId());
        // 解析 scope 列表（去重、去空）；scope 为空时用 "" 表示「全部业务」，只插一条
        List<String> scopes = parseScopes(form.getScope());
        List<UserDelegateMap> result = new ArrayList<>();
        for (String scope : scopes) {
            // 唯一性校验：已存在则跳过
            if (existsDuplicate(form.getUserId(), form.getDelegateUserId(), scope, null)) {
                continue;
            }
            UserDelegateMap item = new UserDelegateMap();
            item.setUserId(form.getUserId());
            item.setUserName(user != null ? user.getName() : null);
            item.setDelegateUserId(form.getDelegateUserId());
            item.setDelegateUserName(delegateUser != null ? delegateUser.getName() : null);
            item.setDelegateUserEnName(delegateUser != null ? delegateUser.getEnName() : null);
            item.setRelationType(Strings.isNotBlank(form.getRelationType()) ? form.getRelationType() : "delegate");
            item.setScope(scope);
            item.setEnableStatus(form.getEnableStatus());
            result.add(this.createModel(item));
        }
        return result;
    }

    /**
     * 唯一性校验（供 Controller 的 validate 接口使用）
     * <p>
     * 判断给定的委托人 + 代理人 + scope 组合是否已存在（排除自身 id 与已逻辑删除记录）。
     *
     * @param id             当前编辑的记录ID，可为空
     * @param userId         委托人ID
     * @param delegateUserId 代理人ID
     * @param scope          业务范围（单值）
     * @return true 表示可用（不重复），false 表示已存在
     */
    public boolean validateDuplicate(String id, String userId, String delegateUserId, String scope) {
        if (Strings.isBlank(userId) || Strings.isBlank(delegateUserId)) {
            return true;
        }
        return !existsDuplicate(userId, delegateUserId, scope, id);
    }

    /**
     * 按 委托人ID + 代理人ID 查询所有业务范围行（用于列表聚合展示等）
     */
    public List<UserDelegateMap> queryByUserAndDelegate(String userId, String delegateUserId) {
        if (Strings.isBlank(userId) || Strings.isBlank(delegateUserId)) {
            return new ArrayList<>();
        }
        FilterGroup filter = new FilterGroup();
        filter.addFilter("userId", userId);
        filter.addFilter("delegateUserId", delegateUserId);
        return this.queryModel(UserDelegateMap.class, filter);
    }

    /**
     * 判断指定组合是否已存在
     *
     * @param excludeId 需要排除的记录ID（编辑场景传入自身），新增时传 null
     */
    private boolean existsDuplicate(String userId, String delegateUserId, String scope, String excludeId) {
        FilterGroup filter = new FilterGroup();
        filter.addFilter("userId", userId);
        filter.addFilter("delegateUserId", delegateUserId);
        filter.addFilter("scope", scope != null ? scope : "");
        List<UserDelegateMap> list = this.queryModel(UserDelegateMap.class, filter);
        if (list == null || list.isEmpty()) {
            return false;
        }
        if (Strings.isNotBlank(excludeId)) {
            return list.stream().anyMatch(item -> !excludeId.equals(item.getId()));
        }
        return true;
    }

    /**
     * 表单基础校验
     */
    private void validateForm(UserDelegateMap form) {
        if (Strings.isBlank(form.getUserId())) {
            throw new RuntimeException("委托人不能为空");
        }
        if (Strings.isBlank(form.getDelegateUserId())) {
            throw new RuntimeException("代理人不能为空");
        }
        if (form.getUserId().equals(form.getDelegateUserId())) {
            throw new RuntimeException("不能委托给自己");
        }
        if (userService.getModel(User.class, form.getUserId()) == null) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
        if (userService.getModel(User.class, form.getDelegateUserId()) == null) {
            throw new RuntimeException(ApiErrorMsg.IS_NULL);
        }
    }

    /**
     * 解析 scope 字符串为去重后的列表
     * <p>
     * 输入空串或 null 时，返回 ["" ]，表示「全部业务」，仅插一条。
     */
    private List<String> parseScopes(String scope) {
        List<String> result = new ArrayList<>();
        if (Strings.isBlank(scope)) {
            result.add("");
            return result;
        }
        for (String s : scope.split(",")) {
            String trimmed = s.trim();
            if (!result.contains(trimmed)) {
                result.add(trimmed);
            }
        }
        return result;
    }
}
