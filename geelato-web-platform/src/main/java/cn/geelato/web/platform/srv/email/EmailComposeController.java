package cn.geelato.web.platform.srv.email;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.security.SecurityContext;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.email.dto.EmailComposeContextDto;
import cn.geelato.web.platform.srv.email.dto.SendEmailRequest;
import cn.geelato.web.platform.srv.email.dto.SendEmailResult;
import cn.geelato.web.platform.srv.email.service.EmailComposeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@ApiRestController("/email")
@Slf4j
public class EmailComposeController extends BaseController {

    @Autowired
    private EmailComposeService emailComposeService;

    @PostMapping("/message/send")
    public ApiResult<SendEmailResult> send(@RequestBody SendEmailRequest req) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailComposeService.send(userId, getTenantCodeSafe(), req));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("send email failed", e);
            return ApiResult.fail("发送邮件失败: " + e.getMessage());
        }
    }

    @GetMapping("/message/{id}/composeContext")
    public ApiResult<EmailComposeContextDto> composeContext(@PathVariable("id") String id,
                                                            @RequestParam(value = "mode", required = false) String mode) {
        try {
            String userId = currentUserId();
            if (Strings.isBlank(userId)) {
                return ApiResult.fail("用户未登录");
            }
            return ApiResult.success(emailComposeService.buildComposeContext(userId, id, mode));
        } catch (IllegalArgumentException ex) {
            return ApiResult.fail(ex.getMessage());
        } catch (Exception e) {
            log.error("build compose context failed", e);
            return ApiResult.fail("获取回复上下文失败: " + e.getMessage());
        }
    }

    private String currentUserId() {
        return SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getUserId() : null;
    }

    private String getTenantCodeSafe() {
        return SecurityContext.getCurrentUser() != null ? SecurityContext.getCurrentUser().getTenantCode() : null;
    }
}
