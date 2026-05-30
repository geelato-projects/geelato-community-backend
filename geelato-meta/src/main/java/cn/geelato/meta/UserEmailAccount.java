package cn.geelato.meta;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.BaseSortableEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_user_email_account", catalog = "platform")
@TableName("platform_user_email_account")
@Title(title = "用户邮箱账号")
public class UserEmailAccount extends BaseEntity implements EntityEnableAble {
    @Title(title = "用户ID")
    @Col(name = "user_id", nullable = false)
    private String userId;

    @Title(title = "邮箱地址")
    @Col(name = "email_address", nullable = false)
    private String emailAddress;

    @Title(title = "显示名称")
    @Col(name = "display_name")
    private String displayName;

    @Title(title = "默认邮箱", description = "0否，1是")
    @Col(name = "default_flag")
    private int defaultFlag;

    @Title(title = "IMAP Host")
    @Col(name = "imap_host")
    private String imapHost;

    @Title(title = "IMAP 端口")
    @Col(name = "imap_port")
    private Integer imapPort;

    @Title(title = "IMAP SSL", description = "0否，1是")
    @Col(name = "imap_ssl")
    private int imapSsl;

    @Title(title = "默认文件夹")
    @Col(name = "imap_folder_default")
    private String imapFolderDefault;

    @Title(title = "认证类型", description = "auth_code/password/oauth2")
    @Col(name = "auth_type")
    private String authType;

    @Title(title = "认证用户名")
    @Col(name = "auth_user")
    private String authUser;

    @Title(title = "认证密文", description = "授权码/密码密文")
    @Col(name = "auth_secret")
    private String authSecret;

    @Title(title = "OAuth2 信息", description = "JSON")
    @Col(name = "oauth2_json")
    private String oauth2Json;

    @Title(title = "启用状态", description = "1启用，0不启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
}

