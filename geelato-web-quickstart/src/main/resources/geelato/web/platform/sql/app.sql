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
  AND p2.id = '$.roleId'
@/if
@if $.appId!=null&&$.appId!=''
  AND p1.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if