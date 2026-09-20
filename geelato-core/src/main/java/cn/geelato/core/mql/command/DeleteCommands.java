package cn.geelato.core.mql.command;

import cn.geelato.core.GlobalContext;
import cn.geelato.core.SessionCtx;
import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.meta.model.entity.EntityMeta;
import cn.geelato.lang.meta.DeleteMode;
import cn.geelato.utils.DateUtils;
import org.springframework.util.Assert;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 删除命令的统一装配帮助：删除模式解析与逻辑删除默认字段填充的唯一事实来源。
 * <p>
 * 模式优先级（高到低）：
 * <ol>
 *     <li>调用级显式：MetaFactory {@code physicalDelete(...)} / MQL {@code @physicalDelete}</li>
 *     <li>实体级显式：{@code @Entity(deleteMode = LOGIC|PHYSICAL)}</li>
 *     <li>全局默认：GlobalContext.getDefaultDeleteMode()（环境变量 GEELATO_DELETE_MODE 类加载时读取一次固化，默认 LOGIC）</li>
 * </ol>
 */
public final class DeleteCommands {

    private DeleteCommands() {
    }

    /**
     * 解析删除操作的最终模式。
     *
     * @param em                实体元数据
     * @param physicalRequested 调用级显式物理删除标记（MetaFactory.physicalDelete(...)/MQL @physicalDelete）
     * @return LOGIC 或 PHYSICAL（永不返回 AUTO）
     * @throws IllegalArgumentException 最终模式为 LOGIC 但实体缺少 delStatus 字段时——
     *                                  逻辑删除无字段可置，属配置错误，直接失败并给出可操作提示
     */
    public static DeleteMode resolve(EntityMeta em, boolean physicalRequested) {
        Assert.notNull(em, "解析删除模式需要实体元数据 EntityMeta。");
        if (physicalRequested) {
            return DeleteMode.PHYSICAL;
        }
        DeleteMode mode = em.getDeleteMode() != null && em.getDeleteMode() != DeleteMode.AUTO
                ? em.getDeleteMode()
                : GlobalContext.getDefaultDeleteMode();
        if (mode == DeleteMode.LOGIC && !hasLogicDeleteField(em)) {
            throw new IllegalArgumentException(String.format(
                    "实体[%s]的删除模式解析为逻辑删除，但缺少逻辑删除字段%s。可按需选择：@Entity(deleteMode = PHYSICAL) 配置物理删除、"
                            + "调用方改用 MetaFactory.physicalDelete(...)（或 MQL @physicalDelete）、或将环境变量 GEELATO_DELETE_MODE 设为 physical 后重启。",
                    em.getEntityName(), ColumnDefault.DEL_STATUS_FIELD));
        }
        return mode;
    }

    /**
     * 实体是否含逻辑删除标记字段 delStatus。
     */
    public static boolean hasLogicDeleteField(EntityMeta em) {
        return em.containsField(ColumnDefault.DEL_STATUS_FIELD);
    }

    /**
     * 按逻辑删除语义填充默认字段（delStatus=已删除、deleteAt、updateAt、updater、updaterName），
     * 仅填充实体实际拥有的字段，并同步 command 的 fields/valueMap。
     * <p>
     * 调用前提：{@link #resolve} 已判定为 LOGIC（即实体必含 delStatus 字段）。
     */
    public static void fillLogicDeleteValues(DeleteCommand command, EntityMeta em) {
        Map<String, Object> params = new HashMap<>();
        String now = new SimpleDateFormat(DateUtils.DATETIME).format(new Date());
        if (em.containsField(ColumnDefault.DEL_STATUS_FIELD)) {
            params.put(ColumnDefault.DEL_STATUS_FIELD, DeleteStatusEnum.IS.getValue());
        }
        if (em.containsField(ColumnDefault.DELETE_AT_FIELD)) {
            params.put(ColumnDefault.DELETE_AT_FIELD, now);
        }
        if (em.containsField(ColumnDefault.UPDATE_AT_FIELD)) {
            params.put(ColumnDefault.UPDATE_AT_FIELD, now);
        }
        if (em.containsField(ColumnDefault.UPDATER_FIELD)) {
            params.put(ColumnDefault.UPDATER_FIELD, SessionCtx.getUserId());
        }
        if (em.containsField(ColumnDefault.UPDATER_NAME_FIELD)) {
            params.put(ColumnDefault.UPDATER_NAME_FIELD, SessionCtx.getUserName());
        }
        command.setValueMap(params);
        command.setFields(params.keySet().toArray(new String[0]));
    }
}
