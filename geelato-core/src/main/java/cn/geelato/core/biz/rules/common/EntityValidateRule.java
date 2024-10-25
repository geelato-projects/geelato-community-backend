package cn.geelato.core.biz.rules.common;


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
        return true;
    }

    @Override
    public void execute(Facts facts) throws Exception {
        //my rule actions
    }
}
