package cn.geelato.web.platform.srv.announcement;

import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import cn.geelato.web.common.interceptor.annotation.AllowSystemAccess;
import cn.geelato.web.platform.srv.BaseController;
import cn.geelato.web.platform.srv.announcement.dto.AnnouncementInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 平台公告 REST：提供当前最新公告（markdown 原文）。
 * <p>公告源为配置目录（geelato.announcement.dir）内的 md 文件，通常由 git 定时拉取维护；
 * 已读状态由前端 localStorage（{公告id}_{userId}_read）维护，服务端不落库。</p>
 *
 * @author geelato
 */
@ApiRestController("/announcement")
@Slf4j
public class AnnouncementController extends BaseController {

    private final AnnouncementService announcementService;

    @Autowired
    public AnnouncementController(AnnouncementService announcementService) {
        this.announcementService = announcementService;
    }

    /**
     * 当前最新公告：返回目录内日期最新一份，无公告时 data 为 null。
     * 每次调用轻量重扫目录，git 拉取新公告后无需重启即生效。
     */
    @GetMapping("/current")
    public ApiResult<AnnouncementInfo> current() {
        try {
            return ApiResult.success(announcementService.getCurrentAnnouncement());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 强制刷新公告：清空缓存立即重扫，返回刷新后的最新公告（data 为 null 表示暂无）。
     * 标注 @AllowSystemAccess：供 git 拉取后的运维脚本以
     * Authorization: SystemToken <固定密钥> 调用，与站内信发送（/api/notification/send）
     * 同一鉴权方式；密钥配置 geelato.security.system-token.token（可用环境变量
     * GEELATO_SYSTEM_TOKEN 覆盖）。虽然 /current 每次调用都会轻量重扫，本接口
     * 供脚本在拉取后获得即时反馈与服务端刷新日志。
     */
    @AllowSystemAccess
    @PostMapping("/refresh")
    public ApiResult<AnnouncementInfo> refresh() {
        try {
            return ApiResult.success(announcementService.refresh());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }
}
