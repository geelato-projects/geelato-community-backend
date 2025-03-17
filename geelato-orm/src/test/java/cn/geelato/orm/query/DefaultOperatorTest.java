package cn.geelato.orm.query;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.core.orm.Dao;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class DefaultOperatorTest {

    @Qualifier("dynamicDao")
    protected Dao dao;

    @Before
    public void setUp() throws Exception {
        MetaManager metaManager=MetaManager.singleInstance();
        metaManager.parseDBMeta(dao);
    }
    @Test
    public void query() {
        Operator operator=new DefaultOperator();
        List<Map<String, Object>> list= operator.query("demo_entity")
                .select("id")
                .where("id=1")
                .queryForMapList();
    }
}