package cn.geelato.web.platform.graal.service;

import com.alibaba.fastjson.JSON;
import cn.geelato.core.graal.GraalService;

@GraalService(name="json",built = "true")
public class JsonService {
    public String toString(Object object){
        return JSON.toJSONString(object);
    }
}
