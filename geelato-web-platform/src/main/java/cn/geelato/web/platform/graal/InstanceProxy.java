package cn.geelato.web.platform.graal;

import cn.geelato.web.platform.m.base.service.RuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InstanceProxy {

    private final RuleService ruleService;

    @Autowired
    public InstanceProxy(RuleService ruleService){
        this.ruleService=ruleService;
    }
    public RuleService getRuleService(){
        return  ruleService;
    }
}
