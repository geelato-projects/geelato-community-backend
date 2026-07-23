package cn.geelato.ide.dto;

import lombok.Data;

/**
 * 脚本分组（脚本树用）。
 *
 * @author geelato
 */
@Data
public class IdeScriptGroup {
    private String groupName;
    private long count;
}
