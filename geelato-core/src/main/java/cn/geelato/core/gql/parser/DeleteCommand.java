package cn.geelato.core.gql.parser;

import java.util.Map;

/**
 * 用于数据库delete操作
 * @author geemeta
 *
 */
public class DeleteCommand extends BaseCommand<DeleteCommand> {

    public DeleteCommand(){
        setCommandType(CommandType.Delete);
    }

    private Map<String, Object> valueMap;

    /**
     * 与fields同步，冗余
     */
    public Map<String, Object> getValueMap() {
        return valueMap;
    }

    public void setValueMap(Map<String, Object> valueMap) {
        this.valueMap = valueMap;
    }


}
