package cn.geelato.web.platform.m.base.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;

/**
 *  未引入：文件版本、文件关系
 */

@Entity(name = "platform_file")
@Title(title = "文件")
public class FileInfo extends BaseEntity {
    private String name;
    private String savedName;
    private String relativePath;
    private int size;
    private String fileType;
    private String description;

    @Col(name = "path")
    @Title(title = "相对路径",description = "一般相对于文件存储根目录。")
    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    @Col(name = "name")
    @Title(title = "名称")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Col(name = "saved_name")
    @Title(title = "保存名称",description = "存储在磁盘的文件名称")
    public String getSavedName() {
        return savedName;
    }

    public void setSavedName(String savedName) {
        this.savedName = savedName;
    }

    @Col(name = "size")
    @Title(title = "大小", description = "单位Byte")
    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    @Col(name = "type")
    @Title(title = "文件类型", description = "文件后缀")
    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    @Col(name = "description")
    @Title(title = "描述")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
