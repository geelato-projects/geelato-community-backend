package cn.geelato.orm.adapter;

import cn.geelato.core.mql.command.CommandType;
import cn.geelato.core.mql.command.DeleteCommand;
import cn.geelato.core.mql.command.DeleteCommands;
import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.meta.DeleteMode;
import cn.geelato.orm.query.MetaDelete;
import org.springframework.util.Assert;

/**
 * MetaDelete 到 DeleteCommand 的适配器。
 * <p>
 * 删除模式解析（调用级 MetaFactory.physicalDelete(...) ＞ 实体级 @Entity(deleteMode) ＞ 全局默认）与
 * 逻辑删除默认字段填充统一收敛在 {@link DeleteCommands}。
 */
public final class DeleteCommandAdapter {

    private static final MetaManager META_MANAGER = MetaManager.singleInstance();

    private DeleteCommandAdapter() {
    }

    public static DeleteCommand from(MetaDelete delete) {
        DeleteCommand command = new DeleteCommand();
        String entityName = delete.resolveEntityName();
        EntityMeta em = META_MANAGER.getByEntityName(entityName);
        Assert.notNull(em, "未能通过entityName：" + entityName + ",获取元数据信息EntityMeta。");
        command.setEntityName(entityName);
        command.setConnectId(delete.getConnectId());
        command.setCommandType(CommandType.Delete);
        command.setWhere(FilterAdapter.adapt(delete.getFilters()));
        command.setDeleteMode(DeleteCommands.resolve(em, delete.isPhysical()));
        if (command.getDeleteMode() == DeleteMode.LOGIC) {
            DeleteCommands.fillLogicDeleteValues(command, em);
        }
        return command;
    }
}
