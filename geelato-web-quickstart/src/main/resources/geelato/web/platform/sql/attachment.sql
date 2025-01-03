-- @sql platform_attachment_by_more
SELECT
    p.id,
    p.object_id as objectId,
    p.form_ids as formIds,
    p.name,
    p.type,
    p.size,
    p.path,
    p.genre,
    p.invalid_time as invalidTime,
    p.dept_id as deptId,
    p.bu_id as buId,
    p.app_id as appId,
    p.tenant_code as tenantCode,
    p.del_status as delStatus,
    p.update_at as updateAt,
    p.updater,
    p.updater_name as updaterName,
    p.create_at as createAt,
    p.creator,
    p.creator_name as creatorName,
    p.delete_at as deleteAt,
    p.source
FROM (
    SELECT
        pa.*, 'attach' as source
    FROM platform_attach pa WHERE 1=1
    UNION ALL
    SELECT
        pc.*, 'compress' as source
    FROM platform_compress pc WHERE 1=1
    UNION ALL
    SELECT
        pr.*, 'resources' as source
    FROM platform_resources pr WHERE 1=1
) p where 1=1 and p.del_status = 0
@if $.ids!=null&&$.ids!=''
  AND find_in_set(p.id, '$.ids')
@/if
@if $.id!=null&&$.id!=''
  AND find_in_set(p.id, '$.id')
@/if
@if $.objectId!=null&&$.objectId!=''
  AND p.object_id = '$.objectId'
@/if
@if $.formIds!=null&&$.formIds!=''
  AND find_in_set(p.form_ids, '$.formIds')
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
@if $.invalidTime!=null&&$.invalidTime!=''
  AND p.invalid_time = '$.invalidTime'
@/if
@if $.gtInvalidTime!=null&&$.gtInvalidTime!=''
  AND p.invalid_time >= '$.gtInvalidTime'
@/if
@if $.ltInvalidTime!=null&&$.ltInvalidTime!=''
  AND p.invalid_time <= '$.ltInvalidTime'
@/if
@if $.source!=null&&$.source!=''
  AND find_in_set(p.source, '$.source')
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
