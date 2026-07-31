package cn.geelato.web.common.security.delegate;

/**
 * 委托代办会话。
 * <p>
 * 记录"某个登录态（以其原始 Authorization 头为 key）当前正以另一个用户身份操作"的关系，
 * 用于支持老带新场景：导师（origin）代新员工（target）查看/操作数据。
 * <ul>
 *   <li>{@code origin*} —— 实际操作人（被委托人/代理人，即导师）。切换后该信息注入到
 *       {@code User.delegateUserId/delegateUserName} 供审计使用。</li>
 *   <li>{@code target*} —— 当前生效身份（委托人，即新员工）。切换后 SecurityContext 的
 *       currentUser 即按此身份加载（菜单、数据权限、行级审计均按 target 计算）。</li>
 *   <li>{@code tokenKey} —— 关联的凭证 key，约定使用完整的原始 Authorization 头字符串
 *       （与 DefaultSecurityInterceptor.tokenContextCache 的 key 一致），不重签 token。</li>
 * </ul>
 *
 * @author geelato
 */
public class DelegateSession {

    private String originUserId;
    private String originLoginName;
    private String originUserName;
    private String targetUserId;
    private String targetLoginName;
    private String targetUserName;
    private String tenantCode;
    private long createAt;
    private long expireAt;

    public DelegateSession() {
    }

    public DelegateSession(String originUserId, String originLoginName, String originUserName,
                           String targetUserId, String targetLoginName, String targetUserName,
                           String tenantCode) {
        this.originUserId = originUserId;
        this.originLoginName = originLoginName;
        this.originUserName = originUserName;
        this.targetUserId = targetUserId;
        this.targetLoginName = targetLoginName;
        this.targetUserName = targetUserName;
        this.tenantCode = tenantCode;
        this.createAt = System.currentTimeMillis();
    }

    public boolean isExpired(long now) {
        return expireAt > 0 && now > expireAt;
    }

    public String getOriginUserId() {
        return originUserId;
    }

    public void setOriginUserId(String originUserId) {
        this.originUserId = originUserId;
    }

    public String getOriginLoginName() {
        return originLoginName;
    }

    public void setOriginLoginName(String originLoginName) {
        this.originLoginName = originLoginName;
    }

    public String getOriginUserName() {
        return originUserName;
    }

    public void setOriginUserName(String originUserName) {
        this.originUserName = originUserName;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getTargetLoginName() {
        return targetLoginName;
    }

    public void setTargetLoginName(String targetLoginName) {
        this.targetLoginName = targetLoginName;
    }

    public String getTargetUserName() {
        return targetUserName;
    }

    public void setTargetUserName(String targetUserName) {
        this.targetUserName = targetUserName;
    }

    public String getTenantCode() {
        return tenantCode;
    }

    public void setTenantCode(String tenantCode) {
        this.tenantCode = tenantCode;
    }

    public long getCreateAt() {
        return createAt;
    }

    public void setCreateAt(long createAt) {
        this.createAt = createAt;
    }

    public long getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(long expireAt) {
        this.expireAt = expireAt;
    }
}
