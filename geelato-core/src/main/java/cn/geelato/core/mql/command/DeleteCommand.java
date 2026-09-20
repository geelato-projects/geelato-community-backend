package cn.geelato.core.mql.command;

import cn.geelato.lang.meta.DeleteMode;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

/**
 * 用于数据库delete操作
 * @author geemeta
 *
 */
@Setter
@Getter
public class DeleteCommand extends BaseCommand<DeleteCommand> {

    public DeleteCommand(){
        setCommandType(CommandType.Delete);
        this.valueMap = new HashMap<>();
        setFields(new String[0]);
    }

    /**
     * -- GETTER --
     *  与fields同步，冗余
     */
    private Map<String, Object> valueMap;

    private DeleteMode deleteMode;


}
