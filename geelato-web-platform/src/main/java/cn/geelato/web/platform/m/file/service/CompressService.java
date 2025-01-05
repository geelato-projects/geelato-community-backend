package cn.geelato.web.platform.m.file.service;

import cn.geelato.core.SessionCtx;
import cn.geelato.web.platform.handler.file.FileHandler;
import cn.geelato.web.platform.m.base.service.BaseService;
import cn.geelato.web.platform.m.file.entity.Attachment;
import cn.geelato.web.platform.m.file.enums.AttachmentSourceEnum;
import cn.geelato.web.platform.m.file.param.CompressRequestBody;
import cn.geelato.web.platform.m.file.param.FileParam;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.ParseException;
import java.util.*;

/**
 * @author diabl
 */
@Component
public class CompressService extends BaseService {
    private static final String IlCargoInfoCollection = "SELECT DISTINCT pic_id FROM il_cargo_info_collection WHERE del_status = 0";

    @Lazy
    @Autowired
    private FileHandler fileHandler;

    /**
     * 压缩附件
     *
     * @param attachmentIds 附件ID字符串，多个附件ID用逗号分隔
     * @param fileName      压缩后的文件名
     * @param serviceType   服务类型
     * @param invalidTime   失效时间
     * @param amount        压缩包中的附件数量
     * @param appId         应用ID
     * @return 压缩后的附件列表
     * @throws IOException    如果在文件操作过程中发生I/O错误
     * @throws ParseException 如果解析日期时发生错误
     */
    public List<Attachment> compress(String attachmentIds, String fileName, String serviceType, Date invalidTime, Integer amount, String appId) throws IOException {
        FileParam fileParam = new FileParam(serviceType, AttachmentSourceEnum.PLATFORM_COMPRESS.getValue(), null, null, "ZIP", invalidTime, appId, SessionCtx.getCurrentTenantCode());
        List<Attachment> attachments = fileHandler.compress(attachmentIds, fileName, amount, fileParam);
        return attachments;
    }

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
            params.put("ctn_no", compBody.getOrderNo());
        }
        if (!params.isEmpty()) {
            // 组装查询Sql
            String sql = IlCargoInfoCollection;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                sql += " AND find_in_set(" + entry.getKey() + "," + entry.getValue() + ") ";
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
