package com.cd.mapper;

import com.cd.entity.User;
import com.cd.entity.UserRoleRelation;
import com.cd.entity.UserStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMapper {

    List<User> selectPage(@Param("userName") String userName,
                          @Param("offset") int offset,
                          @Param("pageSize") int pageSize);

    long count(@Param("userName") String userName);

    User selectById(@Param("id") Long id);

    User selectByUserName(@Param("userName") String userName);

    List<Long> selectRoleIdsByUserId(@Param("userId") Long userId);

    List<UserRoleRelation> selectRoleRelationsByUserIds(@Param("userIds") List<Long> userIds);

    int insert(User user);

    int update(User user);

    int updateAuthState(User user);

    int clearLockState(@Param("id") Long id, @Param("userStatus") UserStatus userStatus);

    int unlockExpiredUsers(@Param("unlockBefore") java.time.LocalDateTime unlockBefore);

    int resetExpiredFailures(@Param("failureBefore") java.time.LocalDateTime failureBefore);

    int deleteById(@Param("id") Long id);

    int deleteUserRolesByUserId(@Param("userId") Long userId);

    int insertUserRoles(@Param("userId") Long userId, @Param("roleIds") List<Long> roleIds);

    long countAll();

    long countByStatus(@Param("userStatus") UserStatus userStatus);

    int updatePassword(@Param("id") Long id, @Param("userPwd") String userPwd);

    int updateEmailVerificationCode(@Param("id") Long id,
                                     @Param("emailVerificationCode") String emailVerificationCode,
                                     @Param("emailVerificationExpire") java.time.LocalDateTime emailVerificationExpire);

    int clearEmailVerificationCode(@Param("id") Long id);

    int recordPasswordChangeFailure(@Param("id") Long id,
                                     @Param("passwordChangeFailures") Integer passwordChangeFailures,
                                     @Param("passwordChangeLockedUntil") java.time.LocalDateTime passwordChangeLockedUntil);

    int resetPasswordChangeFailures(@Param("id") Long id);
}
