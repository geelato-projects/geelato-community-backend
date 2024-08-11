-- @sql platform_attachment_by_more
SELECT
    p.id,
    p.name,
    p.type,
    p.size,
    p.path,
    p.url,
    p.genre,
    p.source,
    p.object_id as objectId,
    p.dept_id as deptId,
    p.bu_id as buId,
    p.app_id as appId,
    p.tenant_code as tenantCode,
    p.update_at as updateAt,
    p.updater,
    p.updater_name as updaterName,
    p.create_at as createAt,
    p.creator,
    p.creator_name as creatorName,
    p.del_status as delStatus,
    p.delete_at as deleteAt
FROM (
    SELECT
        id,
        name,
        type,
        size,
        path,
        url,
        genre,
        'attach' as source,
        object_id,
        dept_id,
        bu_id,
        app_id,
        tenant_code,
        update_at,
        updater,
        updater_name,
        create_at,
        creator,
        creator_name,
        del_status,
        delete_at
    FROM platform_attach WHERE 1=1
    UNION ALL
    SELECT
        id,
        name,
        type,
        size,
        path,
        url,
        genre,
        'resources' as source,
        object_id,
        dept_id,
        bu_id,
        app_id,
        tenant_code,
        update_at,
        updater,
        updater_name,
        create_at,
        creator,
        creator_name,
        del_status,
        delete_at
    FROM platform_resources WHERE 1=1
) p where 1=1 and p.del_status = 0
@if $.ids!=null&&$.ids!=''
  AND find_in_set(p.id, '$.ids')
@/if
@if $.id!=null&&$.id!=''
  AND find_in_set(p.id, '$.id')
@/if
@if $.name!=null&&$.name!=''
  AND p.name like '%$.appId%'
@/if
@if $.type!=null&&$.type!=''
  AND p.type = '$.type'
@/if
@if $.path!=null&&$.path!=''
  AND p.path like '%$.path%'
@/if
@if $.genre!=null&&$.genre!=''
  AND find_in_set(p.genre, '$.genre')
@/if
@if $.source!=null&&$.source!=''
  AND find_in_set(p.source, '$.source')
@/if
@if $.objectId!=null&&$.objectId!=''
  AND p.object_id = '$.objectId'
@/if
@if $.appId!=null&&$.appId!=''
  AND p.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p.tenant_code = '$.tenantCode'
@/if    
@if $.orderBy!=null&&$.orderBy!=''
    ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
    LIMIT $.pageSize OFFSET $.startNum
@/if