package cn.geelato.web.platform.m.site.entity;

import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import cn.geelato.core.meta.model.entity.EntityEnableAble;
import cn.geelato.web.platform.m.arco.entity.TreeNodeData;
import cn.geelato.web.platform.m.base.service.UploadService;
import cn.geelato.web.platform.m.site.utils.FolderUtils;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@Title(title = "静态站点")
@Entity(name = "platform_static_site")
public class StaticSite extends BaseEntity implements EntityEnableAble {
    @Title(title = "应用ID")
    @Col(name = "app_id")
    private String appId;
    @Title(title = "名称")
    private String name;
    @Title(title = "编码")
    private String code;
    @Title(title = "是否需要权限控制")
    @Col(name = "require_permission")
    private boolean requirePermission = false;
    @Title(title = "描述")
    private String description;
    @Title(title = "状态")
    @Col(name = "enable_status")
    private int enableStatus;


    public static Set<TreeNodeData> buildTreeNodeDataList(List<StaticSite> staticSites) throws IOException {
        Set<TreeNodeData> treeNodeDataList = new LinkedHashSet<>();
        for (StaticSite staticSite : staticSites) {
            TreeNodeData treeNodeData = new TreeNodeData();
            File file = new File(UploadService.ROOT_SITE_DIRECTORY, staticSite.getId());
            if (file == null || !file.exists()) {
                continue;
            }
            Path path = Paths.get(file.getAbsolutePath()).normalize().toAbsolutePath();
            treeNodeData.setKey(path.toString());
            treeNodeData.setTitle(String.format("%s(%s)", staticSite.getName(), staticSite.getCode()));
            treeNodeData.setIsLeaf(!FolderUtils.hasSubFolders(path));
            treeNodeData.setLevel(1);
            treeNodeData.setData(staticSite);
            treeNodeDataList.add(treeNodeData);
        }
        return treeNodeDataList;
    }
}
