package cn.geelato.web.platform.m.model.service;

import cn.geelato.core.constants.MetaDaoSql;
import cn.geelato.core.enums.DeleteStatusEnum;
import cn.geelato.core.gql.filter.FilterGroup;
import cn.geelato.core.meta.model.column.ColumnMeta;
import cn.geelato.core.meta.model.entity.TableCheck;
import cn.geelato.core.meta.model.entity.TableMeta;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.platform.m.base.service.BaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DevTableCheckService extends BaseService {
    @Lazy
    @Autowired
    private DevTableService devTableService;
    @Lazy
    @Autowired
    private DevTableColumnService devTableColumnService;

    /**
     * 在表单设置后执行的操作
     * 设置模型信息，connectId,tableName,appId,tenantCode
     * 设置字段信息，columnName
     *
     * @param form 表单对象
     * @throws RuntimeException 当表ID为空或表不存在时抛出异常
     */
    public void afterSet(TableCheck form) {
        // 判断表id是否为空
        if (StringUtils.isBlank(form.getTableId())) {
            throw new RuntimeException("table id can not be null");
        }
        TableMeta tableMeta = devTableService.getModel(TableMeta.class, form.getTableId());
        if (tableMeta == null) {
            throw new RuntimeException("table not exist");
        }
        form.setConnectId(tableMeta.getConnectId());
        form.setTableSchema(tableMeta.getTableSchema());
        form.setTableName(tableMeta.getEntityName());
        form.setAppId(tableMeta.getAppId());
        form.setTenantCode(tableMeta.getTenantCode());
        // 如果有指定列，则获取列名称
        if (StringUtils.isNotBlank(form.getColumnId())) {
            FilterGroup filter = new FilterGroup();
            filter.addFilter("id", FilterGroup.Operator.in, form.getColumnId());
            List<ColumnMeta> columnMetas = devTableColumnService.queryModel(ColumnMeta.class, filter);
            if (columnMetas != null && columnMetas.size() > 0) {
                List<String> names = columnMetas.stream().map(ColumnMeta::getName).collect(Collectors.toList());
                form.setColumnName(StringUtils.join(names, ","));
            } else {
                form.setColumnId(null);
                form.setColumnName(null);
            }
        } else {
            form.setColumnId(null);
            form.setColumnName(null);
        }
    }

    public boolean validate(TableCheck form) {
        Map<String, String> params = new HashMap<>();
        params.put("code", form.getCode());
        params.put("table_schema", form.getTableSchema());
        params.put("type", form.getType());
        params.put("del_status", String.valueOf(DeleteStatusEnum.NO.getCode()));
        params.put("tenant_code", form.getTenantCode());
        boolean isEmpty = validate("platform_dev_table_check", form.getId(), params);
        if (!isEmpty) {
            return false;
        }
        switchDbByConnectId(form.getConnectId());
        List<Map<String, Object>> mapList = dynamicDao.getJdbcTemplate().queryForList(
                String.format(MetaDaoSql.SQL_QUERY_TABLE_CONSTRAINTS_BY_NAME + " AND TABLE_NAME != '%s';", form.getTableSchema(), form.getType().toUpperCase(Locale.ENGLISH), form.getCode(), form.getTableName()));
        return mapList.isEmpty();
    }
}
