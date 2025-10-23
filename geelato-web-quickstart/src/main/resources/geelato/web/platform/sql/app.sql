-- @sql platform_permission_by_app_page
SELECT
  p1.id,
  p1.name,
  p1.code,
  p1.type,
  p1.object,
  p1.rule,
  p1.description,
  p2.extend_id as treeId
FROM platform_permission p1
LEFT JOIN platform_app_page p2 ON p2.id = p1.object
WHERE 1=1
AND p1.del_status = 0 AND p2.del_status = 0
@if $.appId!=null&&$.appId!=''
  AND p1.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.type!=null&&$.type!=''
  AND p1.type = '$.type'
@/if
@if $.treeId!=null&&$.treeId!=''
  AND p2.extend_id = '$.treeId'
@/if

-- @sql platform_role_r_permission_by_app_page
SELECT
    p1.id as id,
    p1.app_id as appId,
    p1.role_id as roleId,
    p1.role_name as roleName,
    p1.permission_id as permissionId,
    p1.permission_name as permissionName
FROM platform_role_r_permission p1
LEFT JOIN platform_role p2 ON p2.id = p1.role_id
LEFT JOIN platform_permission p3 ON p3.id = p1.permission_id
LEFT JOIN platform_app_page p4 ON p4.id = p3.object
WHERE 1=1 AND p1.del_status = 0
AND p4.del_status = 0 AND p3.del_status = 0
@if $.type!=null&&$.type!=''
  AND p3.type = '$.type'
@/if
@if $.roleId!=null&&$.roleId!=''
  @if $.roleId.indexOf(',')>-1
    AND p1.role_id IN ($.roleId)
  @/if
  @if $.roleId.indexOf(',')==-1
    AND p2.id = '$.roleId'
  @/if
@/if
@if $.appId!=null&&$.appId!=''
  AND p1.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if


-- 查询应用，角色和用户关联
-- @sql query_app_by_role_user
SELECT DISTINCT * FROM (
   SELECT DISTINCT
       p1.*
   FROM platform_app p1
   LEFT JOIN platform_role p2 ON p2.app_id = p1.id
   LEFT JOIN platform_role_r_user p3 ON p3.role_id = p2.id
   LEFT JOIN platform_user p4 ON p4.id = p3.user_id
   WHERE 1=1
     AND p1.del_status = 0
     AND p2.del_status = 0
     AND p3.del_status = 0
     AND p4.del_status = 0
     AND p2.enable_status = 1
     AND p2.type = 'app'
     AND p1.apply_status = 1
     AND p1.tenant_code = '$.tenantCode'
     AND p4.id = '$.userId'
   UNION ALL
   SELECT DISTINCT
       p1.*
   FROM platform_app p1
   LEFT JOIN platform_role_r_app p5 ON p5.app_id = p1.id
   LEFT JOIN platform_role p2 ON p2.id = p5.role_id
   LEFT JOIN platform_role_r_user p3 ON p3.role_id = p2.id
   LEFT JOIN platform_user p4 ON p4.id = p3.user_id
   WHERE 1=1
     AND p1.del_status = 0
     AND p2.del_status = 0
     AND p3.del_status = 0
     AND p4.del_status = 0
     AND p5.del_status = 0
     AND p2.enable_status = 1
     AND p2.type = 'platform'
     AND p1.apply_status = 1
     AND p1.tenant_code = '$.tenantCode'
     AND p4.id = '$.userId'
)t ORDER BY type ASC, seq_no ASC

-- 查询权限，角色和用户关联
-- @sql query_permission_by_role_user
SELECT DISTINCT
    p1.*,
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

-- 只查询code和rule两字段
-- @sql query_permission_code_and_rule_by_role_user
SELECT DISTINCT
    t2.code,
    t2.rule,
    t2.name
from platform_role_r_permission t1
left join platform_permission t2 on t1.permission_id =t2.id
left join platform_role_r_user t4 on t4.role_id =t1.role_id
left join platform_role t3 on t4.role_id =t3.id
left join platform_user t5 on t5.id =t4.user_id
WHERE 1=1
  AND t1.del_status = 0
  AND t2.del_status = 0
  AND t3.del_status = 0
  AND t4.del_status = 0
  AND t5.del_status = 0
  AND t3.enable_status = 1
  AND t5.id = '$.userId'
@if $.tenantCode!=null&&$.tenantCode!=''
  AND t2.tenant_code = '$.tenantCode'
@/if
@if $.appId!=null&&$.appId!=''
  AND t2.app_id = '$.appId'
@/if
@if $.type!=null&&$.type!=''
  AND t2.type = '$.type'
@/if
@if $.object!=null&&$.object!=''
  AND t2.object = '$.object'
@/if
