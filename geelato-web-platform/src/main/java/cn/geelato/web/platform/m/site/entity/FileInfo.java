package cn.geelato.web.platform.m.site.entity;

import cn.geelato.core.constants.ColumnDefault;
import cn.geelato.lang.meta.Title;
import cn.geelato.web.platform.m.arco.entity.TreeNodeData;
import cn.geelato.web.platform.m.site.utils.FolderUtils;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Getter
@Setter
public class FileInfo {
    @Title(title = "文件路径")
    private String path;
    @Title(title = "文件名")
    private String name;
    @Title(title = "是否是文件夹")
    private boolean directory;
    @Title(title = "文件类型")
    private String fileType;
    @Title(title = "文件大小")
    private long fileSize;
    @Title(title = "最后修改时间")
    private String lastModified;
    @Title(title = "是否隐藏")
    private boolean hidden;
    @Title(title = "是否可读")
    private boolean canRead;
    @Title(title = "是否可写")
    private boolean canWrite;
    @Title(title = "是否可执行")
    private boolean canExecute;
    @Title(title = "文件列表")
    private LinkedHashSet<FileInfo> fileInfos;
    @Title(title = "状态")
    private int enableStatus = ColumnDefault.ENABLE_STATUS_VALUE;

    public static Set<TreeNodeData> buildTreeNodeDataList(Set<FileInfo> fileInfos) throws IOException {
        Set<TreeNodeData> treeNodeDataList = new LinkedHashSet<>();
        for (FileInfo fileInfo : fileInfos) {
            TreeNodeData treeNodeData = new TreeNodeData();
            Path path = Paths.get(fileInfo.getPath()).normalize().toAbsolutePath();
            treeNodeData.setKey(path.toString());
            treeNodeData.setTitle(path.getFileName().toString());
            treeNodeData.setIsLeaf(FolderUtils.hasNoSubFolders(path));
            treeNodeData.setLevel(2);
            treeNodeData.setData(fileInfo);
            treeNodeDataList.add(treeNodeData);
        }
        return treeNodeDataList;
    }

    public static void sortFileInfos(Set<FileInfo> fileInfos) {
        if (fileInfos != null && !fileInfos.isEmpty()) {
            // 转换为List以便排序
            List<FileInfo> list = new ArrayList<>(fileInfos);
            // 使用自定义比较器排序
            list.sort(new FileInfoComparator());
            // 清空原集合并添加排序后的元素
            fileInfos.clear();
            fileInfos.addAll(list);
        }
    }
}
