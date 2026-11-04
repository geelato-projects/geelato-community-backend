package cn.geelato.mqltest.model;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.ForeignKey;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：用户（运行时用）。
 */
@Entity(name = "mql_test_user", table = "mql_test_user")
public class MqlTestUserModel {
    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;
    @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
    private String name;
    @Col(name = "login_name", dataType = "VARCHAR", charMaxlength = 64, unique = true)
    private String loginName;
    @Col(name = "email", dataType = "VARCHAR", charMaxlength = 128)
    private String email;
    @Col(name = "mobile_phone", dataType = "VARCHAR", charMaxlength = 32)
    private String mobilePhone;
    @Col(name = "age", dataType = "INT")
    private Integer age;
    @Col(name = "score", dataType = "BIGINT")
    private Long score;
    @Col(name = "balance", dataType = "DECIMAL", numericPrecision = 12, numericScale = 2)
    private java.math.BigDecimal balance;
    @Col(name = "birthday", dataType = "DATE")
    private String birthday;
    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;
    @ForeignKey(fTable = MqlTestOrgModel.class)
    @Col(name = "org_id", dataType = "BIGINT")
    private String orgId;
    @Col(name = "pid", dataType = "BIGINT")
    private String pid;
    @Col(name = "enable_status", dataType = "INT")
    private Integer enableStatus;
    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;
    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;
}
