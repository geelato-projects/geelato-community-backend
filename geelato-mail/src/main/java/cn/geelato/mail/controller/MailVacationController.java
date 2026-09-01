package cn.geelato.mail.controller;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRuntimeRestController;
import cn.geelato.mail.service.MailVacationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 邮件假期自动回复配置 Controller（P3-V79，2 个端点）。
 *
 * 由 ApiPrefixAutoConfiguration 自动加 /api 前缀。实际路径 = /api/mail/vacation-reply。
 *
 * 端点列表：
 * - GET /vacation-reply   当前用户假期回复配置（未配置返回默认快照 enabled=false）
 * - PUT /vacation-reply   全量替换（upsert；长度/时间格式/时间先后非法 40000）
 *
 * 真实自动回复发送属引擎范畴（SMTP 通道 + 每发件人去重台账），本批仅配置持久化，
 * 见 MailFilterService 类尾设计说明。
 *
 * 数据隔离：全部按当前登录用户 userId 过滤（每用户至多一行）。
 *
 * 关联文档：.geelato/plans/2026-08-11-mail-backend-api.md
 */
@ApiRuntimeRestController("/mail/vacation-reply")
public class MailVacationController {

    @Autowired
    private MailVacationService vacationService;

    /** 当前用户假期回复配置 */
    @GetMapping
    public ApiResult<Map<String, Object>> get() {
        return ApiResult.success(vacationService.get());
    }

    /** 全量替换假期回复配置（前端提交完整 MailVacationReply 对象） */
    @PutMapping
    public ApiResult<Map<String, Object>> put(@RequestBody VacationRequest req) {
        try {
            vacationService.put(req.getEnabled(), req.getSubject(), req.getContent(),
                    req.getOnlyContacts(), req.getStartTime(), req.getEndTime());
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(40000, e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        return ApiResult.success(result);
    }

    /** 假期回复请求（与前端 MailVacationReply 对齐；lastSentAt 不接受客户端写入） */
    @lombok.Data
    public static class VacationRequest {
        private Boolean enabled;
        private String subject;
        private String content;
        private Boolean onlyContacts;
        private String startTime;
        private String endTime;
    }
}
