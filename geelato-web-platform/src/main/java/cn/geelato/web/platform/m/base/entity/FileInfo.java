package cn.geelato.web.platform.m.base.entity;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 未引入：文件版本、文件关系
 * @author diabl
 */
@Getter
@Setter
@Entity(name = "platform_file")
@Title(title = "文件")
public class FileInfo extends BaseEntity {

    @Col(name = "name")
    @Title(title = "名称")
    private String name;

    @Col(name = "saved_name")
    @Title(title = "保存名称", description = "存储在磁盘的文件名称")
    private String savedName;

    @Col(name = "path")
    @Title(title = "相对路径", description = "一般相对于文件存储根目录。")
    private String relativePath;

    @Col(name = "size")
    @Title(title = "大小", description = "单位Byte")
    private int size;

    @Col(name = "type")
    @Title(title = "文件类型", description = "文件后缀")
    private String fileType;

    @Col(name = "description")
    @Title(title = "描述")
    private String description;


}
