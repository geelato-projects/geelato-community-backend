package cn.geelato.orm.support;

import cn.geelato.core.meta.MetaManager;
import org.junit.jupiter.api.BeforeEach;

public abstract class OrmTestSupport {

    @BeforeEach
    public void setUpOrmMetadata() {
        MetaManager.singleInstance().parseOne(TestUserEntity.class);
        MetaManager.singleInstance().parseOne(TestOrderEntity.class);
    }
}
