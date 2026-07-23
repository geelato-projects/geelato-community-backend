package cn.geelato.ide.dto;

import cn.geelato.ide.entity.IdeScript;
import lombok.Data;

import java.util.List;

/**
 * IDE 脚本列表响应（含分页信息）。
 *
 * @author geelato
 */
@Data
public class IdeScriptListResponse {
    private List<IdeScript> list;
    private long total;
    private int page;
    private int size;
}
