package cn.geelato.core.biz.rules;


import lombok.Getter;
import lombok.Setter;
import org.apache.commons.codec.language.bm.Rule;

import javax.script.CompiledScript;

/**
 * @author geemeta
 * @description 一规则对应一function*
 */
@Getter
@Setter
public class BizRule {

    private String name;
    private CompiledScript script;
    private Rule rule;
}
