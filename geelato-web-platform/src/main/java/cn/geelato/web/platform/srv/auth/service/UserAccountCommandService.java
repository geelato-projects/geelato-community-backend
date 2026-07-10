package cn.geelato.web.platform.srv.auth.service;

import cn.geelato.core.orm.Dao;
import cn.geelato.lang.constants.ApiErrorMsg;
import cn.geelato.meta.User;
import cn.geelato.security.SecurityContext;
import cn.geelato.utils.Base64Utils;
import cn.geelato.utils.StringUtils;
import cn.geelato.web.common.interceptor.UnauthorizedException;
import cn.geelato.web.platform.srv.auth.AccountOperationForbiddenException;
import cn.geelato.web.platform.srv.auth.AuthBadRequestException;
import cn.geelato.web.platform.srv.security.entity.UpdateUserProfileRequest;
import cn.geelato.web.platform.utils.EncryptUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Component
public class UserAccountCommandService {
    private final Dao dao;

    public UserAccountCommandService(@Qualifier("primaryDao") Dao dao) {
        this.dao = dao;
    }

    public void uploadAvatar(String currentUserId, String targetUserId, MultipartFile file) {
        ensureCanOperateUser(currentUserId, targetUserId);
        if (file == null || file.isEmpty()) {
            throw new AuthBadRequestException("头像文件不能为空");
        }
        String contentType = file.getContentType();
        if (StringUtils.isBlank(contentType) || !contentType.startsWith("image/")) {
            throw new AuthBadRequestException("头像文件必须为图片格式");
        }
        User user = requireUser(targetUserId);
        try {
            user.setAvatar(Base64Utils.fromFile(file.getBytes(), contentType));
        } catch (IOException e) {
            throw new AuthBadRequestException("头像文件读取失败", e);
        }
        dao.save(user);
    }

    public void updateUserProfile(String currentUserId, String targetUserId, UpdateUserProfileRequest request) {
        ensureCanOperateUser(currentUserId, targetUserId);
        User user = requireUser(targetUserId);
        user.setName(trimToNull(request.getName()));
        user.setEnName(trimToNull(request.getEnName()));
        user.setMobilePrefix(trimToNull(request.getMobilePrefix()));
        user.setMobilePhone(trimToNull(request.getMobilePhone()));
        user.setTelephone(trimToNull(request.getTelephone()));
        user.setEmail(trimToNull(request.getEmail()));
        user.setNationCode(trimToNull(request.getNationCode()));
        user.setProvinceCode(trimToNull(request.getProvinceCode()));
        user.setCityCode(trimToNull(request.getCityCode()));
        user.setAddress(trimToNull(request.getAddress()));
        user.setDescription(trimToNull(request.getDescription()));
        dao.save(user);
    }

    public String resetCurrentUserPassword(String currentUserId, int passwordLength) {
        if (StringUtils.isBlank(currentUserId)) {
            throw new UnauthorizedException("用户未登录");
        }
        User user = requireUser(currentUserId);
        String plainPassword = RandomStringUtils.randomAlphanumeric(passwordLength > 32 ? 32 : Math.max(passwordLength, 8));
        user.setPlainPassword(plainPassword);
        EncryptUtil.encryptPassword(user);
        dao.save(user);
        return plainPassword;
    }

    private User requireUser(String userId) {
        if (StringUtils.isBlank(userId)) {
            throw new AuthBadRequestException("用户ID不能为空");
        }
        User user = dao.queryForObject(User.class, userId);
        if (user == null) {
            throw new AuthBadRequestException(ApiErrorMsg.IS_NULL);
        }
        return user;
    }

    private void ensureCanOperateUser(String currentUserId, String targetUserId) {
        if (StringUtils.isBlank(currentUserId)) {
            throw new UnauthorizedException("用户未登录");
        }
        if (StringUtils.isBlank(targetUserId)) {
            throw new AuthBadRequestException("用户ID不能为空");
        }
        if (!currentUserId.equals(targetUserId) && !SecurityContext.isAdmin()) {
            throw new AccountOperationForbiddenException();
        }
    }

    private String trimToNull(String value) {
        return StringUtils.isBlank(value) ? null : value.trim();
    }
}
