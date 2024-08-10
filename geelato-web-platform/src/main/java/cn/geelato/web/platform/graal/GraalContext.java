package cn.geelato.web.platform.graal;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class GraalContext {

    public GraalContext(Object result){
        this.result=result;
    }

    private Object result;

}
