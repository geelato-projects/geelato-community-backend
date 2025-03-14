-- @sql page_query_platform_role_r_app
SELECT
    p1.id,
    p1.app_id as appId,
    p1.tenant_code as tenantCode,
    p1.role_id as roleId,
    p1.update_at as updateAt,
    p1.updater,
    p1.updater_name as updaterName,
    p1.create_at as createAt,
    p1.creator,
    p1.creator_name as creatorName,
    p2.name as roleName,
    p2.code as roleCode,
    p2.type as roleType,
    p2.weight as roleWeight,
    p2.enable_status as roleEnableStatus,
    p2.seq_no as roleSeqNo,
    p2.description as roleDescription,
    p3.logo as appLogo,
    p3.name as appName,
    p3.code as appCode,
    p3.type as appType,
    p3.icon as appIcon,
    p3.purpose as appPurpose,
    p3.watermark as appWaterMark,
    p3.apply_status as appApplyStatus,
    p3.design_status as appDesignStatus,
    p3.seq_no as appSeqNo,
    p3.description as appDescription
FROM platform_role_r_app p1
LEFT JOIN platform_role p2 ON p2.id = p1.role_id
LEFT JOIN platform_app p3 ON p3.id = p1.app_id
WHERE 1=1 AND p1.del_status = 0
    @if $.roleId!=null&&$.roleId!=''
  AND p1.role_id = '$.roleId'
@/if
@if $.appId!=null&&$.appId!=''
  AND p1.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.roleName!=null&&$.roleName!=''
  AND p2.name like '%$.roleName%'
@/if
@if $.roleCode!=null&&$.roleCode!=''
  AND p2.code like '%$.roleCode%'
@/if
@if $.roleType!=null&&$.roleType!=''
  AND p2.type = '$.roleType'
@/if
@if $.roleWeight!=null&&$.roleWeight!=''
  AND p2.weight = '$.roleWeight'
@/if
@if $.roleEnableStatus!=null&&$.roleEnableStatus!=''
  AND p2.enable_status = '$.roleEnableStatus'
@/if
@if $.appName!=null&&$.appName!=''
  AND p3.name like '%$.appName%'
@/if
@if $.appCode!=null&&$.appCode!=''
  AND p3.code like '%$.appCode%'
@/if
@if $.appType!=null&&$.appType!=''
  AND p3.type = '$.appType'
@/if
@if $.appApplyStatus!=null&&$.appApplyStatus!=''
  AND p3.apply_status = '$.appApplyStatus'
@/if
@if $.appDesignStatus!=null&&$.appDesignStatus!=''
  AND p3.design_status = '$.appDesignStatus'
@/if
@if $.orderBy!=null&&$.orderBy!=''
  ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
  LIMIT $.pageSize OFFSET $.startNum
@/if


-- @sql page_query_platform_role_r_permission
SELECT
    p1.id,
    p1.app_id as appId,
    p1.tenant_code as tenantCode,
    p1.role_id as roleId,
    p1.permission_id as permissionId,
    p1.update_at as updateAt,
    p1.updater,
    p1.updater_name as updaterName,
    p1.create_at as createAt,
    p1.creator,
    p1.creator_name as creatorName,
    p2.name as roleName,
    p2.code as roleCode,
    p2.type as roleType,
    p2.weight as roleWeight,
    p2.enable_status as roleEnableStatus,
    p2.seq_no as roleSeqNo,
    p2.description as roleDescription,
    p3.name as permissionName,
    p3.code as permissionCode,
    p3.type as permissionType,
    p3.object as permissionObject,
    p3.parent_object as permissionParentObject,
    p3.rule as permissionRule,
    p3.description as permissionDescription
FROM platform_role_r_permission p1
LEFT JOIN platform_role p2 ON p2.id = p1.role_id
LEFT JOIN platform_permission p3 ON p3.id = p1.permission_id
WHERE 1=1 AND p1.del_status = 0
@if $.roleId!=null&&$.roleId!=''
  AND p1.role_id = '$.roleId'
@/if
@if $.permissionId!=null&&$.permissionId!=''
  AND p1.permission_id = '$.permissionId'
@/if
@if $.appId!=null&&$.appId!=''
  AND p1.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.roleName!=null&&$.roleName!=''
  AND p2.name like '%$.roleName%'
@/if
@if $.roleCode!=null&&$.roleCode!=''
  AND p2.code like '%$.roleCode%'
@/if
@if $.roleType!=null&&$.roleType!=''
  AND p2.type = '$.roleType'
@/if
@if $.roleWeight!=null&&$.roleWeight!=''
  AND p2.weight = '$.roleWeight'
@/if
@if $.roleEnableStatus!=null&&$.roleEnableStatus!=''
  AND p2.enable_status = '$.roleEnableStatus'
@/if
@if $.permissionName!=null&&$.permissionName!=''
  AND p3.name like '%$.permissionName%'
@/if
@if $.permissionCode!=null&&$.permissionCode!=''
  AND p3.code like '%$.permissionCode%'
@/if
@if $.permissionObject!=null&&$.permissionObject!=''
  AND p3.object like '%$.permissionObject%'
@/if
@if $.permissionType!=null&&$.permissionType!=''
  AND p3.type = '$.permissionType'
@/if
@if $.orderBy!=null&&$.orderBy!=''
  ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
  LIMIT $.pageSize OFFSET $.startNum
@/if


-- @sql page_query_platform_role_r_tree_node
SELECT
    p1.id,
    p1.tree_id as appId,
    p1.pid,
    p1.extend_id as extendId,
    p1.flag,
    p1.icon,
    p1.description,
    p1.type,
    p1.tree_entity as treeEntity,
    p1.meta,
    p1.extend_entity as extendEntity,
    p1.text,
    p1.icon_type as iconType,
    p1.tenant_code as tenantCode,
    p1.seq_no as seqNo,
    p1.update_at as updateAt,
    p1.updater,
    p1.updater_name as updaterName,
    p1.create_at as createAt,
    p1.creator,
    p1.creator_name as creatorName,
    p1.url,
    IF(p2.total > 0,TRUE,FALSE) isRoled,
    IF(p3.total > 0,FALSE,TRUE) isLeaf
FROM platform_tree_node p1
LEFT JOIN (
SELECT tree_node_id,COUNT(*) total FROM platform_role_r_tree_node WHERE 1=1 AND del_status = 0
@if $.roleId!=null&&$.roleId!=''
  AND role_id = '$.roleId'
@/if
@if $.appId!=null&&$.appId!=''
  AND app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND tenant_code = '$.tenantCode'
@/if
GROUP BY tree_node_id) p2 ON p2.tree_node_id = p1.id
LEFT JOIN (
SELECT pid,COUNT(*) total FROM platform_tree_node WHERE 1=1 AND del_status = 0
@if $.appId!=null&&$.appId!=''
  AND tree_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND tenant_code = '$.tenantCode'
@/if
GROUP BY pid) p3 ON p3.pid = p1.id
WHERE 1=1 and del_status = 0
@if $.pid!=null&&$.pid!=''
  AND p1.pid = '$.pid'
@/if
@if $.appId!=null&&$.appId!=''
  AND p1.tree_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.orderBy!=null&&$.orderBy!=''
    ORDER BY $.orderBy
@/if


-- @sql page_query_platform_role_r_user
SELECT
    p1.id,
    p1.tenant_code as tenantCode,
    p1.role_id as roleId,
    p1.user_id as userId,
    p1.update_at as updateAt,
    p1.updater,
    p1.updater_name as updaterName,
    p1.create_at as createAt,
    p1.creator,
    p1.creator_name as creatorName,
    p2.app_id as appId,
    p2.name as roleName,
    p2.code as roleCode,
    p2.type as roleType,
    p2.weight as roleWeight,
    p2.enable_status as roleEnableStatus,
    p2.seq_no as roleSeqNo,
    p2.description as roleDescription,
    p3.name as userName,
    p3.en_name as userEnName,
    p3.login_name as userLoginName,
    p3.org_id as userOrgId,
    p3.orgName as userOrgName,
    p3.sex as userSex,
    p3.source as userSource,
    p3.type as userType,
    p3.post as userPost,
    p3.email as userEmail,
    p3.address as userAddress,
    p3.mobile_prefix as userMobilePrefix,
    p3.mobile_phone as userMobilePhone,
    p3.job_number as userJobNumber,
    p3.cooperating_org_id as userCooperatingOrgId,
    p3.union_id as userUnionId,
    p3.enable_status as userEnableStatus,
    p3.description as userDescription
FROM platform_role_r_user p1
LEFT JOIN platform_role p2 ON p2.id = p1.role_id
LEFT JOIN platform_user p3 ON p3.id = p1.user_id
WHERE 1=1 AND p1.del_status = 0 AND p2.del_status = 0 AND p3.del_status = 0
@if $.roleId!=null&&$.roleId!=''
  AND p1.role_id = '$.roleId'
@/if
@if $.userId!=null&&$.userId!=''
  AND p1.user_id = '$.userId'
@/if
@if $.appId!=null&&$.appId!=''
  AND p2.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.roleName!=null&&$.roleName!=''
  AND p2.name like '%$.roleName%'
@/if
@if $.roleCode!=null&&$.roleCode!=''
  AND p2.code like '%$.roleCode%'
@/if
@if $.roleType!=null&&$.roleType!=''
  AND p2.type = '$.roleType'
@/if
@if $.roleWeight!=null&&$.roleWeight!=''
  AND p2.weight = '$.roleWeight'
@/if
@if $.roleEnableStatus!=null&&$.roleEnableStatus!=''
  AND p2.enable_status = '$.roleEnableStatus'
@/if
@if $.userName!=null&&$.userName!=''
  AND p3.name like '%$.userName%'
@/if
@if $.userEnName!=null&&$.userEnName!=''
  AND p3.en_name like '%$.userEnName%'
@/if
@if $.userLoginName!=null&&$.userLoginName!=''
  AND p3.login_name like '%$.userLoginName%'
@/if
@if $.userOrgName!=null&&$.userOrgName!=''
  AND p3.orgName like '%$.userOrgName%'
@/if
@if $.userType!=null&&$.userType!=''
  AND p3.type = '$.userType'
@/if
@if $.userEnableStatus!=null&&$.userEnableStatus!=''
  AND p3.enable_status = '$.userEnableStatus'
@/if
@if $.orderBy!=null&&$.orderBy!=''
  ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
  LIMIT $.pageSize OFFSET $.startNum
@/if


-- @sql page_query_platform_org_r_user
SELECT
    p1.id,
    p1.tenant_code as tenantCode,
    p1.org_id as orgId,
    p1.user_id as userId,
    p1.default_org as defaultOrg,
    p1.post,
    p1.update_at as updateAt,
    p1.updater,
    p1.updater_name as updaterName,
    p1.create_at as createAt,
    p1.creator,
    p1.creator_name as creatorName,
    p2.pid as orgPid,
    p2.name as orgName,
    p2.code as orgCode,
    p2.type as orgType,
    p2.category as orgCategory,
    p2.status as orgStatus,
    p2.seq_no as orgSeqNo,
    p2.description as orgDescription,
    p3.name as userName,
    p3.en_name as userEnName,
    p3.login_name as userLoginName,
    p3.org_id as userOrgId,
    p3.orgName as userOrgName,
    p3.sex as userSex,
    p3.source as userSource,
    p3.type as userType,
    p3.post as userPost,
    p3.email as userEmail,
    p3.address as userAddress,
    p3.mobile_prefix as userMobilePrefix,
    p3.mobile_phone as userMobilePhone,
    p3.job_number as userJobNumber,
    p3.cooperating_org_id as userCooperatingOrgId,
    p3.union_id as userUnionId,
    p3.enable_status as userEnableStatus,
    p3.description as userDescription
FROM platform_org_r_user p1
LEFT JOIN platform_org p2 ON p2.id = p1.org_id
LEFT JOIN platform_user p3 ON p3.id = p1.user_id
WHERE 1=1 AND p1.del_status = 0
@if $.orgId!=null&&$.orgId!=''
  AND p1.org_id = '$.orgId'
@/if
@if $.userId!=null&&$.userId!=''
  AND p1.user_id = '$.userId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p1.tenant_code = '$.tenantCode'
@/if
@if $.orgName!=null&&$.orgName!=''
  AND p2.name like '%$.orgName%'
@/if
@if $.orgCode!=null&&$.orgCode!=''
  AND p2.code like '%$.orgCode%'
@/if
@if $.orgType!=null&&$.orgType!=''
  AND p2.type = '$.orgType'
@/if
@if $.orgCategory!=null&&$.orgCategory!=''
  AND p2.category = '$.orgCategory'
@/if
@if $.orgStatus!=null&&$.orgStatus!=''
  AND p2.status = '$.orgStatus'
@/if
@if $.userName!=null&&$.userName!=''
  AND p3.name like '%$.userName%'
@/if
@if $.userEnName!=null&&$.userEnName!=''
  AND p3.en_name like '%$.userEnName%'
@/if
@if $.userLoginName!=null&&$.userLoginName!=''
  AND p3.login_name like '%$.userLoginName%'
@/if
@if $.userOrgName!=null&&$.userOrgName!=''
  AND p3.orgName like '%$.userOrgName%'
@/if
@if $.userType!=null&&$.userType!=''
  AND p3.type = '$.userType'
@/if
@if $.userEnableStatus!=null&&$.userEnableStatus!=''
  AND p3.enable_status = '$.userEnableStatus'
@/if
@if $.orderBy!=null&&$.orderBy!=''
  ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
  LIMIT $.pageSize OFFSET $.startNum
@/if


-- @sql page_query_platform_export_template
SELECT
    id as id,
    app_id as appId,
    title as title,
    use_type as useType,
    file_type as fileType,
    file_code as fileCode,
    IF(enable_status,1,0) AS enableStatus,
    description as description,
    dept_id as deptId,
    bu_id as buId,
    tenant_code as tenantCode,
    del_status as delStatus,
    update_at as updateAt,
    updater as updater,
    updater_name as updaterName,
    create_at as createAt,
    creator as creator,
    creator_name as creatorName,
    delete_at as deleteAt
FROM platform_export_template
WHERE 1=1 AND del_status = 0
@if $.id!=null&&$.id!=''
  AND id = '$.id'
@/if
@if $.appId!=null&&$.appId!=''
  AND app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND tenant_code = '$.tenantCode'
@/if
@if $.title!=null&&$.title!=''
  AND title like '%$.title%'
@/if
@if $.useType!=null&&$.useType!=''
  AND use_type = '$.useType'
@/if
@if $.fileType!=null&&$.fileType!=''
  AND file_type = '$.fileType'
@/if
@if $.fileCode!=null&&$.fileCode!=''
  AND file_code like '%$.fileCode%'
@/if
@if $.enableStatus!=null&&$.enableStatus!=''
  AND enable_status = '$.enableStatus'
@/if
@if $.description!=null&&$.description!=''
  AND description like '%$.description%'
@/if
@if $.orderBy!=null&&$.orderBy!=''
  ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
  LIMIT $.pageSize OFFSET $.startNum
@/if

-- @sql page_query_platform_export_template_index
SELECT
    template,template2,template3,template4,template5,template6,template7,template8,template9
FROM platform_export_template
WHERE 1=1 AND del_status = 0
@if $.id!=null&&$.id!=''
  AND id = '$.id'
@/if
@if $.appId!=null&&$.appId!=''
  AND app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND tenant_code = '$.tenantCode'
@/if

-- @sql page_query_platform_app_r_table

-- @sql page_query_platform_role_app
SELECT
    distinct id,
    app_id as appId,
    name,
    code,
    type,
    weight,
    enable_status as enableStatus,
    description,
    seq_no as seqNo,
    used_app as usedApp,
    dept_id as deptId,
    bu_id as buId,
    tenant_code as tenantCode,
    del_status as delStatus,
    update_at as updateAt,
    updater,
    updater_name as updaterName,
    create_at as createAt,
    creator,
    creator_name as creatorName,
    delete_at as deleteAt
FROM (SELECT p1.* FROM platform_role p1
    WHERE 1=1 and p1.type = 'app'
    and p1.app_id = '$.appId'
    UNION ALL
    SELECT DISTINCT p1.* FROM platform_role p1
    LEFT JOIN platform_role_r_app p2 on p2.role_id = p1.id
    WHERE 1=1 and p2.del_status = 0
    and p1.type = 'platform' and p1.used_app = 1
    and p2.app_id = '$.appId') p
WHERE 1=1 and p.del_status = 0
@if $.name!=null&&$.name!=''
  AND p.name like '%$.name%'
@/if
@if $.code!=null&&$.code!=''
  AND p.code like '%$.code%'
@/if
@if $.type!=null&&$.type!=''
  AND p.type = '$.type'
@/if
@if $.weight!=null&&$.weight!=''
  AND p.weight = '$.weight'
@/if
@if $.enableStatus!=null&&$.enableStatus!=''
  AND p.enable_status = '$.enableStatus'
@/if
@if $.orderBy!=null&&$.orderBy!=''
  ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
  LIMIT $.pageSize OFFSET $.startNum
@/if
