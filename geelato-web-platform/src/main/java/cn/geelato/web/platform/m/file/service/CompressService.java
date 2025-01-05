package cn.geelato.web.platform.m.file.service;

import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.file.param.CompressRequestBody;
import org.apache.logging.log4j.util.Strings;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author diabl
 */
@Component
public class CompressService extends BaseService {
    private static final String IlCargoInfoCollection = "SELECT DISTINCT pic_id FROM il_cargo_info_collection WHERE del_status = 0";

    /**
     * 查询货物信息集合中的附件ID列表
     *
     * @param compBody 请求体，包含查询参数
     * @return 包含所有查询到的附件ID的列表
     */
    public List<String> queryIlCargoInfoCollection(CompressRequestBody compBody) {
        List<String> attachmentIds = new ArrayList<>();
        // 组装查询参数
        Map<String, Object> params = new HashMap<>();
        if (Strings.isNotBlank(compBody.getCtnNo())) {
            params.put("ctn_no", compBody.getCtnNo());
        }
        if (Strings.isNotBlank(compBody.getOrderNo())) {
            params.put("order_no", compBody.getOrderNo());
        }
        if (!params.isEmpty()) {
            // 组装查询Sql
            String sql = IlCargoInfoCollection;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                sql += " AND find_in_set(" + entry.getKey() + ", '" + entry.getValue() + "') ";
            }
            // 查询
            List<Map<String, Object>> mapList = dao.getJdbcTemplate().queryForList(sql);
            // 结果处理
            if (mapList != null && !mapList.isEmpty()) {
                for (Map<String, Object> map : mapList) {
                    String picId = map.get("pic_id") == null ? "" : map.get("pic_id").toString();
                    if (Strings.isNotBlank(picId) && !attachmentIds.contains(picId)) {
                        attachmentIds.add(picId);
                    }
                }
            }
        }
        return attachmentIds;
    }
}
