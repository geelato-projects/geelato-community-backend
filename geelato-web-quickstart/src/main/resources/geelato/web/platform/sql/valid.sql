-- @sql platform_validate
select * from $.tableName where 1=1
@if $.id!=null&&$.id!=''
    AND id != '$.id'
@/if
@for i in $.condition
    @if $.condition[i].value!=null&&$.condition[i].value!=''
      AND $.condition[i].key = '$.condition[i].value'
    @/if
@/for