package cn.geelato.core.biz.rules.common;

import cn.geelato.core.gql.parser.DeleteCommand;
import cn.geelato.core.gql.parser.SaveCommand;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.core.BasicRule;

/**
 * @author geemeta
 */
public class EntityValidateRule extends BasicRule {

    public EntityValidateRule() {
        super("EntityValidateRule", "实体校验规则。");
    }

    @Override
    public boolean evaluate(Facts facts) {
        SaveCommand saveCommand = (SaveCommand) facts.get("saveCommand");
        if (saveCommand != null) {
            saveCommand.getEntityName();
        } else {
            DeleteCommand deleteCommand = (DeleteCommand) facts.get("deleteCommand");
            if (deleteCommand != null) {
                deleteCommand.getEntityName();
            }
        }
        return true;
    }

    @Override
    public void execute(Facts facts) throws Exception {
        //my rule actions
    }
}
