package cn.geelato.web.platform.script.entty;


import cn.geelato.core.meta.annotation.Col;
import cn.geelato.core.meta.annotation.Entity;
import cn.geelato.core.meta.annotation.Title;
import cn.geelato.core.meta.model.entity.BaseEntity;
import lombok.Setter;

@Setter
@Entity(name = "platform_api", table = "platform_api")
@Title(title = "服务接口")
public class Api extends BaseEntity {

    private String release_content;
    @Col(name = "release_content", nullable = true)
    @Title(title = "服务脚本", description = "服务脚本")
    public String getRelease_content() {
        return release_content;
    }

}
