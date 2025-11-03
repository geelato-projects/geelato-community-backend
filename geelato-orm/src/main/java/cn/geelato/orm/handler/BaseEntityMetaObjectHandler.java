package cn.geelato.orm.handler;

import cn.geelato.core.SessionCtx;
import cn.geelato.utils.DateUtils;
import cn.geelato.utils.UIDGenerator;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * BaseEntity字段自动填充处理器
 * 实现MyBatis-Plus的MetaObjectHandler接口，用于自动填充BaseEntity及其父类的字段
 * 填充逻辑参考JsonTextSaveParser中的实现
 */
@Component
public class BaseEntityMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        // 填充ID字段（使用雪花ID生成器）
        if (hasField(metaObject, "id")) {
            // 使用setFieldValByName强制覆盖原有值
            this.setFieldValByName("id", String.valueOf(UIDGenerator.generate()), metaObject);
        }

        // 获取当前时间
        Date currentTime = new Date();

        // 填充创建相关字段
        if (hasField(metaObject, "createAt")) {
            this.setFieldValByName("createAt", currentTime, metaObject);
        }
        if (hasField(metaObject, "creator")) {
            this.setFieldValByName("creator", SessionCtx.getUserId(), metaObject);
        }
        if (hasField(metaObject, "creatorName")) {
            this.setFieldValByName("creatorName", SessionCtx.getUserName(), metaObject);
        }

        // 填充租户和组织相关字段
        if (hasField(metaObject, "tenantCode")) {
            this.setFieldValByName("tenantCode", SessionCtx.getCurrentTenantCode(), metaObject);
        }
        if (hasField(metaObject, "buId") && SessionCtx.getCurrentUser() != null) {
            this.setFieldValByName("buId", SessionCtx.getCurrentUser().getBuId(), metaObject);
        }
        if (hasField(metaObject, "deptId") && SessionCtx.getCurrentUser() != null) {
            this.setFieldValByName("deptId", SessionCtx.getCurrentUser().getDefaultOrgId(), metaObject);
        }

        // 填充更新相关字段（插入时也需要填充，与JsonTextSaveParser保持一致）
        if (hasField(metaObject, "updateAt")) {
            this.setFieldValByName("updateAt", currentTime, metaObject);
        }
        if (hasField(metaObject, "updater")) {
            this.setFieldValByName("updater", SessionCtx.getUserId(), metaObject);
        }
        if (hasField(metaObject, "updaterName")) {
            this.setFieldValByName("updaterName", SessionCtx.getUserName(), metaObject);
        }

        // 填充删除状态字段
        if (hasField(metaObject, "delStatus")) {
            this.setFieldValByName("delStatus", 0, metaObject);
        }
        if (hasField(metaObject, "deleteAt")) {
            this.setFieldValByName("deleteAt", DateUtils.defaultDeleteAt(), metaObject);
        }
    }

    @Override
     public void updateFill(MetaObject metaObject) {
         // 获取当前时间
         Date currentTime = new Date();

         // 填充更新相关字段，使用setFieldValByName强制覆盖原有值
         if (hasField(metaObject, "updateAt")) {
             this.setFieldValByName("updateAt", currentTime, metaObject);
         }
         if (hasField(metaObject, "updater")) {
             this.setFieldValByName("updater", SessionCtx.getUserId(), metaObject);
         }
         if (hasField(metaObject, "updaterName")) {
             this.setFieldValByName("updaterName", SessionCtx.getUserName(), metaObject);
         }
     }

    /**
     * 检查MetaObject是否包含指定字段
     *
     * @param metaObject MetaObject对象
     * @param fieldName  字段名
     * @return 是否包含该字段
     */
    private boolean hasField(MetaObject metaObject, String fieldName) {
        return metaObject.hasSetter(fieldName);
    }
}
