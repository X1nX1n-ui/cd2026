package com.cd.server.impl;

import com.cd.entity.DashboardSummary;
import com.cd.entity.LoginLog;
import com.cd.entity.LoginUser;
import com.cd.entity.PageResult;
import com.cd.entity.Role;
import com.cd.entity.User;
import com.cd.entity.UserRoleRelation;
import com.cd.entity.UserStatus;
import com.cd.entity.UserView;
import com.cd.exception.BusinessException;
import com.cd.exception.ResourceNotFoundException;
import com.cd.mapper.LoginLogMapper;
import com.cd.mapper.PermissionMapper;
import com.cd.mapper.RoleMapper;
import com.cd.mapper.UserMapper;
import com.cd.security.Md5PasswordEncoder;
import com.cd.server.UserServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class UserServerImpl implements UserServer {

    private static final String LOGIN_ERROR_MESSAGE = "用户名或密码错误，或账号已被锁定";
    private static final String LOGIN_SUCCESS_MESSAGE = "登录成功";
    private static final String DEFAULT_ADMIN_USER_NAME = "admin";
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long FAILED_WINDOW_MINUTES = 10L;
    private static final long LOCK_DURATION_MINUTES = 30L;

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final LoginLogMapper loginLogMapper;

    public UserServerImpl(UserMapper userMapper,
                          RoleMapper roleMapper,
                          PermissionMapper permissionMapper,
                          LoginLogMapper loginLogMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.loginLogMapper = loginLogMapper;
    }

    @Override
    public PageResult<UserView> page(int pageNo, int pageSize, String userName) {
        refreshExpiredAuthStates();
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;

        List<User> users = userMapper.selectPage(trimToNull(userName), offset, validPageSize);
        List<UserView> records = users.stream().map(this::toUserView).toList();
        enrichUserRoles(records);

        PageResult<UserView> pageResult = new PageResult<>();
        pageResult.setPageNo(validPageNo);
        pageResult.setPageSize(validPageSize);
        pageResult.setTotal(userMapper.count(trimToNull(userName)));
        pageResult.setRecords(records);
        return pageResult;
    }

    @Override
    public UserView getById(Long id) {
        refreshExpiredAuthStates();
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("user not found, id=" + id);
        }
        UserView userView = toUserView(user);
        enrichUserRoles(List.of(userView));
        return userView;
    }

    @Override
    public UserView getCurrentUser(Long id) {
        return getById(id);
    }

    @Override
    @Transactional
    public UserView create(User user) {
        normalizeUser(user);
        validateUser(user, true, null);
        applyManagedStatus(user, true, false);
        if (user.getDeleted() == null) {
            user.setDeleted(0);
        }
        user.setUserPwd(Md5PasswordEncoder.encode(user.getUserPwd()));
        ensureAdminSuperRole(user, null);
        userMapper.insert(user);
        replaceUserRoles(user.getId(), user.getRoleIds());
        return getById(user.getId());
    }

    @Override
    @Transactional
    public UserView update(User user) {
        if (user.getId() == null) {
            throw new BusinessException("用户 ID 不能为空");
        }
        User existingUser = userMapper.selectById(user.getId());
        if (existingUser == null) {
            throw new ResourceNotFoundException("user not found, id=" + user.getId());
        }

        normalizeUser(user);
        validateUser(user, false, existingUser);

        if (user.getUserPwd() != null && !user.getUserPwd().isBlank()) {
            user.setUserPwd(Md5PasswordEncoder.encode(user.getUserPwd()));
        } else {
            user.setUserPwd(null);
        }

        boolean protectedAdmin = DEFAULT_ADMIN_USER_NAME.equals(existingUser.getUserName());
        applyManagedStatus(user, false, protectedAdmin);
        ensureAdminSuperRole(user, existingUser);

        int rows = userMapper.update(user);
        if (rows == 0) {
            throw new ResourceNotFoundException("user not found, id=" + user.getId());
        }
        if (user.getUserStatus() == UserStatus.NORMAL || user.getUserStatus() == UserStatus.DISABLED) {
            userMapper.clearLockState(user.getId(), user.getUserStatus());
        }
        if (user.getRoleIds() != null) {
            replaceUserRoles(user.getId(), user.getRoleIds());
        }
        return getById(user.getId());
    }

    @Override
    @Transactional
    public UserView updateUserHeader(Long id, String userHeader) {
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new ResourceNotFoundException("user not found, id=" + id);
        }
        User user = new User();
        user.setId(id);
        user.setUserHeader(userHeader);
        int rows = userMapper.update(user);
        if (rows == 0) {
            throw new ResourceNotFoundException("user not found, id=" + id);
        }
        return getById(id);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        User existingUser = userMapper.selectById(id);
        if (existingUser == null) {
            throw new ResourceNotFoundException("user not found, id=" + id);
        }
        if (DEFAULT_ADMIN_USER_NAME.equals(existingUser.getUserName())) {
            throw new BusinessException("默认超级管理员不允许删除");
        }

        int rows = userMapper.deleteById(id);
        userMapper.deleteUserRolesByUserId(id);
        if (rows == 0) {
            throw new ResourceNotFoundException("user not found, id=" + id);
        }
    }

    @Override
    @Transactional(noRollbackFor = BusinessException.class)
    public LoginUser login(String userName, String password, String ipAddress) {
        if (isBlank(userName) || isBlank(password)) {
            recordLoginLog(null, trimToEmpty(userName), ipAddress, "FAILURE", LOGIN_ERROR_MESSAGE, LocalDateTime.now());
            throw new BusinessException(LOGIN_ERROR_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();
        User user = userMapper.selectByUserName(userName.trim());
        if (user == null) {
            recordLoginLog(null, userName.trim(), ipAddress, "FAILURE", LOGIN_ERROR_MESSAGE, now);
            throw new BusinessException(LOGIN_ERROR_MESSAGE);
        }

        normalizeAuthState(user, now);

        if (user.getUserStatus() == UserStatus.DISABLED) {
            recordLoginLog(user.getId(), user.getUserName(), ipAddress, "DISABLED", LOGIN_ERROR_MESSAGE, now);
            throw new BusinessException(LOGIN_ERROR_MESSAGE);
        }
        if (user.getUserStatus() == UserStatus.LOCKED && isWithinLockPeriod(user, now)) {
            recordLoginLog(user.getId(), user.getUserName(), ipAddress, "LOCKED", LOGIN_ERROR_MESSAGE, now);
            throw new BusinessException(LOGIN_ERROR_MESSAGE);
        }

        String encodedPassword = Md5PasswordEncoder.encode(password);
        if (!passwordMatches(password, user.getUserPwd(), encodedPassword)) {
            recordLoginFailure(user, now);
            recordLoginLog(
                    user.getId(),
                    user.getUserName(),
                    ipAddress,
                    user.getUserStatus() == UserStatus.LOCKED ? "LOCKED" : "FAILURE",
                    LOGIN_ERROR_MESSAGE,
                    now
            );
            throw new BusinessException(LOGIN_ERROR_MESSAGE);
        }

        clearLoginFailures(user, now);
        recordLoginLog(user.getId(), user.getUserName(), ipAddress, "SUCCESS", LOGIN_SUCCESS_MESSAGE, now);
        return getLoginUserById(user.getId());
    }

    @Override
    public LoginUser getLoginUserById(Long id) {
        refreshExpiredAuthStates();
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new ResourceNotFoundException("user not found, id=" + id);
        }

        List<Role> roles = roleMapper.selectByUserId(id);
        LoginUser loginUser = new LoginUser();
        loginUser.setId(user.getId());
        loginUser.setUserName(user.getUserName());
        loginUser.setUserHeader(user.getUserHeader());
        loginUser.setUserPhone(user.getUserPhone());
        loginUser.setUserEmail(user.getUserEmail());
        loginUser.setUserStatus(user.getUserStatus());
        loginUser.setLastLoginTime(user.getLastLoginTime());
        loginUser.setRoleCodes(roles.stream().map(Role::getRoleCode).toList());
        loginUser.setPermissionCodes(permissionMapper.selectPermissionCodesByUserId(id));
        return loginUser;
    }

    @Override
    public PageResult<LoginLog> loginLogPage(int pageNo, int pageSize, String userName) {
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;

        PageResult<LoginLog> pageResult = new PageResult<>();
        pageResult.setPageNo(validPageNo);
        pageResult.setPageSize(validPageSize);
        pageResult.setTotal(loginLogMapper.count(trimToNull(userName)));
        pageResult.setRecords(loginLogMapper.selectPage(trimToNull(userName), offset, validPageSize));
        return pageResult;
    }

    @Override
    public DashboardSummary getDashboardSummary() {
        refreshExpiredAuthStates();
        DashboardSummary summary = new DashboardSummary();
        summary.setTotalUsers(userMapper.countAll());
        summary.setNormalUsers(userMapper.countByStatus(UserStatus.NORMAL));
        summary.setLockedUsers(userMapper.countByStatus(UserStatus.LOCKED));
        summary.setDisabledUsers(userMapper.countByStatus(UserStatus.DISABLED));

        LocalDateTime startTime = LocalDate.now().atStartOfDay();
        LocalDateTime endTime = startTime.plusDays(1);
        summary.setTodayLoginCount(loginLogMapper.countToday(startTime, endTime));
        summary.setTodayFailedLoginCount(loginLogMapper.countTodayFailure(startTime, endTime));
        return summary;
    }

    private void validateUser(User user, boolean createMode, User existingUser) {
        if (createMode && isBlank(user.getUserName())) {
            throw new BusinessException("用户名不能为空");
        }
        if (!createMode && user.getUserName() != null && user.getUserName().isBlank()) {
            throw new BusinessException("用户名不能为空");
        }
        if (createMode && isBlank(user.getUserPwd())) {
            throw new BusinessException("密码不能为空");
        }

        String userName = trimToNull(user.getUserName());
        if (userName != null) {
            User duplicatedUser = userMapper.selectByUserName(userName);
            if (duplicatedUser != null && (createMode || !duplicatedUser.getId().equals(user.getId()))) {
                throw new BusinessException("用户名已存在");
            }
        }

        if (!createMode && existingUser != null && DEFAULT_ADMIN_USER_NAME.equals(existingUser.getUserName())) {
            if (userName != null && !DEFAULT_ADMIN_USER_NAME.equals(userName)) {
                throw new BusinessException("默认超级管理员用户名不允许修改");
            }
            if (user.getUserStatus() != null && user.getUserStatus() != UserStatus.NORMAL) {
                throw new BusinessException("默认超级管理员状态不允许修改");
            }
        }
    }

    private void applyManagedStatus(User user, boolean createMode, boolean protectedAdmin) {
        if (protectedAdmin) {
            user.setUserStatus(UserStatus.NORMAL);
            user.setFailedAttempts(0);
            user.setLockTime(null);
            return;
        }

        if (user.getUserStatus() == null) {
            user.setUserStatus(UserStatus.NORMAL);
        }
        if (user.getUserStatus() == UserStatus.LOCKED) {
            user.setFailedAttempts(MAX_FAILED_ATTEMPTS);
            if (user.getLockTime() == null) {
                user.setLockTime(LocalDateTime.now());
            }
            return;
        }
        if (createMode || user.getUserStatus() == UserStatus.NORMAL || user.getUserStatus() == UserStatus.DISABLED) {
            user.setFailedAttempts(0);
            user.setLockTime(null);
        }
    }

    private void refreshExpiredAuthStates() {
        LocalDateTime now = LocalDateTime.now();
        userMapper.unlockExpiredUsers(now.minusMinutes(LOCK_DURATION_MINUTES));
        userMapper.resetExpiredFailures(now.minusMinutes(FAILED_WINDOW_MINUTES));
    }

    private void normalizeAuthState(User user, LocalDateTime now) {
        if (user.getUserStatus() == null) {
            user.setUserStatus(UserStatus.NORMAL);
            persistAuthState(user);
            return;
        }
        if (user.getUserStatus() == UserStatus.LOCKED && !isWithinLockPeriod(user, now)) {
            user.setUserStatus(UserStatus.NORMAL);
            user.setFailedAttempts(0);
            user.setLockTime(null);
            persistAuthState(user);
            return;
        }
        if (user.getUserStatus() == UserStatus.NORMAL && hasExpiredFailureWindow(user, now)) {
            user.setFailedAttempts(0);
            user.setLockTime(null);
            persistAuthState(user);
        }
    }

    private boolean hasExpiredFailureWindow(User user, LocalDateTime now) {
        return user.getFailedAttempts() != null
                && user.getFailedAttempts() > 0
                && user.getLockTime() != null
                && !user.getLockTime().plusMinutes(FAILED_WINDOW_MINUTES).isAfter(now);
    }

    private boolean isWithinLockPeriod(User user, LocalDateTime now) {
        return user.getLockTime() != null && user.getLockTime().plusMinutes(LOCK_DURATION_MINUTES).isAfter(now);
    }

    private void recordLoginFailure(User user, LocalDateTime now) {
        int currentFailedAttempts = Objects.requireNonNullElse(user.getFailedAttempts(), 0);
        LocalDateTime failureWindowStart = user.getLockTime();

        if (currentFailedAttempts <= 0
                || failureWindowStart == null
                || !failureWindowStart.plusMinutes(FAILED_WINDOW_MINUTES).isAfter(now)) {
            user.setUserStatus(UserStatus.NORMAL);
            user.setFailedAttempts(1);
            user.setLockTime(now);
            persistAuthState(user);
            return;
        }

        int nextFailedAttempts = currentFailedAttempts + 1;
        if (nextFailedAttempts >= MAX_FAILED_ATTEMPTS) {
            user.setUserStatus(UserStatus.LOCKED);
            user.setFailedAttempts(nextFailedAttempts);
            user.setLockTime(now);
            persistAuthState(user);
            return;
        }

        user.setUserStatus(UserStatus.NORMAL);
        user.setFailedAttempts(nextFailedAttempts);
        persistAuthState(user);
    }

    private void clearLoginFailures(User user, LocalDateTime now) {
        user.setUserStatus(UserStatus.NORMAL);
        user.setFailedAttempts(0);
        user.setLockTime(null);
        user.setLastLoginTime(now);
        persistAuthState(user);
    }

    private void persistAuthState(User user) {
        int rows = userMapper.updateAuthState(user);
        if (rows == 0) {
            throw new ResourceNotFoundException("user not found, id=" + user.getId());
        }
    }

    private void recordLoginLog(Long userId,
                                String userName,
                                String ipAddress,
                                String result,
                                String message,
                                LocalDateTime createdAt) {
        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(userId);
        loginLog.setUserName(userName);
        loginLog.setIpAddress(trimToEmpty(ipAddress));
        loginLog.setResult(result);
        loginLog.setMessage(message);
        loginLog.setCreatedAt(createdAt);
        loginLogMapper.insert(loginLog);
    }

    private void replaceUserRoles(Long userId, List<Long> roleIds) {
        userMapper.deleteUserRolesByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            userMapper.insertUserRoles(userId, roleIds);
        }
    }

    private void enrichUserRoles(List<UserView> userViews) {
        if (userViews.isEmpty()) {
            return;
        }
        List<Long> userIds = userViews.stream().map(UserView::getId).toList();
        List<UserRoleRelation> relations = userMapper.selectRoleRelationsByUserIds(userIds);
        Map<Long, List<Long>> roleIdsMap = new LinkedHashMap<>();
        Map<Long, List<String>> roleNamesMap = new LinkedHashMap<>();
        for (UserRoleRelation relation : relations) {
            roleIdsMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleId());
            roleNamesMap.computeIfAbsent(relation.getUserId(), key -> new ArrayList<>()).add(relation.getRoleName());
        }
        for (UserView userView : userViews) {
            userView.setRoleIds(roleIdsMap.getOrDefault(userView.getId(), new ArrayList<>()));
            userView.setRoleNames(roleNamesMap.getOrDefault(userView.getId(), new ArrayList<>()));
        }
    }

    private UserView toUserView(User user) {
        UserView userView = new UserView();
        userView.setId(user.getId());
        userView.setUserName(user.getUserName());
        userView.setUserHeader(user.getUserHeader());
        userView.setUserPhone(user.getUserPhone());
        userView.setUserEmail(user.getUserEmail());
        userView.setUserStatus(user.getUserStatus());
        userView.setFailedAttempts(user.getFailedAttempts());
        userView.setLockTime(user.getLockTime());
        userView.setCreatedAt(user.getCreatedAt());
        userView.setUpdatedAt(user.getUpdatedAt());
        userView.setLastLoginTime(user.getLastLoginTime());
        return userView;
    }

    private void normalizeUser(User user) {
        if (user.getUserName() != null) {
            user.setUserName(user.getUserName().trim());
        }
        if (user.getUserPhone() != null) {
            user.setUserPhone(trimToNull(user.getUserPhone()));
        }
        if (user.getUserEmail() != null) {
            user.setUserEmail(trimToNull(user.getUserEmail()));
        }
        if (user.getUserHeader() != null) {
            user.setUserHeader(trimToNull(user.getUserHeader()));
        }
        if (user.getUserPwd() != null) {
            user.setUserPwd(user.getUserPwd().trim());
        }
    }

    private void ensureAdminSuperRole(User user, User existingUser) {
        boolean isAdminUser = DEFAULT_ADMIN_USER_NAME.equals(user.getUserName())
                || (existingUser != null && DEFAULT_ADMIN_USER_NAME.equals(existingUser.getUserName()));
        if (!isAdminUser) {
            return;
        }

        Role superAdminRole = roleMapper.selectByCode(SUPER_ADMIN_ROLE_CODE);
        if (superAdminRole == null) {
            return;
        }

        if (user.getRoleIds() == null) {
            user.setRoleIds(List.of(superAdminRole.getId()));
            return;
        }

        if (!user.getRoleIds().contains(superAdminRole.getId())) {
            List<Long> roleIds = new ArrayList<>(user.getRoleIds());
            roleIds.add(superAdminRole.getId());
            user.setRoleIds(roleIds);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean passwordMatches(String rawPassword, String storedPassword, String encodedPassword) {
        if (storedPassword == null || storedPassword.isBlank()) {
            return false;
        }
        String normalizedStored = storedPassword.trim();
        if (encodedPassword.equalsIgnoreCase(normalizedStored)) {
            return true;
        }
        return rawPassword != null && rawPassword.equals(normalizedStored);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
