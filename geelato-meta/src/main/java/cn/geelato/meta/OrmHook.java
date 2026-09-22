package cn.geelato.meta;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * ORM 实体钩子配置：一条 Hook = 名称 + 地址 + 启停。
 * <p>
 * 平台内任何实体的 insert/update/delete 事务提交后，向地址 POST 完整载荷 JSON，
 * 下游自行按 entityName/eventType 分发处理。纯附加特性、绝不影响业务链路：
 * 业务事务内零额外操作，触发与执行全部异步，经发件箱 {@code platform_orm_hook_log}
 * 重试/死信，失败永不上抛。钩子机制自身的两张表不触发（防递归）。
 *
 * @author geelato
 */
@Getter
@Setter
@Entity(name = "platform_orm_hook", catalog = "platform")
@Title(title = "ORM实体钩子配置")
public class OrmHook extends BaseEntity {

    @Title(title = "钩子名称")
    @Col(name = "title", charMaxlength = 128, nullable = false)
    private String title;

    @Title(title = "地址", description = "下游系统接收地址：任何实体事件提交后 POST 该地址")
    @Col(name = "http_url", charMaxlength = 512, nullable = false)
    private String httpUrl;

    @Title(title = "是否启用", description = "0：禁用；1：启用")
    @Col(name = "enable_status")
    private int enableStatus = 1;
}
