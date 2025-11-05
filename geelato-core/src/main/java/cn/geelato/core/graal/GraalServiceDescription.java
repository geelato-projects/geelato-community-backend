package cn.geelato.core.graal;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class GraalServiceDescription {
    private String serviceName;

    private String description;

    private List<GraalFunctionDescription> functions;


}
