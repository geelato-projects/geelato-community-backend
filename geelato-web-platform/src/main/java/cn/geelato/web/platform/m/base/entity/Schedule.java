package cn.geelato.web.platform.m.base.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_schedule",catalog = "platform")
@Title(title = "应用")
public class Schedule extends BaseEntity {
    @Title(title = "所属应用")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "名称")
    private String name;
    @Title(title = "编码")
    private String code;
    @Title(title = "当type=java的时候，这个字段的值要  类名，方法名")
    @Col(name = "class_info")
    private String classInfo;
    @Title(title = "cron表达式")
    private String expression;
    @Title(title = "类型")
    private String type;
    @Title(title = "参数")
    private String params;
    @Title(title = "状态；0：停止，1：启动")
    private Integer status = 0;
    @Title(title = "描述")
    private String description;
    @Title(title = "重试次数")
    @Col(name = "retry_times")
    private Integer retryTimes;
    @Title(title = "冗余字段")
    private String extra;
}
