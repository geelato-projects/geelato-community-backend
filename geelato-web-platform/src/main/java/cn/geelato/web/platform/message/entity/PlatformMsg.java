package cn.geelato.web.platform.message.entity;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

/**
 * 平台消息实体类
 */
@Getter
@Setter
@Entity(name = "platform_msg", catalog = "platform")
@Title(title = "平台消息")
@TableName("platform_msg")
public class PlatformMsg extends BaseEntity {
    private String title;

    private String content;

    private String sender;

    private String receiver;

    private String type;

    private String buss;

    private String status;

    private String channel;
}