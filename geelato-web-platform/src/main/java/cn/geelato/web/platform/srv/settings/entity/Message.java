package cn.geelato.web.platform.srv.settings.entity;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
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
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "消息标题")
    private String title;
    @Title(title = "消息内容")
    private String content;
    @Title(title = "接收者")
    private String receiver;
    @Title(title = "发送者")
    private String sender;
    @Title(title = "发送时间")
    @Col(name = "send_time")
    private Date sendTime;
    @Title(title = "发送方式")
    @Col(name = "send_method")
    private String sendMethod;
    @Title(title = "发送类别")
    @Col(name = "send_type")
    private String sendType;
    @Title(title = "发送状态", description = "发送状态，0：未发送、1：发送成功、2：发送失败、3：已读")
    @Col(name = "send_status")
    private int sendStatus;
}
