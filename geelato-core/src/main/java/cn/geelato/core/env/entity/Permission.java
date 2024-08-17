package cn.geelato.core.env.entity;


import cn.geelato.core.Ctx;
import lombok.Getter;
import lombok.Setter;

@Getter
public class Permission {

    @Setter
    private String entity;

    private String  rule;

    private Integer weight;

    @Setter
    private Integer roleWeight;

    public void setRule(String rule) {
        this.rule = rule;

        switch (rule){
            case "creator=#currentUser.userId#":
                this.weight=1;
                break;
            case "dept_id=#currentUser.deptId#":
                this.weight=2;
                break;
            case "bu_id=#currentUser.buId#":
                this.weight=3;
                break;
            case "1=1":
                this.weight=4;
                break;
            default:
                this.weight=5;
        }
    }

    public String getRuleReplaceVariable(){
       return  this.rule.replace("#currentUser.userId#", String.format("'%s'",Ctx.getCurrentUser().getUserId()))
               .replace("#currentUser.deptId#",String.format("'%s'",Ctx.getCurrentUser().getDefaultOrgId()))
               .replace("#currentUser.buId#",String.format("'%s'",Ctx.getCurrentUser().getBuId()))
               .replace("#currentUser.cooperatingOrgId#",String.format("'%s'",Ctx.getCurrentUser().getCooperatingOrgId()));
    }

}
