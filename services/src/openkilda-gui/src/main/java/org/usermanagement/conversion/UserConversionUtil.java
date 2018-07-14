package org.usermanagement.conversion;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.openkilda.constants.Status;
import org.openkilda.utility.StringUtil;
import org.usermanagement.dao.entity.RoleEntity;
import org.usermanagement.dao.entity.StatusEntity;
import org.usermanagement.dao.entity.UserEntity;
import org.usermanagement.model.UserInfo;
import org.usermanagement.util.ValidatorUtil;

public class UserConversionUtil {

    private static String password = "Admin@2018";

    public static UserEntity toUserEntity(final UserInfo userInfo, final Set<RoleEntity> roleEntities) {

        UserEntity userEntity = new UserEntity();

        userEntity.setUsername(userInfo.getUsername());
        userEntity.setPassword(StringUtil.encodeString(password));
        userEntity.setEmail(userInfo.getEmail());
        userEntity.setName(userInfo.getName());
        userEntity.setRoles(roleEntities);
        userEntity.setActiveFlag(true);
        userEntity.setCreatedBy(1l);
        userEntity.setCreatedDate(new Date());
        userEntity.setUpdatedBy(1l);
        userEntity.setUpdatedDate(new Date());
        userEntity.setLoginTime(new Timestamp(System.currentTimeMillis()));
        userEntity.setLogoutTime(new Timestamp(System.currentTimeMillis()));
        userEntity.setIsAuthorized(true);
        userEntity.setIs2FaEnabled(true);
        userEntity.setIs2FaConfigured(false);
        StatusEntity statusEntity = Status.ACTIVE.getStatusEntity();
        userEntity.setStatusEntity(statusEntity);
        return userEntity;
    }

    public static UserInfo toUserInfo(final UserEntity userEntity) {
        UserInfo userInfo = new UserInfo();
        userInfo.setName(userEntity.getName());
        userInfo.setEmail(userEntity.getEmail());
        userInfo.setUsername(userEntity.getUsername());
        userInfo.setIs2FaEnabled(userEntity.getIs2FaEnabled());
        userInfo.setStatus(userEntity.getStatusEntity().getStatus());
        userInfo.setUserId(userEntity.getUserId());
        Set<String> roles = new HashSet<>();

        if (!ValidatorUtil.isNull(userEntity.getRoles())) {
            for (RoleEntity roleEntity : userEntity.getRoles()) {
                roles.add(roleEntity.getName());
            }
            userInfo.setRoles(roles);
        }
        return userInfo;
    }

    public static List<UserInfo> toAllUsers(final List<UserEntity> userEntityList) {
        List<UserInfo> userList = new ArrayList<>();

        for (UserEntity userEntity : userEntityList) {
			if (userEntity.getUserId() != 1) {
				userList.add(toUserInfo(userEntity));
			}
        }
        return userList;
    }

    public static void toUpateUserEntity(final UserInfo userInfo, final UserEntity userEntity) {
        if (!ValidatorUtil.isNull(userInfo.getName())) {
            userEntity.setName(userInfo.getName());
        }

        if (!ValidatorUtil.isNull(userInfo.getStatus())) {
            Status status = Status.getStatusByName(userInfo.getStatus());
            if(status == Status.ACTIVE) {
                userEntity.setActiveFlag(true);
            } else if(status == Status.INACTIVE) {
                userEntity.setActiveFlag(false);
            }
            userEntity.setStatusEntity(status.getStatusEntity());
        }

        if (!ValidatorUtil.isNull(userInfo.getPassword())) {
            userEntity.setPassword(StringUtil.encodeString(userInfo.getPassword()));
        }
        userEntity.setUpdatedDate(new Date());
    }

    public static UserEntity toResetPwdUserEntity(final UserEntity userEntity, final String randomPassword) {
        userEntity.setPassword(StringUtil.encodeString(randomPassword));
        userEntity.setUpdatedDate(new Date());
        return userEntity;
    }
}
