package cn.geelato.web.platform.message.mapper;

import cn.geelato.web.platform.message.entity.PlatformMsg;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 平台消息Mapper接口
 */
@Mapper
public interface PlatformMsgMapper extends BaseMapper<PlatformMsg> {
}