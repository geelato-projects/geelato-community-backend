package cn.geelato.web.platform.srv.email;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.email.dto.EmailContactBackfillRequest;
import cn.geelato.web.platform.srv.email.dto.EmailContactDto;
import cn.geelato.web.platform.srv.email.dto.EmailContactUpsertRequest;
import cn.geelato.web.platform.srv.email.dto.EmailRecipientSuggestionDto;
import cn.geelato.web.platform.srv.email.service.EmailContactService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApiRestController("/email/contact")
@Slf4j
public class EmailContactController extends BaseController {

    @Autowired
    private EmailContactService emailContactService;

    @PostMapping("/pageQuery")
    public ApiPagedResult<List<EmailContactDto>> pageQuery(@RequestBody(required = false) Map<String, Object> requestMap) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiPagedResult.fail("用户未登录");
            }
            Map<String, Object> rm = requestMap != null ? requestMap : Collections.emptyMap();
            int[] page = parsePage(rm);
            String keyword = trimToNull(Objects.toString(rm.get("keyword"), null));
            Integer favoriteFlag = parseInteger(rm.get("favoriteFlag"));
            String sourceType = trimToNull(Objects.toString(rm.get("sourceType"), null));
            String emailAccountId = trimToNull(Objects.toString(rm.get("emailAccountId"), null));
            EmailContactService.PageResult<EmailContactDto> result = emailContactService.pageQuery(
                    userId, keyword, favoriteFlag, sourceType, emailAccountId, page[0], page[1]);
            List<EmailContactDto> list = result.data();
            return ApiPagedResult.success(list, page[0], page[1], list != null ? list.size() : 0, result.total());
        } catch (IllegalArgumentException ex) {
            return ApiPagedResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("pageQuery email contact failed", e);
            return ApiPagedResult.fail("获取联系人列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResult<EmailContactDto> get(@PathVariable("id") String id) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            EmailContactDto dto = emailContactService.get(userId, id);
            if (dto == null) {
                return ApiResult.fail("联系人不存在");
            }
            return ApiResult.success(dto);
        } catch (Exception e) {
            log.error("get email contact failed", e);
            return ApiResult.fail("获取联系人失败: " + e.getMessage());
        }
    }

    @PostMapping("/createOrUpdate")
    public ApiResult<String> createOrUpdate(@RequestBody EmailContactUpsertRequest req) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            String id = emailContactService.createOrUpdate(userId, getTenantCodeSafe(), req);
            return ApiResult.success(id);
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("save email contact failed", e);
            return ApiResult.fail("保存联系人失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable("id") String id) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailContactService.remove(userId, id));
        } catch (Exception e) {
            log.error("delete email contact failed", e);
            return ApiResult.fail("删除联系人失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/favorite")
    public ApiResult<Boolean> favorite(@PathVariable("id") String id, @RequestBody(required = false) Map<String, Object> requestMap) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            boolean favoriteFlag = parseBoolean(requestMap != null ? requestMap.get("favoriteFlag") : null);
            return ApiResult.success(emailContactService.setFavorite(userId, id, favoriteFlag));
        } catch (Exception e) {
            log.error("favorite email contact failed", e);
            return ApiResult.fail("设置常用联系人失败: " + e.getMessage());
        }
    }

    @GetMapping("/favorites")
    public ApiResult<List<EmailRecipientSuggestionDto>> favorites(@RequestParam(value = "limit", required = false) Integer limit,
                                                                  @RequestParam(value = "keyword", required = false) String keyword,
                                                                  @RequestParam(value = "emailAccountId", required = false) String emailAccountId) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailContactService.listFavorites(userId, trimToNull(keyword), trimToNull(emailAccountId), safeLimit(limit)));
        } catch (Exception e) {
            log.error("query favorite email contacts failed", e);
            return ApiResult.fail("获取常用联系人失败: " + e.getMessage());
        }
    }

    @GetMapping("/recent")
    public ApiResult<List<EmailRecipientSuggestionDto>> recent(@RequestParam(value = "limit", required = false) Integer limit,
                                                               @RequestParam(value = "emailAccountId", required = false) String emailAccountId) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailContactService.listRecent(userId, trimToNull(emailAccountId), safeLimit(limit)));
        } catch (Exception e) {
            log.error("query recent email contacts failed", e);
            return ApiResult.fail("获取最近联系人失败: " + e.getMessage());
        }
    }

    @GetMapping("/suggest")
    public ApiResult<List<EmailRecipientSuggestionDto>> suggest(@RequestParam("keyword") String keyword,
                                                                @RequestParam(value = "limit", required = false) Integer limit,
                                                                @RequestParam(value = "emailAccountId", required = false) String emailAccountId) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailContactService.suggest(userId, trimToNull(keyword), trimToNull(emailAccountId), safeLimit(limit)));
        } catch (Exception e) {
            log.error("suggest email contacts failed", e);
            return ApiResult.fail("获取联系人建议失败: " + e.getMessage());
        }
    }

    @PostMapping("/backfill")
    public ApiResult<EmailContactService.BackfillResult> backfill(@RequestBody(required = false) EmailContactBackfillRequest req) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailContactService.backfill(userId, getTenantCodeSafe(), req));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("backfill email contacts failed", e);
            return ApiResult.fail("回填联系人失败: " + e.getMessage());
        }
    }

    private String currentUserId() {
        return SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getUserId() : null;
    }

    private String getTenantCodeSafe() {
        return SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getTenantCode() : null;
    }

    private static int safeLimit(Integer limit) {
        return limit != null && limit > 0 ? limit : 20;
    }

    private static int[] parsePage(Map<String, Object> requestMap) {
        if (requestMap == null || requestMap.isEmpty()) {
            return new int[]{1, 20};
        }
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

    private static Integer parseInteger(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean parseBoolean(Object raw) {
        if (raw == null) {
            return false;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String value = String.valueOf(raw).trim();
        return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
