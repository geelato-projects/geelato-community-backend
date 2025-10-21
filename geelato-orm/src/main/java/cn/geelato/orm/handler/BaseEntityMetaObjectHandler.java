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
            this.strictInsertFill(metaObject, "id", String.class, String.valueOf(UIDGenerator.generate()));
        }

        // 获取当前时间
        Date currentTime = new Date();

        // 填充创建相关字段
        if (hasField(metaObject, "createAt")) {
            this.strictInsertFill(metaObject, "createAt", Date.class, currentTime);
        }
        if (hasField(metaObject, "creator")) {
            this.strictInsertFill(metaObject, "creator", String.class, SessionCtx.getUserId());
        }
        if (hasField(metaObject, "creatorName")) {
            this.strictInsertFill(metaObject, "creatorName", String.class, SessionCtx.getUserName());
        }

        // 填充租户和组织相关字段
        if (hasField(metaObject, "tenantCode")) {
            this.strictInsertFill(metaObject, "tenantCode", String.class, SessionCtx.getCurrentTenantCode());
        }
        if (hasField(metaObject, "buId") && SessionCtx.getCurrentUser() != null) {
            this.strictInsertFill(metaObject, "buId", String.class, SessionCtx.getCurrentUser().getBuId());
        }
        if (hasField(metaObject, "deptId") && SessionCtx.getCurrentUser() != null) {
            this.strictInsertFill(metaObject, "deptId", String.class, SessionCtx.getCurrentUser().getDefaultOrgId());
        }

        // 填充更新相关字段（插入时也需要填充，与JsonTextSaveParser保持一致）
        if (hasField(metaObject, "updateAt")) {
            this.strictInsertFill(metaObject, "updateAt", Date.class, currentTime);
        }
        if (hasField(metaObject, "updater")) {
            this.strictInsertFill(metaObject, "updater", String.class, SessionCtx.getUserId());
        }
        if (hasField(metaObject, "updaterName")) {
            this.strictInsertFill(metaObject, "updaterName", String.class, SessionCtx.getUserName());
        }

        // 填充删除状态字段
        if (hasField(metaObject, "delStatus")) {
            this.strictInsertFill(metaObject, "delStatus", Integer.class, 0);
        }
        if (hasField(metaObject, "deleteAt")) {
            this.strictInsertFill(metaObject, "deleteAt", Date.class, DateUtils.defaultDeleteAt());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        // 获取当前时间
        Date currentTime = new Date();

        // 填充更新相关字段（与JsonTextSaveParser中的putUpdateDefaultField方法保持一致）
        if (hasField(metaObject, "updateAt")) {
            this.strictUpdateFill(metaObject, "updateAt", Date.class, currentTime);
        }
        if (hasField(metaObject, "updater")) {
            this.strictUpdateFill(metaObject, "updater", String.class, SessionCtx.getUserId());
        }
        if (hasField(metaObject, "updaterName")) {
            this.strictUpdateFill(metaObject, "updaterName", String.class, SessionCtx.getUserName());
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
