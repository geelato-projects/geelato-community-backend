package cn.geelato.web.platform.m.security.entity;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_user_r_stock")
@Title(title = "用户常用联系人")
public class UserStockMap extends BaseEntity {
    @Title(title = "用户ID")
    @Col(name = "user_id")
    private String userId;
    @Title(title = "联系人ID")
    @Col(name = "stock_id")
    private String stockId;
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
}
