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

            Object defaultFlag = requestMap.get("defaultFlag");
            if (defaultFlag == null) {
                defaultFlag = requestMap.get("default_flag");
            }
            if (defaultFlag == null) {
                defaultFlag = requestMap.get("isDefault");
            }
            if (defaultFlag == null) {
                defaultFlag = requestMap.get("default");
            }
            if (defaultFlag != null && Strings.isNotBlank(String.valueOf(defaultFlag))) {
                filters.add(Filter.eq("defaultFlag", defaultFlag));
            }

            log.debug("emailAccount pageQuery, tenantCode={}, userId={}, emailAddress={}, enableStatus={}, defaultFlag={}, pageNum={}, pageSize={}",
                    tenantCode, userId, emailAddress, enableStatus, defaultFlag, page[0], page[1]);
            PageResult<Map<String, Object>> raw = MetaFactory.query(UserEmailAccount.class)
                    .where(filters.toArray(new Filter[0]))
                    .order(Order.desc("defaultFlag"), Order.desc("createAt"))
                    .page(page[0], page[1])
                    .page();

            List<UserEmailAccountDto> dtos = raw.getRecords().stream().map(this::toDto).toList();
            log.debug("emailAccount pageQuery done, tenantCode={}, userId={}, dataSize={}, total={}", tenantCode, userId, dtos.size(), raw.getTotal());
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

            log.debug("emailAccount get, tenantCode={}, userId={}, id={}", tenantCode, userId, id);
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
            Integer defaultFlag = req.getDefaultFlag() != null ? req.getDefaultFlag() : 0;
            Integer imapSsl = req.getImapSsl() != null ? req.getImapSsl() : 1;
            Integer imapPort = req.getImapPort() != null ? req.getImapPort() : (imapSsl == 1 ? 993 : 143);
            String authUser = Strings.isNotBlank(req.getAuthUser()) ? req.getAuthUser() : req.getEmailAddress();

            log.debug("emailAccount createOrUpdate request, tenantCode={}, userId={}, id={}, emailAddress={}, authType={}, enableStatus={}, defaultFlag={}, imapHost={}, imapPort={}, imapSsl={}, imapFolderDefault={}, authUser={}",
                    tenantCode, userId, req.getId(), maskEmail(req.getEmailAddress()), authType, enableStatus, defaultFlag, req.getImapHost(), imapPort, imapSsl, req.getImapFolderDefault(), maskEmail(authUser));
            String encryptedSecret = null;
            if (!"oauth2".equalsIgnoreCase(authType) && Strings.isNotBlank(req.getAuthSecret())) {
                encryptedSecret = EncryptUtils.encrypt(req.getAuthSecret());
            }

            String id = Strings.isNotBlank(req.getId()) ? req.getId() : null;
            if (id == null) {
                id = MetaFactory.insert(UserEmailAccount.class)
                        .value("tenantCode", tenantCode)
                        .value("userId", userId)
                        .value("emailAddress", req.getEmailAddress())
                        .value("displayName", req.getDisplayName())
                        .value("defaultFlag", defaultFlag)
                        .value("imapHost", req.getImapHost())
                        .value("imapPort", imapPort)
                        .value("imapSsl", imapSsl)
                        .value("imapFolderDefault", req.getImapFolderDefault())
                        .value("authType", authType)
                        .value("authUser", authUser)
                        .value("authSecret", encryptedSecret)
                        .value("oauth2Json", req.getOauth2Json())
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
                        .value("defaultFlag", defaultFlag)
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
                if (req.getOauth2Json() != null) {
                    update.value("oauth2Json", req.getOauth2Json());
                }

                update.save();
            }

            if (defaultFlag == 1) {
                setDefaultInternal(tenantCode, userId, id);
            }

            log.debug("emailAccount createOrUpdate done, tenantCode={}, userId={}, id={}, defaultFlag={}", tenantCode, userId, id, defaultFlag);
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

            log.debug("emailAccount setDefault request, tenantCode={}, userId={}, id={}", tenantCode, userId, id);
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
            log.debug("emailAccount setDefault done, tenantCode={}, userId={}, id={}", tenantCode, userId, id);
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

            log.debug("emailAccount delete request, tenantCode={}, userId={}, id={}", tenantCode, userId, id);
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
            log.debug("emailAccount delete done, tenantCode={}, userId={}, id={}", tenantCode, userId, id);
            return ApiResult.success(true);
        } catch (Exception e) {
            log.error("delete email account failed", e);
            return ApiResult.fail("删除邮箱账号失败: " + e.getMessage());
        }
    }

    private void setDefaultInternal(String tenantCode, String userId, String targetId) {
        log.debug("emailAccount setDefaultInternal start, tenantCode={}, userId={}, targetId={}", tenantCode, userId, targetId);
        MetaFactory.update(UserEmailAccount.class)
                .where(
                        Filter.eq("tenantCode", tenantCode),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0),
                        Filter.eq("defaultFlag", 1)
                )
                .value("defaultFlag", 0)
                .save();

        MetaFactory.update(UserEmailAccount.class)
                .where(
                        Filter.eq("id", targetId),
                        Filter.eq("tenantCode", tenantCode),
                        Filter.eq("userId", userId),
                        Filter.eq("delStatus", 0)
                )
                .value("defaultFlag", 1)
                .save();
        log.debug("emailAccount setDefaultInternal done, tenantCode={}, userId={}, targetId={}", tenantCode, userId, targetId);
    }

    private UserEmailAccountDto toDto(Map<String, Object> row) {
        UserEmailAccountDto dto = new UserEmailAccountDto();
        dto.setId(Objects.toString(row.get("id"), null));
        dto.setEmailAddress(Objects.toString(row.get("emailAddress"), null));
        dto.setDisplayName(Objects.toString(row.get("displayName"), null));
        dto.setDefaultFlag(intVal(row.get("defaultFlag")));
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

    private static String maskEmail(String raw) {
        if (Strings.isBlank(raw)) {
            return raw;
        }
        int at = raw.indexOf('@');
        if (at <= 1) {
            return raw;
        }
        String local = raw.substring(0, at);
        String domain = raw.substring(at);
        if (local.length() <= 2) {
            return local.charAt(0) + "*" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
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

