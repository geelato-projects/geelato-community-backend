package cn.geelato.web.platform.m.base.rest;

import cn.geelato.core.orm.Dao;
import cn.geelato.web.platform.annotation.ApiRestController;
import com.fasterxml.jackson.databind.ser.Serializers;
import org.bouncycastle.pqc.crypto.newhope.NHSecretKeyProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

@ApiRestController("/sk")
public class SqlKeyController extends BaseController {

    @RequestMapping("/{key}")
    public void exec(@PathVariable("key") String key, Map<String, Object> paramMap){
        dao.execute(key,paramMap);
    }
}
