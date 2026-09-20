package cn.geelato.meta;

import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import lombok.Getter;
import lombok.Setter;

/**
 * ORM 实体钩子配置：为指定实体配置 insert/update/delete 三类 after-commit 异步钩子。
 * <p>
 * 纯附加特性、绝不影响业务链路：业务事务内零额外操作，事务真正提交后才触发，
 * 触发与执行全部异步，经发件箱 {@code platform_orm_hook_log} 重试/死信，失败永不上抛。
 * 事件语义：insert=新增提交后；update=更新提交后（逻辑删除以 Update 形式触发本事件，
 * values 含 del_status=1）；delete=物理删除提交后。
 * 动作类型 v2 起：script（脚本内容内嵌本表，不经 platform_api）与 http（直接调用 HTTP 接口）。
 * 发件箱执行时按 hook_id 回读<b>当前</b>配置（配置被删/禁用则死信），修改配置对未完成的
 * 重试立即生效。
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

    @Title(title = "实体名称", description = "目标实体的 entityName，须存在于元数据；禁止对钩子自身两张表配置")
    @Col(name = "entity_name", charMaxlength = 64, nullable = false)
    private String entityName;

    @Title(title = "事件类型", description = "insert | update | delete")
    @Col(name = "event_type", charMaxlength = 16, nullable = false)
    private String eventType;

    @Title(title = "动作类型", description = "script：内嵌脚本；http：调用HTTP接口")
    @Col(name = "action_type", charMaxlength = 16, nullable = false)
    private String actionType;

    @Title(title = "脚本内容", description = "script 动作的 JavaScript 函数体（function(){...}，parameter 在闭包中可用）")
    @Col(name = "script_content")
    private String scriptContent;

    @Title(title = "HTTP方法", description = "http 动作：GET | POST | PUT | DELETE | PATCH")
    @Col(name = "http_method", charMaxlength = 16)
    private String httpMethod;

    @Title(title = "HTTP地址", description = "http 动作的接口地址")
    @Col(name = "http_url", charMaxlength = 512)
    private String httpUrl;

    @Title(title = "HTTP请求头", description = "http 动作的请求头 JSON，如 {\"Authorization\":\"Bearer x\"}")
    @Col(name = "http_headers")
    private String httpHeaders;

    @Title(title = "HTTP请求体", description = "http 动作的请求体模板，支持 ${路径} 占位符（如 ${values.id}）；留空默认发送完整载荷 JSON")
    @Col(name = "http_body")
    private String httpBody;

    @Title(title = "是否启用", description = "0：禁用；1：启用")
    @Col(name = "enable_status")
    private int enableStatus = 1;
}
