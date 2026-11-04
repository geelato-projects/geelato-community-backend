package cn.geelato.mqltest.model;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Id;
import cn.geelato.lang.meta.Title;

/**
 * 测试实体：组织机构（运行时用，供 explain/execute 端点）。
 */
@Entity(name = "mql_test_org", table = "mql_test_org")
public class MqlTestOrgModel {
    @Id
    @Col(name = "id", dataType = "BIGINT")
    private String id;
    @Col(name = "name", dataType = "VARCHAR", charMaxlength = 128)
    private String name;
    @Col(name = "code", dataType = "VARCHAR", charMaxlength = 64, unique = true)
    private String code;
    @Col(name = "pid", dataType = "BIGINT")
    private String pid;
    @Col(name = "status", dataType = "VARCHAR", charMaxlength = 32)
    private String status;
    @Col(name = "create_at", dataType = "DATETIME")
    private String createAt;
    @Col(name = "creator", dataType = "VARCHAR", charMaxlength = 64)
    private String creator;
    @Col(name = "tenant_code", dataType = "VARCHAR", charMaxlength = 64)
    private String tenantCode;
}
