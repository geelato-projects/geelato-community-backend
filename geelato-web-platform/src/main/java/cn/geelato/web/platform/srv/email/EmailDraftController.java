package cn.geelato.web.platform.srv.email;

import cn.geelato.lang.api.ApiPagedResult;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.email.dto.EmailDraftDto;
import cn.geelato.web.platform.srv.email.dto.EmailDraftUpsertRequest;
import cn.geelato.web.platform.srv.email.dto.SendEmailResult;
import cn.geelato.web.platform.srv.email.service.EmailComposeService;
import cn.geelato.web.platform.srv.email.service.EmailDraftService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@ApiRestController("/email/draft")
@Slf4j
public class EmailDraftController extends BaseController {

    @Autowired
    private EmailDraftService emailDraftService;

    @Autowired
    private EmailComposeService emailComposeService;

    @PostMapping("/pageQuery")
    public ApiPagedResult<List<EmailDraftDto>> pageQuery(@RequestBody(required = false) Map<String, Object> requestMap) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiPagedResult.fail("用户未登录");
            }
            Map<String, Object> rm = requestMap != null ? requestMap : Collections.emptyMap();
            int[] page = parsePage(rm);
            String emailAccountId = trimToNull(Objects.toString(rm.get("emailAccountId"), null));
            String keyword = trimToNull(Objects.toString(rm.get("keyword"), null));
            EmailDraftService.PageResult<EmailDraftDto> result = emailDraftService.pageQuery(userId, emailAccountId, keyword, page[0], page[1]);
            List<EmailDraftDto> list = result.data();
            return ApiPagedResult.success(list, page[0], page[1], list != null ? list.size() : 0, result.total());
        } catch (Exception e) {
            log.error("pageQuery email draft failed", e);
            return ApiPagedResult.fail("获取草稿列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ApiResult<EmailDraftDto> get(@PathVariable("id") String id) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            EmailDraftDto dto = emailDraftService.get(userId, id);
            if (dto == null) {
                return ApiResult.fail("草稿不存在");
            }
            return ApiResult.success(dto);
        } catch (Exception e) {
            log.error("get email draft failed", e);
            return ApiResult.fail("获取草稿失败: " + e.getMessage());
        }
    }

    @PostMapping("/createOrUpdate")
    public ApiResult<String> createOrUpdate(@RequestBody EmailDraftUpsertRequest req) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailDraftService.createOrUpdate(userId, getTenantCodeSafe(), req));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("save email draft failed", e);
            return ApiResult.fail("保存草稿失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ApiResult<Boolean> delete(@PathVariable("id") String id) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailDraftService.remove(userId, id));
        } catch (Exception e) {
            log.error("delete email draft failed", e);
            return ApiResult.fail("删除草稿失败: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/send")
    public ApiResult<SendEmailResult> send(@PathVariable("id") String id) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailComposeService.send(userId, getTenantCodeSafe(), emailDraftService.toSendRequest(userId, id)));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("send email draft failed", e);
            return ApiResult.fail("发送草稿失败: " + e.getMessage());
        }
    }

    private String currentUserId() {
        return SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getUserId() : null;
    }

    private String getTenantCodeSafe() {
        return SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getTenantCode() : null;
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

    private static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
