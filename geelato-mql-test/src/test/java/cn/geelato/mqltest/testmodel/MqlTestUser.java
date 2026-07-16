package cn.geelato.mqltest.testmodel;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：用户（含 org 外键、自引用 pid 树、多种字段类型）
 */
@Entity(name = "mql_test_user", table = "mql_test_user")
public class MqlTestUser {

    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;

    @Title(title = "名称")
    @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
    private String name;

    @Title(title = "登录名")
    @Col(name = "login_name", dataType = "VARCHAR", charMaxlength = 64, unique = true)
    private String loginName;

    @Title(title = "邮箱")
    @Col(name = "email", dataType = "VARCHAR", charMaxlength = 128)
    private String email;

    @Title(title = "手机")
    @Col(name = "mobile_phone", dataType = "VARCHAR", charMaxlength = 32)
    private String mobilePhone;

    @Title(title = "年龄")
    @Col(name = "age", dataType = "INT")
    private Integer age;

    @Title(title = "积分")
    @Col(name = "score", dataType = "BIGINT")
    private Long score;

    @Title(title = "余额")
    @Col(name = "balance", dataType = "DECIMAL", numericPrecision = 12, numericScale = 2)
    private java.math.BigDecimal balance;

    @Title(title = "生日")
    @Col(name = "birthday", dataType = "DATE")
    private String birthday;

    @Title(title = "注册时间")
    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;

    @Title(title = "机构")
    @ForeignKey(fTable = MqlTestOrg.class)
    @Col(name = "org_id", dataType = "BIGINT")
    private String orgId;

    @Title(title = "父级用户")
    @Col(name = "pid", dataType = "BIGINT")
    private String pid;

    @Title(title = "启用状态")
    @Col(name = "enable_status", dataType = "INT")
    private Integer enableStatus;

    @Title(title = "创建人")
    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;

    @Title(title = "租户编码")
    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;
}
