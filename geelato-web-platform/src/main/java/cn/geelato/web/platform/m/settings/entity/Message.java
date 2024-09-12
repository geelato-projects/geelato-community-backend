package cn.geelato.web.platform.m.settings.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

/**
 * 平台消息
 *
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_message")
@Title(title = "消息")
public class Message extends BaseEntity {
    @Title(title = "消息标题")
    private String title;
    @Title(title = "消息内容")
    private String content;
    @Title(title = "接收者")
    private String receiver;
    @Title(title = "发送者")
    private String sender;
    @Title(title = "发送时间")
    @Col(name = "sendTime")
    private Date sendTime;
    @Title(title = "发送方式")
    @Col(name = "sendMethod")
    private String sendMethod;
}
