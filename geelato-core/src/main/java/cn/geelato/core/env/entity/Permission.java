package cn.geelato.core.env.entity;


import cn.geelato.core.SessionCtx;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class Permission {

    private String entity;
    private String  rule;
    private Integer weight;
    private Integer roleWeight;

    public String getRuleReplaceVariable(){
       return  this.rule.replace("#currentUser.userId#", String.format("'%s'", SessionCtx.getCurrentUser().getUserId()))
               .replace("#currentUser.deptId#",String.format("'%s'", SessionCtx.getCurrentUser().getDefaultOrgId()))
               .replace("#currentUser.buId#",String.format("'%s'", SessionCtx.getCurrentUser().getBuId()))
               .replace("#currentUser.cooperatingOrgId#",String.format("'%s'", SessionCtx.getCurrentUser().getCooperatingOrgId()));
    }

}
