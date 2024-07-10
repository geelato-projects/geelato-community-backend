package cn.geelato.core.meta;

import cn.geelato.core.meta.model.entity.DemoEntity;
import cn.geelato.core.meta.model.entity.EntityMeta;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.junit4.SpringRunner;

/**
 * @author geemeta
 */
@RunWith(SpringRunner.class)
public class MetaManagerTest {

    @Test
    public void parseOne() {
        MetaManager.singleInstance().parseOne(DemoEntity.class);
        EntityMeta entityMeta = MetaManager.singleInstance().get(DemoEntity.class);
        Assert.assertEquals("platform_demo_entity", entityMeta.getTableName());
        Assert.assertTrue(entityMeta.getFieldMetas().size() > 0);
    }
}