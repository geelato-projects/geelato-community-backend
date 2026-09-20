package cn.geelato.archive.engine.controller;

import cn.geelato.archive.config.ArchiveProperties;
import cn.geelato.archive.engine.trigger.ArchiveScheduler;
import cn.geelato.lang.api.ApiResult;
import cn.geelato.web.common.annotation.ApiRestController;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 归档调度器控制接口：引擎默认日常静默，需要定时归档时随时"run 起来"，不需要时随时停。
 * <p>start/stop 为内存态运行时操作（重启后回到 geelato.archive.scheduler-enabled 配置默认）。</p>
 */
@ApiRestController("/archive/scheduler")
@Slf4j
public class ArchiveSchedulerController {

    private final ArchiveScheduler scheduler;
    private final ArchiveProperties properties;

    @Autowired
    public ArchiveSchedulerController(ArchiveScheduler scheduler, ArchiveProperties properties) {
        this.scheduler = scheduler;
        this.properties = properties;
    }

    /** 启动每日定时调度（幂等；按 schedule-time 执行，已过则次日） */
    @RequestMapping(value = "/start", method = RequestMethod.POST)
    public ApiResult<String> start() {
        try {
            scheduler.start();
            return ApiResult.success("归档调度器已启动，每日 " + properties.getScheduleTime() + " 执行，下次触发："
                    + scheduler.formatNextFireAt());
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 停止定时调度（正在执行的当前策略会跑完；手动触发不受影响） */
    @RequestMapping(value = "/stop", method = RequestMethod.POST)
    public ApiResult<String> stop() {
        try {
            scheduler.stop();
            return ApiResult.success("归档调度器已停止（手动触发不受影响，可随时再次启动）");
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return ApiResult.fail(e.getMessage());
        }
    }

    /** 调度器状态：running、下次触发时刻、配置的执行时刻、随应用启动配置 */
    @RequestMapping(value = "/status", method = RequestMethod.GET)
    public ApiResult<Map<String, Object>> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("running", scheduler.isRunning());
        status.put("nextFireAt", scheduler.formatNextFireAt());
        status.put("scheduleTime", properties.getScheduleTime().toString());
        status.put("schedulerEnabledOnStartup", properties.isSchedulerEnabled());
        return ApiResult.success(status);
    }
}
