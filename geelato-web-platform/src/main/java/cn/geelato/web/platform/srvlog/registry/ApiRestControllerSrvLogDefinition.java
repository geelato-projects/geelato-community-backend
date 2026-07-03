package cn.geelato.web.platform.srvlog.registry;

import lombok.Data;

import java.util.List;

@Data
public class ApiRestControllerSrvLogDefinition {
    private String controllerClass;
    private String methodName;
    private String handlerSignature;
    private List<String> methodKeyCandidates;
}

