package cn.geelato.meta;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
@Entity(name = "platform_user_email_contact")
@TableName("platform_user_email_contact")
@Title(title = "用户邮件联系人")
public class UserEmailContact extends BaseEntity {
    @Title(title = "用户ID")
    @Col(name = "user_id", nullable = false)
    private String userId;

    @Title(title = "邮箱账号ID")
    @Col(name = "email_account_id")
    private String emailAccountId;

    @Title(title = "联系人名称")
    private String name;

    @Title(title = "邮箱地址")
    @Col(name = "email_address", nullable = false)
    private String emailAddress;

    @Title(title = "公司名称")
    @Col(name = "company_name")
    private String companyName;

    @Title(title = "备注")
    private String remark;

    @Title(title = "标签JSON")
    @Col(name = "tags_json")
    private String tagsJson;

    @Title(title = "常用联系人", description = "0否，1是")
    @Col(name = "favorite_flag")
    private int favoriteFlag;

    @Title(title = "来源类型", description = "manual/send/inbox_backfill")
    @Col(name = "source_type")
    private String sourceType;

    @Title(title = "最近发送时间")
    @Col(name = "last_sent_at")
    private Date lastSentAt;

    @Title(title = "最近接收时间")
    @Col(name = "last_received_at")
    private Date lastReceivedAt;

    @Title(title = "最近联系时间")
    @Col(name = "last_contact_at")
    private Date lastContactAt;

    @Title(title = "联系次数")
    @Col(name = "contact_count")
    private int contactCount;
}
