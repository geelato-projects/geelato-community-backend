package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.utils.DateUtils;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity(name = "platform_schedule_log")
@Title(title = "调度日志")
public class ScheduleLog extends BaseEntity {
    @Title(title = "所属应用")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "所属调度")
    @Col(name = "schedule_id")
    private String scheduleId;
    @Title(title = "所属调度")
    @Col(name = "schedule_name")
    private String scheduleName;
    @Title(title = "调度信息：表达式，参数，重试次数")
    @Col(name = "schedule_info")
    private String scheduleInfo;
    @Title(title = "类型：开始，执行中，结束")
    private String type;
    @Title(title = "执行-开始时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "start_at")
    private Date startAt;
    @Title(title = "执行-结束时间")
    @JsonFormat(pattern = DateUtils.DATETIME, timezone = DateUtils.TIMEZONE)
    @Col(name = "finish_at")
    private Date finishAt;
    @Title(title = "状态：成功，失败")
    private Integer status;
    @Title(title = "执行-结果")
    private String result;
}
