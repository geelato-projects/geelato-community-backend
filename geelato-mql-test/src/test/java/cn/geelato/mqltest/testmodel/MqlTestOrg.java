package cn.geelato.mqltest.testmodel;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：组织机构（树形结构，自引用 pid）
 */
@Entity(name = "mql_test_org", table = "mql_test_org")
public class MqlTestOrg {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Title(title = "名称")
    @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
    private String name;

    @Title(title = "编码")
    @Col(name = "code", dataType = "VARCHAR", charMaxlength = 64, unique = true)
    private String code;

    @Title(title = "父级Id")
    @Col(name = "pid", dataType = "BIGINT")
    private String pid;

    @Title(title = "状态")
    @Col(name = "status", dataType = "VARCHAR", charMaxlength = 32)
    private String status;

    @Title(title = "创建时间")
    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;

    @Title(title = "创建人")
    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;

    @Title(title = "租户编码")
    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;
}
