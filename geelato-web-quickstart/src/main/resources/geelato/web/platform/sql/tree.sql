/**
* 格式说明：每条语句之间必须用注释“@sql ”进行分割，@sql后跟sqlId
*/

-- 基于platform_tree_node，支持动态业务表组合
-- @sql select_tree_node_left_join
select
  tn.id          tn_id,
  tn.text        tn_text,
  tn.parent      tn_parent,
  tn.icon        tn_icon,
  tn.`type`      tn_type,
  tn.tree_entity tn_tree_entity,
  tn.meta        tn_meta,
  tn.url         tn_url,
  t.*
from platform_tree_node tn left join $.tableName t on tn.id = t.tree_node_id
where tn.tree_id = $.treeId

-- @sql select_platform_menu
SELECT
    tn.id,
    tn.`TEXT` name,
    tn.parent parentId,
    tn.`type` component,
    tn.icon icon,
    tn.meta meta,
    tn.url url,
    tn.tree_id treeId
FROM platform_tree_node tn,platform_app_user app
WHERE tn.tree_id = app.id AND app.user_id=$.userId AND tn.flag='menuItem'
UNION
SELECT tn.id,tn.`TEXT` name,tn.parent parentId,tn.`type` component,tn.icon icon,tn.meta,tn.tree_id treeId
FROM platform_tree_node tn,platform_app_user app
WHERE tn.tree_id = app.id AND app.user_id=$.userId AND tn.id IN (SELECT tn.parent parentId FROM platform_tree_node tn,platform_app_user app
WHERE tn.tree_id = app.id AND app.user_id=$.userId AND tn.flag='menuItem')
UNION
SELECT id,`name`,'0' parentId,'default' component,icon,'' meta,id treeId
FROM platform_app_user au
WHERE au.user_id=$.userId
ORDER BY treeId,parentId ASC;

-- @sql select_platform_tree_node_app_page
SELECT
  p1.id,
  p1.pid,
  p2.id as appId,
  p3.id as pageId,
  p1.flag,
  p1.icon,
  p1.icon_type as iconType,
  p1.type,
  p1.meta,
  p1.tree_entity as treeEntity,
  p1.extend_entity as extendEntity,
  p1.text,
  p1.seq_no as seqNo,
  p1.tenant_code as tenantCode,
  p1.url
FROM platform_tree_node p1
LEFT JOIN platform_app p2 ON p2.id = p1.tree_id
LEFT JOIN platform_app_page p3 ON p3.extend_id = p1.id AND p3.app_id = p2.id
WHERE 1=1 AND p1.del_status = 0 AND p2.del_status = 0
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode' AND p2.tenant_code = '$.tenantCode'
@/if
@if $.appId!=null&&$.appId!=''
  AND p2.id = '$.appId'
@/if
@if $.flag!=null&&$.flag!=''
  AND p1.flag = '$.flag'
@/if
@if $.currentUser!=null&&$.currentUser!=''
  and p1.id in (SELECT r1.tree_node_id FROM platform_role_r_tree_node r1
      LEFT JOIN platform_role_r_user r2 ON r1.role_id = r2.role_id
      WHERE 1=1 AND r1.del_status = 0 AND r2.del_status = 0
      AND r2.user_id = '$.currentUser')
@/if
ORDER BY p2.seq_no ASC,p1.seq_no ASC

-- @sql query_tree_platform_org
SELECT
    p1.*,
    IF(p2.total > 0,0,1) isLeaf
FROM platform_org p1
LEFT JOIN (
SELECT count(*) total,pid FROM platform_org WHERE del_status = 0
@if $.status!=null&&$.status!=''
  AND status = '$.status'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND tenant_code = '$.tenantCode'
@/if
GROUP BY pid) p2 ON p2.pid = p1.id
WHERE 1=1 AND p1.del_status = 0
@if $.pid=='root'
  AND (p1.pid = '' or p1.pid is null)
@/if
@if $.pid!=null&&$.pid!=''&&$.pid!='root'
  AND p1.pid = '$.pid'
@/if
@if $.id!=null&&$.id!=''
  AND FIND_IN_SET(p1.id,'$.id')
@/if
@if $.status!=null&&$.status!=''
  AND p1.status = '$.status'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
ORDER BY p1.seq_no ASC

-- @sql query_app_by_role_user
SELECT DISTINCT p1.* from platform_app p1
LEFT JOIN platform_role p2 ON p2.app_id = p1.id
LEFT JOIN platform_role_r_user p3 ON p3.role_id = p2.id
LEFT JOIN platform_user p4 ON p4.id = p3.user_id
WHERE 1=1
  AND p1.del_status = 0
  AND p2.del_status = 0
  AND p3.del_status = 0
  AND p4.del_status = 0
  AND p2.enable_status = 1
  AND p1.tenant_code = '$.tenantCode'
  AND p4.id = '$.userId'
ORDER BY p1.seq_no ASC

-- @sql query_permission_by_role_user
SELECT
  DISTINCT p1.*,
           p3.id as roleId,
           p3.name as roleName,
           p3.code as roleCode
FROM platform_permission p1
       LEFT JOIN platform_role_r_permission p2 ON p2.permission_id = p1.id
       LEFT JOIN platform_role p3 ON p3.id = p2.role_id
       LEFT JOIN platform_role_r_user p4 ON p4.role_id = p3.id
       LEFT JOIN platform_user p5 ON p5.id = p4.user_id
WHERE 1=1
  AND p1.del_status = 0
  AND p2.del_status = 0
  AND p3.del_status = 0
  AND p4.del_status = 0
  AND p5.del_status = 0
  AND p3.enable_status = 1
  AND p5.id = '$.userId'
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.appId!=null&&$.appId!=''
  AND p1.app_id = '$.appId'
@/if
@if $.type!=null&&$.type!=''
  AND p1.type = '$.type'
@/if
@if $.object!=null&&$.object!=''
  AND p1.object = '$.object'
@/if



-- @sql query_app_by_role_user
SELECT DISTINCT p1.* from platform_app p1
                              LEFT JOIN platform_role p2 ON p2.app_id = p1.id
                              LEFT JOIN platform_role_r_user p3 ON p3.role_id = p2.id
                              LEFT JOIN platform_user p4 ON p4.id = p3.user_id
WHERE 1=1
  AND p1.del_status = 0
  AND p2.del_status = 0
  AND p3.del_status = 0
  AND p4.del_status = 0
  AND p2.enable_status = 1
  AND p1.tenant_code = '$.tenantCode'
  AND p4.id = '$.userId'
ORDER BY p1.seq_no ASC

-- 只查询code和rule两字段
-- @sql query_permission_code_and_rule_by_role_user
SELECT
    DISTINCT p1.code,
             p1.rule
FROM platform_permission p1
         LEFT JOIN platform_role_r_permission p2 ON p2.permission_id = p1.id
         LEFT JOIN platform_role p3 ON p3.id = p2.role_id
         LEFT JOIN platform_role_r_user p4 ON p4.role_id = p3.id
         LEFT JOIN platform_user p5 ON p5.id = p4.user_id
WHERE 1=1
  AND p1.del_status = 0
  AND p2.del_status = 0
  AND p3.del_status = 0
  AND p4.del_status = 0
  AND p5.del_status = 0
  AND p3.enable_status = 1
  AND p5.id = '$.userId'
    @if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.appId!=null&&$.appId!=''
  AND p1.app_id = '$.appId'
@/if
@if $.type!=null&&$.type!=''
  AND p1.type = '$.type'
@/if
@if $.object!=null&&$.object!=''
  AND p1.object = '$.object'
@/if