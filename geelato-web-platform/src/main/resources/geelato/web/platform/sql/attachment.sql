-- @sql platform_attachment_by_more
SELECT
    p.id,
    p.pid,
    p.object_id AS objectId,
    p.form_ids AS formIds,
    p.name,
    p.type,
    p.size,
    p.path,
    p.genre,
    p.invalid_time AS invalidTime,
    p.batch_no AS batchNo,
    p.resolution,
    p.dept_id AS deptId,
    p.bu_id AS buId,
    p.app_id AS appId,
    p.tenant_code AS tenantCode,
    p.del_status AS delStatus,
    p.update_at AS updateAt,
    p.updater,
    p.updater_name AS updaterName,
    p.create_at AS createAt,
    p.creator,
    p.creator_name AS creatorName,
    p.delete_at AS deleteAt,
    p.source,
    p.storageType
FROM (
    SELECT pa.*, 'attach' AS source, IF(pa.object_id IS not null AND pa.object_id != '','aliyun','local') AS storageType FROM platform_attach pa WHERE 1=1
    UNION ALL
    SELECT pc.*, 'compress' AS source, IF(pc.object_id IS not null AND pc.object_id != '','aliyun','local') AS storageType FROM platform_compress pc WHERE 1=1
    UNION ALL
    SELECT pr.*, 'resources' AS source, IF(pr.object_id IS not null AND pr.object_id != '','aliyun','local') AS storageType FROM platform_resources pr WHERE 1=1
) p where 1=1 and p.del_status = 0
@if $.ids!=null&&$.ids!=''
  AND find_in_set(p.id, '$.ids')
@/if
@if $.id!=null&&$.id!=''
  AND find_in_set(p.id, '$.id')
@/if
@if $.pid!=null&&$.pid!=''
  AND find_in_set(p.pid, '$.pid')
@/if
@if $.pids!=null&&$.pids!=''
  AND (find_in_set(p.pid, '$.pids') OR find_in_set(p.id, '$.pids'))
@/if
@if $.objectId!=null&&$.objectId!=''
  AND p.object_id = '$.objectId'
@/if
@if $.formIds!=null&&$.formIds!=''
  AND find_in_set(p.form_ids, '$.formIds')
@/if
@if $.name!=null&&$.name!=''
  AND p.name like '%$.name%'
@/if
@if $.type!=null&&$.type!=''
  AND p.type = '$.type'
@/if
@if $.path!=null&&$.path!=''
  AND p.path like '%$.path%'
@/if
@if $.pathUrl!=null&&$.pathUrl!=''
  AND p.path = '%$.pathUrl%'
@/if
@if $.genre!=null&&$.genre!=''
    AND p.genre like '%$.genre%'
@/if
@if $.genref!=null&&$.genref!=''
  AND find_in_set('$.genref', p.genre)
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
@if $.batchNo!=null&&$.batchNo!=''
  AND find_in_set(p.batch_no, '$.batchNo')
@/if
@if $.resolution!=null&&$.resolution!=''
  AND find_in_set(p.resolution, '$.resolution')
@/if
@if $.source!=null&&$.source!=''
  AND find_in_set(p.source, '$.source')
@/if
@if $.storageType!=null&&$.storageType!=''
  AND find_in_set(p.storageType, '$.storageType')
@/if
@if $.appId!=null&&$.appId!=''
  AND p.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p.tenant_code = '$.tenantCode'
@/if
@if $.gtCreateAt!=null&&$.gtCreateAt!=''
  AND p.create_at >= '$.gtCreateAt'
@/if
@if $.ltCreateAt!=null&&$.ltCreateAt!=''
  AND p.create_at <= '$.ltCreateAt'
@/if
@if $.orderBy!=null&&$.orderBy!=''
    ORDER BY $.orderBy
@/if
@if $.pageSize!=null&&$.pageSize!=''
    LIMIT $.pageSize OFFSET $.startNum
@/if


-- @sql platform_attachment_by_count
SELECT COUNT(*) AS total FROM (
    SELECT pa.*, 'attach' AS source, IF(pa.object_id IS not null AND pa.object_id != '','aliyun','local') AS storageType FROM platform_attach pa WHERE 1=1
    UNION ALL
    SELECT pc.*, 'compress' AS source, IF(pc.object_id IS not null AND pc.object_id != '','aliyun','local') AS storageType FROM platform_compress pc WHERE 1=1
    UNION ALL
    SELECT pr.*, 'resources' AS source, IF(pr.object_id IS not null AND pr.object_id != '','aliyun','local') AS storageType FROM platform_resources pr WHERE 1=1
) p where 1=1 and p.del_status = 0
@if $.ids!=null&&$.ids!=''
  AND find_in_set(p.id, '$.ids')
@/if
@if $.id!=null&&$.id!=''
  AND find_in_set(p.id, '$.id')
@/if
@if $.pid!=null&&$.pid!=''
  AND find_in_set(p.pid, '$.pid')
@/if
@if $.pids!=null&&$.pids!=''
  AND (find_in_set(p.pid, '$.pids') OR find_in_set(p.id, '$.pids'))
@/if
@if $.objectId!=null&&$.objectId!=''
  AND p.object_id = '$.objectId'
@/if
@if $.formIds!=null&&$.formIds!=''
  AND find_in_set(p.form_ids, '$.formIds')
@/if
@if $.name!=null&&$.name!=''
  AND p.name like '%$.name%'
@/if
@if $.type!=null&&$.type!=''
  AND p.type = '$.type'
@/if
@if $.path!=null&&$.path!=''
  AND p.path like '%$.path%'
@/if
@if $.genre!=null&&$.genre!=''
  AND p.genre like '%$.genre%'
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
@if $.batchNo!=null&&$.batchNo!=''
  AND find_in_set(p.batch_no, '$.batchNo')
@/if
@if $.resolution!=null&&$.resolution!=''
  AND find_in_set(p.resolution, '$.resolution')
@/if
@if $.source!=null&&$.source!=''
  AND find_in_set(p.source, '$.source')
@/if
@if $.storageType!=null&&$.storageType!=''
  AND find_in_set(p.storageType, '$.storageType')
@/if
@if $.appId!=null&&$.appId!=''
  AND p.app_id = '$.appId'
@/if
@if $.tenantCode!=null&&$.tenantCode!=''
  AND p.tenant_code = '$.tenantCode'
@/if
@if $.gtCreateAt!=null&&$.gtCreateAt!=''
  AND p.create_at >= '$.gtCreateAt'
@/if
@if $.ltCreateAt!=null&&$.ltCreateAt!=''
  AND p.create_at <= '$.ltCreateAt'
@/if
@if $.orderBy!=null&&$.orderBy!=''
    ORDER BY $.orderBy
@/if

-- @sql platform_attachment_query_type
SELECT DISTINCT p.type FROM (
    SELECT pa.*, 'attach' AS source FROM platform_attach pa WHERE 1=1
    UNION ALL
    SELECT pc.*, 'compress' AS source FROM platform_compress pc WHERE 1=1
    UNION ALL
    SELECT pr.*, 'resources' AS source FROM platform_resources pr WHERE 1=1
) p WHERE 1=1
  AND p.del_status = 0
  AND p.type IS NOT NULL
  AND p.type != ''
ORDER BY p.type

-- @sql platform_attachment_query_resolution
SELECT DISTINCT resolution, product FROM (
     SELECT resolution, del_status, CAST(SUBSTRING_INDEX(resolution, 'x', 1) AS UNSIGNED) * CAST(SUBSTRING_INDEX(resolution, 'x', -1) AS UNSIGNED) AS product
     FROM platform_attach WHERE 1=1
     UNION ALL
     SELECT resolution, del_status, CAST(SUBSTRING_INDEX(resolution, 'x', 1) AS UNSIGNED) * CAST(SUBSTRING_INDEX(resolution, 'x', -1) AS UNSIGNED) AS product
     FROM platform_compress WHERE 1=1
     UNION ALL
     SELECT resolution, del_status, CAST(SUBSTRING_INDEX(resolution, 'x', 1) AS UNSIGNED) * CAST(SUBSTRING_INDEX(resolution, 'x', -1) AS UNSIGNED) AS product
     FROM platform_resources WHERE 1=1
) p WHERE 1=1
  AND p.del_status = 0
  AND p.resolution IS NOT NULL
  AND p.resolution != ''
ORDER BY product;
