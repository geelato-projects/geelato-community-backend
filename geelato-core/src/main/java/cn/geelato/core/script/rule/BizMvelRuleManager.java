package cn.geelato.core.script.rule;

import org.apache.commons.collections.map.HashedMap;
import cn.geelato.core.script.AbstractScriptManager;
import org.jeasy.rules.api.Rule;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.mvel.MVELRuleFactory;

import java.io.*;

/**
 * @author geemeta
 */
public class BizMvelRuleManager extends AbstractScriptManager {
    private final HashedMap ruleMap = new HashedMap();


    public void getRule(String ruleCode) {
        ruleMap.get(ruleCode);
    }

    @Override
    public void loadDb() {
        //暂时不知道用法
//        if (StringUtils.isEmpty(sqlId)) {
//            List<Map<String, Object>> mapList = dao.queryForMapList(Resources.class, "type", "biz-rule-mvel");
//            for (Map<String, Object> map : mapList) {
//                String ruleMvel = map.get("content").toString();
//                Rules rules = MVELRuleFactory.createRulesFrom(new StringReader(ruleMvel));
//                for (Rule rule : rules) {
//                    ruleMap.put(rule.getName(), rule);
//                }
//            }
//        }
    }

    @Override
    public void parseFile(File file) throws IOException {
        //TODO 若是mvel、若是目录
        Rules rules = MVELRuleFactory.createRulesFrom(new FileReader(file));
        for (Rule rule : rules) {
            ruleMap.put(rule.getName(), rule);
        }
    }

    @Override
    public void parseStream(InputStream is) throws IOException {

    }
}
