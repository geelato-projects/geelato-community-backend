package cn.geelato.web.platform.srv.notice.entity;

import cn.geelato.lang.meta.Col;
import cn.geelato.lang.meta.Entity;
import cn.geelato.lang.meta.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity(name = "platform_notice", catalog = "platform")
@TableName("platform_notice")
@Title(title = "平台通知")
public class Notice extends BaseEntity {
    @Title(title = "收件人")
    private String receiver;
    
    @Title(title = "标题")
    @Col(name = "notice_title")
    private String noticeTitle;
    
    @Title(title = "内容")
    @Col(name = "notice_content")
    private String noticeContent;
    
    @Title(title = "发件人")
    private String sender;
    
    @Title(title = "业务关联")
    @Col(name = "biz_ref")
    private String bizRef;
    
    @Title(title = "状态")
    private String status;
    
    @Title(title = "部门信息")
    @Col(name = "dept_id")
    private String deptId;
    
    @Title(title = "企业信息")
    @Col(name = "bu_id")
    private String buId;
    
    @Title(title = "租户编码")
    @Col(name = "tenant_code")
    private String tenantCode;
}