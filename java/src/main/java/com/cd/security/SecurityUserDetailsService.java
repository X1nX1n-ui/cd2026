package com.cd.security;

import com.cd.entity.LoginUser;
import com.cd.entity.Role;
import com.cd.entity.User;
import com.cd.entity.UserStatus;
import com.cd.mapper.PermissionMapper;
import com.cd.mapper.RoleMapper;
import com.cd.mapper.UserMapper;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SecurityUserDetailsService implements UserDetailsService {

    private static final long LOCK_DURATION_MINUTES = 30L;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    public SecurityUserDetailsService(UserMapper userMapper,
                                      RoleMapper roleMapper,
                                      PermissionMapper permissionMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    @Override
    public AuthenticatedUser loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userMapper.selectByUserName(username);
        if (user == null) {
            throw new UsernameNotFoundException("user not found: " + username);
        }
        return buildAuthenticatedUser(user);
    }

    public AuthenticatedUser loadUserById(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new UsernameNotFoundException("user not found: " + userId);
        }
        return buildAuthenticatedUser(user);
    }

    private AuthenticatedUser buildAuthenticatedUser(User user) {
        if (user.getUserStatus() == UserStatus.DISABLED) {
            throw new DisabledException("账号已被禁用");
        }

        if (user.getUserStatus() == UserStatus.LOCKED) {
            LocalDateTime lockTime = user.getLockTime();
            if (lockTime != null && lockTime.plusMinutes(LOCK_DURATION_MINUTES).isAfter(LocalDateTime.now())) {
                throw new LockedException("账号已被锁定");
            }
            userMapper.clearLockState(user.getId(), UserStatus.NORMAL);
            user.setUserStatus(UserStatus.NORMAL);
            user.setFailedAttempts(0);
            user.setLockTime(null);
        }

        List<Role> roles = roleMapper.selectByUserId(user.getId());
        List<String> permissionCodes = permissionMapper.selectPermissionCodesByUserId(user.getId());
        List<GrantedAuthority> authorities = permissionCodes.stream()
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();

        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUserName(user.getUserName());
        loginUser.setUserHeader(user.getUserHeader());
        loginUser.setUserPhone(user.getUserPhone());
        loginUser.setUserEmail(user.getUserEmail());
        loginUser.setUserStatus(user.getUserStatus());
        loginUser.setLastLoginTime(user.getLastLoginTime());
        loginUser.setRoleCodes(roles.stream().map(Role::getRoleCode).toList());
        loginUser.setPermissionCodes(permissionCodes);

        return new AuthenticatedUser(
                user.getId(),
                user.getUserName(),
                user.getUserPwd(),
                user.getDeleted() == null || user.getDeleted() == 0,
                authorities,
                loginUser
        );
    }
}
