package cn.geelato.orm.support;

import cn.geelato.core.meta.MetaManager;
import cn.geelato.security.SecurityContext;
import cn.geelato.security.Tenant;
import cn.geelato.security.User;
import org.junit.Before;

public abstract class OrmTestSupport {

    @Before
    public void setUpOrmMetadata() {
        MetaManager.singleInstance().parseOne(TestUserEntity.class);
        MetaManager.singleInstance().parseOne(TestOrderEntity.class);

        User user = new User();
        user.setUserId("U1001");
        user.setUserName("orm-tester");
        user.setBuId("BU1");
        user.setOrgId("ORG1");
        user.setDefaultOrgId("ORG1");
        SecurityContext.setCurrentUser(user);
        SecurityContext.setCurrentTenant(new Tenant("geelato"));
    }
}
