package cn.geelato.web.platform.m.settings.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;

import java.util.Date;

/**
 * 平台消息
 *
 * @author diabl
 */
@Setter
@Entity(name = "platform_message")
@Title(title = "消息")
public class Message extends BaseEntity {

    private String title;
    private String content;
    private String receiver;
    private String sender;
    private Date sendTime;
    private String sendMethod;

    @Title(title = "消息标题")
    @Col(name = "title")
    public String getTitle() {
        return title;
    }

    @Title(title = "消息内容")
    @Col(name = "content")
    public String getContent() {
        return content;
    }

    @Title(title = "接收者")
    @Col(name = "receiver")
    public String getReceiver() {
        return receiver;
    }

    @Title(title = "发送者")
    @Col(name = "sender")
    public String getSender() {
        return sender;
    }

    @Title(title = "发送时间")
    @Col(name = "sendTime")
    public Date getSendTime() {
        return sendTime;
    }

    @Title(title = "发送方式")
    @Col(name = "sendMethod")
    public String getSendMethod() {
        return sendMethod;
    }
}
