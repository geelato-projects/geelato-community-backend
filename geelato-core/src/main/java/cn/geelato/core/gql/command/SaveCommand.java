package cn.geelato.core.gql.command;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 用于数据库insert、update操作
 * @author geemeta
 *
 */
@Setter
@Getter
public class SaveCommand extends BaseCommand<SaveCommand> {

    // 主键的值，如19位的数字
    private String PK;
    /**
     * -- GETTER --
     *  与fields同步，冗余。
     *
     */
    private Map<String, Object> valueMap;

    private Map<String,Object> originValueMap;

}
