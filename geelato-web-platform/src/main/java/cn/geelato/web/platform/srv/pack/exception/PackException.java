package cn.geelato.web.platform.srv.pack.exception;

import cn.geelato.lang.exception.CoreException;

/**
 * 低代码应用打包与部署异常（60xxx 段）。
 * <p>错误码常量按失败场景定义在本类中，抛出时按场景选用：</p>
 * <ul>
 *   <li>60001-60002 —— 打包（应用不存在 / 打包前列一致性校验不通过）</li>
 *   <li>60003-60006 —— 部署（包数据无效 / 平台版本不匹配 / 元数据缺失 / 数据写入失败）</li>
 *   <li>60007-60010 —— 通用（表名非法 / 包文件 IO / 缓存刷新失败 / 环境限制与回滚）</li>
 * </ul>
 */
public class PackException extends CoreException {

    /** 打包-应用不存在（appId 对应的应用记录缺失）。 */
    public static final int ERROR_CODE_APP_NOT_FOUND = 60001;
    /** 打包前校验-物理表与实体定义列不一致（pre-pack gate，禁止打包）。 */
    public static final int ERROR_CODE_COLUMN_INCONSISTENT = 60002;
    /** 部署-应用版本/包数据缺失或损坏（版本无效、包内容为空、读取失败）。 */
    public static final int ERROR_CODE_PACKAGE_INVALID = 60003;
    /** 部署前校验-平台版本/元数据不匹配（需先升级平台应用）。 */
    public static final int ERROR_CODE_PLATFORM_MISMATCH = 60004;
    /** 部署中-元数据或字段在目标平台不存在（平台版本过低）。 */
    public static final int ERROR_CODE_META_NOT_FOUND = 60005;
    /** 部署中-包数据写入失败（SQL 执行失败等，事务回滚）。 */
    public static final int ERROR_CODE_DEPLOY_DATA_FAILED = 60006;
    /** 表名非法（防注入校验不通过）。 */
    public static final int ERROR_CODE_ILLEGAL_TABLE_NAME = 60007;
    /** 应用包文件读写失败（IO）。 */
    public static final int ERROR_CODE_PACKAGE_IO = 60008;
    /** 部署成功但应用元数据缓存刷新失败。 */
    public static final int ERROR_CODE_REFRESH_CACHE_FAILED = 60009;
    /** 环境限制不允许操作 / 回滚无备份版本。 */
    public static final int ERROR_CODE_NOT_ALLOWED = 60010;

    public PackException(int errorCode, String message) {
        super(errorCode, message);
    }

    public PackException(int errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
