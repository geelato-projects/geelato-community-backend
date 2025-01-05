package cn.geelato.web.platform.m.file.utils;

import cn.geelato.web.platform.m.file.handler.CompressHandler;
import cn.geelato.web.platform.m.file.param.FileParam;

import java.util.Date;

public class FileParamUtils {

    /**
     * 根据本地文件操作生成文件参数对象
     *
     * @param sourceType 数据来源类型
     * @param genre      文件类型
     * @param appId      应用ID
     * @param tenantCode 租户代码
     * @return 生成的文件参数对象
     */
    public static FileParam byLocal(String sourceType, String genre, String appId, String tenantCode) {
        return new FileParam(null, sourceType, null, null, genre, null, null, appId, tenantCode, null, null, null);
    }

    /**
     * 根据压缩操作生成文件参数对象
     *
     * @param serviceType 服务类型
     * @param genre       文件类型
     * @param invalidTime 失效时间
     * @param batchNo     批次号
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @return 生成的文件参数对象
     */
    public static FileParam byBuildCompress(String serviceType, String genre, Date invalidTime, String batchNo, String appId, String tenantCode) {
        return new FileParam(serviceType, CompressHandler.ATTACHMENT_SOURCE, null, null, genre, invalidTime, batchNo, appId, tenantCode, null, null, null);
    }


    /**
     * 根据保存压缩操作生成文件参数对象
     *
     * @param serviceType 服务类型
     * @param sourceType  数据来源类型
     * @param formIds     表单ID列表
     * @param genre       文件类型
     * @param invalidTime 失效时间
     * @param batchNo     批次号
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @return 生成的文件参数对象
     */
    public static FileParam bySaveCompress(String serviceType, String sourceType, String formIds, String genre, Date invalidTime, String batchNo, String appId, String tenantCode) {
        return new FileParam(serviceType, sourceType, null, formIds, genre, invalidTime, batchNo, appId, tenantCode, null, null, null);
    }


    /**
     * 根据缩略图操作生成文件参数对象
     *
     * @param ServiceType 服务类型
     * @param sourceType  数据来源类型
     * @param genre       文件类型
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @return 生成的文件参数对象
     */
    public static FileParam byThumbnail(String ServiceType, String sourceType, String genre, String appId, String tenantCode) {
        return new FileParam(ServiceType, sourceType, null, null, genre, null, null, appId, tenantCode, null, null, null);
    }

    /**
     * 根据Base64编码和缩略图操作生成文件参数对象
     *
     * @param serviceType 服务类型
     * @param sourceType  数据来源类型
     * @param genre       文件类型
     * @param tenantCode  租户代码
     * @param thumbnail   是否生成缩略图
     * @param dimension   缩略图尺寸
     * @param thumbScale  缩略图缩放比例
     * @return 生成的文件参数对象
     */
    public static FileParam byBase64AndThumbnail(String serviceType, String sourceType, String genre, String tenantCode, Boolean thumbnail, Integer dimension, Double thumbScale) {
        return new FileParam(serviceType, sourceType, null, null, genre, null, null, null, tenantCode, thumbnail, dimension, thumbScale);
    }

    /**
     * 创建一个FileParam对象。
     *
     * @param serviceType 服务类型
     * @param sourceType  数据来源类型
     * @param objectId    对象ID
     * @param formIds     表单ID列表
     * @param genre       文件类型
     * @param invalidTime 失效时间
     * @param batchNo     批次号
     * @param appId       应用ID
     * @param tenantCode  租户代码
     * @param thumbnail   是否生成缩略图
     * @param dimension   缩略图尺寸
     * @param thumbScale  缩略图缩放比例
     * @return 创建的FileParam对象
     */
    public static FileParam by(String serviceType, String sourceType, String objectId, String formIds, String genre, Date invalidTime, String batchNo, String appId, String tenantCode, Boolean thumbnail, Integer dimension, Double thumbScale) {
        return new FileParam(serviceType, sourceType, objectId, formIds, genre, invalidTime, batchNo, appId, tenantCode, thumbnail, dimension, thumbScale);
    }
}
