package cn.geelato.web.platform.srv.email;

import cn.geelato.core.util.EncryptUtils;
import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.meta.UserEmailAccount;
import cn.geelato.orm.Filter;
import cn.geelato.orm.MetaFactory;
import cn.geelato.orm.Order;
import cn.geelato.orm.PageResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.email.dto.UserEmailAccountDto;
import cn.geelato.web.platform.srv.email.dto.UserEmailAccountUpsertRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApiRestController("/email/account")
@Slf4j
public class EmailAccountController extends BaseController {

    @PostMapping("/pageQuery")
    public ApiPagedResult<List<UserEmailAccountDto>> pageQuery(@RequestBody Map<String, Object> requestMap) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiPagedResult.fail("用户未登录");
            }
            String tenantCode = getTenantCode();

            int[] page = parsePage(requestMap);

            List<Filter> filters = new ArrayList<>();
            filters.add(Filter.eq("tenantCode", tenantCode));
            filters.add(Filter.eq("userId", userId));
            filters.add(Filter.eq("delStatus", 0));

            String emailAddress = Objects.toString(requestMap.get("emailAddress"), "");
            if (Strings.isNotBlank(emailAddress)) {
                filters.add(Filter.like("emailAddress", emailAddress));
            }

            Object enableStatus = requestMap.get("enableStatus");
            if (enableStatus != null && Strings.isNotBlank(String.valueOf(enableStatus))) {
                filters.add(Filter.eq("enableStatus", enableStatus));
            }

            Object isDefault = requestMap.get("isDefault");
            if (isDefault != null && Strings.isNotBlank(String.valueOf(isDefault))) {
                filters.add(Filter.eq("isDefault", isDefault));
            }

            PageResult<Map<String, Object>> raw = MetaFactory.query(UserEmailAccount.class)
                    .where(filters.toArray(new Filter[0]))
                    .order(Order.desc("isDefault"), Order.desc("createAt"))
                    .page(page[0], page[1])
                    .page();

            List<UserEmailAccountDto> dtos = raw.getRecords().stream().map(this::toDto).toList();
            return ApiPagedResult.success(dtos, raw.getCurrent(), (int) raw.getSize(), dtos.size(), raw.getTotal());
        } catch (Exception e) {
            log.error("pageQuery email account failed", e);
            return ApiPagedResult.fail("获取邮箱账号列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResult<UserEmailAccountDto> get(@PathVariable("id") String id) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String tenantCode = getTenantCode();

            Map<String, Object> row = MetaFactory.query(UserEmailAccount.class)
                    .where(
                            Filter.eq("id", id),
                            Filter.eq("tenantCode", tenantCode),
                            Filter.eq("userId", userId),
                            Filter.eq("delStatus", 0)
                    )
                    .one();
            if (row == null || row.isEmpty()) {
                return ApiResult.fail("邮箱账号不存在");
            }
            return ApiResult.success(toDto(row));
        } catch (Exception e) {
            log.error("get email account failed", e);
            return ApiResult.fail("获取邮箱账号失败: " + e.getMessage());
        }
    }

    @PostMapping("/createOrUpdate")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<String> createOrUpdate(@RequestBody UserEmailAccountUpsertRequest req) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String tenantCode = getTenantCode();

            if (req == null) {
                return ApiResult.fail("参数不能为空");
            }
            if (Strings.isBlank(req.getEmailAddress())) {
                return ApiResult.fail("邮箱地址不能为空");
            }
            if (Strings.isBlank(req.getImapHost())) {
                return ApiResult.fail("IMAP Host不能为空");
            }

            String authType = Strings.isNotBlank(req.getAuthType()) ? req.getAuthType() : "auth_code";
            Integer enableStatus = req.getEnableStatus() != null ? req.getEnableStatus() : 1;
            Integer isDefault = req.getIsDefault() != null ? req.getIsDefault() : 0;
            Integer imapSsl = req.getImapSsl() != null ? req.getImapSsl() : 1;
            Integer imapPort = req.getImapPort() != null ? req.getImapPort() : (imapSsl == 1 ? 993 : 143);
            String authUser = Strings.isNotBlank(req.getAuthUser()) ? req.getAuthUser() : req.getEmailAddress();

            if ("oauth2".equalsIgnoreCase(authType)) {
                return ApiResult.fail("oauth2暂未实现");
            }

            String encryptedSecret = null;
            if (Strings.isNotBlank(req.getAuthSecret())) {
                encryptedSecret = EncryptUtils.encrypt(req.getAuthSecret());
            }

            String id = Strings.isNotBlank(req.getId()) ? req.getId() : null;
            if (id == null) {
                id = MetaFactory.insert(UserEmailAccount.class)
                        .value("tenantCode", tenantCode)
                        .value("userId", userId)
                        .value("emailAddress", req.getEmailAddress())
                        .value("displayName", req.getDisplayName())
                        .value("isDefault", isDefault)
                        .value("imapHost", req.getImapHost())
                        .value("imapPort", imapPort)
                        .value("imapSsl", imapSsl)
                        .value("imapFolderDefault", req.getImapFolderDefault())
                        .value("authType", authType)
                        .value("authUser", authUser)
                        .value("authSecret", encryptedSecret)
                        .value("enableStatus", enableStatus)
                        .save();
            } else {
                Map<String, Object> exists = MetaFactory.query(UserEmailAccount.class)
                        .where(
                                Filter.eq("id", id),
                                Filter.eq("tenantCode", tenantCode),
                                Filter.eq("userId", userId),
                                Filter.eq("delStatus", 0)
                        )
                        .one();
                if (exists == null || exists.isEmpty()) {
                    return ApiResult.fail("邮箱账号不存在或无权限");
                }

                var update = MetaFactory.update(UserEmailAccount.class)
                        .where(
                                Filter.eq("id", id),
                                Filter.eq("tenantCode", tenantCode),
                                Filter.eq("userId", userId),
                                Filter.eq("delStatus", 0)
                        )
                        .value("emailAddress", req.getEmailAddress())
                        .value("displayName", req.getDisplayName())
                        .value("isDefault", isDefault)
                        .value("imapHost", req.getImapHost())
                        .value("imapPort", imapPort)
                        .value("imapSsl", imapSsl)
                        .value("imapFolderDefault", req.getImapFolderDefault())
                        .value("authType", authType)
                        .value("authUser", authUser)
                        .value("enableStatus", enableStatus);

                if (encryptedSecret != null) {
                    update.value("authSecret", encryptedSecret);
                }

                update.save();
            }

            if (isDefault == 1) {
                setDefaultInternal(tenantCode, userId, id);
            }

            return ApiResult.success(id);
        } catch (Exception e) {
            log.error("createOrUpdate email account failed", e);
            return ApiResult.fail("保存邮箱账号失败: " + e.getMessage());
        }
    }

    @PostMapping("/default/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Boolean> setDefault(@PathVariable("id") String id) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String tenantCode = getTenantCode();

            Map<String, Object> exists = MetaFactory.query(UserEmailAccount.class)
                    .where(
                            Filter.eq("id", id),
                            Filter.eq("tenantCode", tenantCode),
                            Filter.eq("userId", userId),
                            Filter.eq("delStatus", 0)
                    )
                    .one();
            if (exists == null || exists.isEmpty()) {
                return ApiResult.fail("邮箱账号不存在或无权限");
            }

            setDefaultInternal(tenantCode, userId, id);
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error("set default email account failed", e);
            return ApiResult.fail("设置默认邮箱失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Transactional(rollbackFor = Exception.class)
    public ApiResult<Boolean> delete(@PathVariable("id") String id) {
        try {
            String userId = SecurityContext.getCurrentUser().getUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String tenantCode = getTenantCode();

            MetaFactory.update(UserEmailAccount.class)
                    .where(
                            Filter.eq("id", id),
                            Filter.eq("tenantCode", tenantCode),
                            Filter.eq("userId", userId),
                            Filter.eq("delStatus", 0)
                    )
                    .value("delStatus", 1)
                    .value("deleteAt", new java.util.Date())
                    .save();
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error("delete email account failed", e);
            return ApiResult.fail("删除邮箱账号失败: " + e.getMessage());
        }
    }

    private void setDefaultInternal(String tenantCode, String userId, String targetId) {
        MetaFactory.update(UserEmailAccount.class)
                .where(
                        Filter.eq("tenantCode", tenantCode),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0),
                        Filter.eq("isDefault", 1)
                )
                .value("isDefault", 0)
                .save();

        MetaFactory.update(UserEmailAccount.class)
                .where(
                        Filter.eq("id", targetId),
                        Filter.eq("tenantCode", tenantCode),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("isDefault", 1)
                .save();
    }

    private UserEmailAccountDto toDto(Map<String, Object> row) {
        UserEmailAccountDto dto = new UserEmailAccountDto();
        dto.setId(Objects.toString(row.get("id"), null));
        dto.setEmailAddress(Objects.toString(row.get("emailAddress"), null));
        dto.setDisplayName(Objects.toString(row.get("displayName"), null));
        dto.setIsDefault(intVal(row.get("isDefault")));
        dto.setImapHost(Objects.toString(row.get("imapHost"), null));
        dto.setImapPort(intVal(row.get("imapPort")));
        dto.setImapSsl(intVal(row.get("imapSsl")));
        dto.setImapFolderDefault(Objects.toString(row.get("imapFolderDefault"), null));
        dto.setAuthType(Objects.toString(row.get("authType"), null));
        dto.setAuthUser(Objects.toString(row.get("authUser"), null));
        dto.setEnableStatus(intVal(row.get("enableStatus")));
        dto.setCreateAt(dateVal(row.get("createAt")));
        dto.setUpdateAt(dateVal(row.get("updateAt")));
        return dto;
    }

    private static Integer intVal(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(o));
        } catch (Exception ex) {
            return null;
        }
    }

    private static java.util.Date dateVal(Object o) {
        if (o instanceof java.util.Date d) {
            return d;
        }
        return null;
    }

    private static int[] parsePage(Map<String, Object> requestMap) {
        String p = Objects.toString(requestMap.get("@p"), "");
        if (Strings.isNotBlank(p) && p.contains(",")) {
            String[] ps = p.split(",", 2);
            try {
                int pageNum = Integer.parseInt(ps[0].trim());
                int pageSize = Integer.parseInt(ps[1].trim());
                return new int[]{Math.max(1, pageNum), Math.max(1, pageSize)};
            } catch (Exception ignored) {
            }
        }

        String current = Objects.toString(requestMap.get("current"), Objects.toString(requestMap.get("page"), ""));
        String size = Objects.toString(requestMap.get("pageSize"), Objects.toString(requestMap.get("size"), ""));
        int pageNum = 1;
        int pageSize = 20;
        try {
            if (Strings.isNotBlank(current)) {
                pageNum = Integer.parseInt(current);
            }
            if (Strings.isNotBlank(size)) {
                pageSize = Integer.parseInt(size);
            }
        } catch (Exception ignored) {
        }
        return new int[]{Math.max(1, pageNum), Math.max(1, pageSize)};
    }
}

