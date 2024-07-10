package cn.geelato.core.biz.rules;

import org.jeasy.rules.api.Facts;
import org.jeasy.rules.core.BasicRule;

/**
 * @author geemeta
 *
 */
public class JavaScriptRule extends BasicRule {

    public JavaScriptRule() {
        super("JavaScriptRule", "通用javascript脚本规则。");
    }

    @Override
    public boolean evaluate(Facts facts) {
        //my rule conditions
        return true;
    }

    @Override
    public void execute(Facts facts) throws Exception {
        //my rule actions
    }
}
