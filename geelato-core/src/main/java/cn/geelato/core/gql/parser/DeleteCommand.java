package cn.geelato.core.gql.parser;

import lombok.Getter;
import lombok.Setter;

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
    }

    /**
     * -- GETTER --
     *  与fields同步，冗余
     */
    private Map<String, Object> valueMap;


}
