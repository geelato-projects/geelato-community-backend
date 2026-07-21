package cn.geelato.meta;


import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * 用户委托关系表
 * <p>
 * 用于记录用户之间的委托/助理关系，委托人（user）可配置代理人（delegate_user）代为处理事务。
 * 一个委托人可配置多个代理人；同一委托人为同一代理人配置多个业务范围时，存为多行（每行业务范围单值）。
 * 业务唯一性：委托人(user_id) + 代理人(delegate_user_id) + 业务范围(scope) 三者组合不重复。
 * 其他业务模块可直接查询本表，按 user_id + scope + enable_status=1 获取有效的代理人列表。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_user_r_delegate", catalog = "platform")
@Title(title = "用户委托关系表")
public class UserDelegateMap extends BaseEntity implements EntityEnableAble {
    @Title(title = "委托人ID", description = "发起委托的用户ID")
    @ForeignKey(fTable = User.class)
    @Col(name = "user_id", refTables = "platform_user", refColName = "platform_user.id")
    private String userId;
    @Title(title = "委托人名称")
    @Col(name = "user_name", isRefColumn = true, refLocalCol = "userId", refColName = "platform_user.name")
    private String userName;
    @Title(title = "代理人ID", description = "被委托的用户ID，代为处理事务")
    @ForeignKey(fTable = User.class)
    @Col(name = "delegate_user_id", refTables = "platform_user", refColName = "platform_user.id")
    private String delegateUserId;
    @Title(title = "代理人名称")
    @Col(name = "delegate_user_name", isRefColumn = true, refLocalCol = "delegateUserId", refColName = "platform_user.name")
    private String delegateUserName;
    @Title(title = "代理人英文名")
    @Col(name = "delegate_user_en_name", isRefColumn = true, refLocalCol = "delegateUserId", refColName = "platform_user.en_name")
    private String delegateUserEnName;
    @Title(title = "关系类型", description = "默认 delegate，预留扩展")
    @Col(name = "relation_type", charMaxlength = 32)
    private String relationType = "delegate";
    @Title(title = "业务范围", description = "业务标识单值，如 periodic_report；一行一个业务范围")
    @Col(name = "scope", charMaxlength = 64)
    private String scope;
    @Title(title = "启用状态", description = "1表示启用、0表示未启用")
    @Col(name = "enable_status")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;
}
