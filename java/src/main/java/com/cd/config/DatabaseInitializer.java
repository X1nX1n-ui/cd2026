package com.cd.config;

import com.cd.entity.PermissionType;
import com.cd.entity.UserStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String DEFAULT_ADMIN_USER_NAME = "admin";
    private static final String DEFAULT_ADMIN_PASSWORD = "4297f44b13955235245b2497399d7a93";
    private static final String DEFAULT_TEST_PASSWORD = "4297f44b13955235245b2497399d7a93";
    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";
    private static final String ANALYST_ROLE_CODE = "ANALYST";
    private static final String AUDITOR_ROLE_CODE = "AUDITOR";

    @Value("${app.database.initialize-on-startup:true}")
    private boolean initializeOnStartup;

    @Bean
    public ApplicationRunner applicationRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            if (!initializeOnStartup) {
                log.info("Database initialization on startup is disabled.");
                return;
            }

            try {
                ensureBaseTables(jdbcTemplate);
                ensureUserColumns(jdbcTemplate);
                normalizeExistingUsers(jdbcTemplate);
                ensureAdminUser(jdbcTemplate);
                initializePermissions(jdbcTemplate);
                initializeRoles(jdbcTemplate);
                initializeRolePermissions(jdbcTemplate);
                initializeUserRoles(jdbcTemplate);
                initializeTestUsers(jdbcTemplate);
                log.info("Database initialization completed successfully.");
            } catch (Exception ex) {
                log.warn("Database initialization skipped because the database is unavailable: {}", ex.getMessage());
                log.debug("Database initialization failure details", ex);
            }
        };
    }

    private void ensureBaseTables(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `test` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    name VARCHAR(64) NOT NULL,
                    remark VARCHAR(255) NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `login_log` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NULL,
                    user_name VARCHAR(64) NOT NULL,
                    ip_address VARCHAR(64) NOT NULL,
                    result VARCHAR(16) NOT NULL,
                    message VARCHAR(255) NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_login_log_user_name (user_name),
                    KEY idx_login_log_created_at (created_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `role` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    role_name VARCHAR(64) NOT NULL,
                    role_code VARCHAR(64) NOT NULL,
                    description VARCHAR(255) NULL,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_role_role_code (role_code)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `permission` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    permission_name VARCHAR(64) NOT NULL,
                    permission_code VARCHAR(64) NOT NULL,
                    permission_type VARCHAR(16) NOT NULL,
                    parent_id BIGINT NULL,
                    route_path VARCHAR(255) NULL,
                    component_path VARCHAR(255) NULL,
                    icon VARCHAR(64) NULL,
                    sort_no INT NOT NULL DEFAULT 0,
                    visible TINYINT NOT NULL DEFAULT 1,
                    description VARCHAR(255) NULL,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_permission_permission_code (permission_code),
                    KEY idx_permission_parent_id (parent_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `user_role` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_id BIGINT NOT NULL,
                    role_id BIGINT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_user_role_user_role (user_id, role_id),
                    KEY idx_user_role_role_id (role_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `role_permission` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    role_id BIGINT NOT NULL,
                    permission_id BIGINT NOT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_role_permission_role_permission (role_id, permission_id),
                    KEY idx_role_permission_permission_id (permission_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `hosts` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    hostname VARCHAR(128) NOT NULL,
                    ip_address VARCHAR(64) NULL,
                    mac_address VARCHAR(64) NOT NULL,
                    cpu_architecture VARCHAR(64) NULL,
                    cpu_name VARCHAR(255) NULL,
                    logical_cores INT NULL,
                    memory_available VARCHAR(64) NULL,
                    memory_total VARCHAR(64) NULL,
                    os_detail VARCHAR(255) NULL,
                    os_name VARCHAR(128) NULL,
                    os_type VARCHAR(64) NULL,
                    os_version VARCHAR(128) NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
                    last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    source_queue VARCHAR(64) NULL,
                    raw_payload TEXT NULL,
                    deleted TINYINT NOT NULL DEFAULT 0,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_hosts_mac_address (mac_address),
                    KEY idx_hosts_hostname (hostname),
                    KEY idx_hosts_ip_address (ip_address),
                    KEY idx_hosts_status (status),
                    KEY idx_hosts_last_seen_at (last_seen_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `accounts` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    mac_address VARCHAR(64) NOT NULL,
                    accounts LONGTEXT NULL,
                    shadow_accounts LONGTEXT NULL,
                    account_count INT NOT NULL DEFAULT 0,
                    shadow_account_count INT NOT NULL DEFAULT 0,
                    raw_payload LONGTEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_accounts_mac_address (mac_address),
                    KEY idx_accounts_updated_at (updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `services` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    mac_address VARCHAR(64) NOT NULL,
                    services LONGTEXT NULL,
                    service_count INT NOT NULL DEFAULT 0,
                    raw_payload LONGTEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_services_mac_address (mac_address),
                    KEY idx_services_updated_at (updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `processes` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    mac_address VARCHAR(64) NOT NULL,
                    processes LONGTEXT NULL,
                    process_count INT NOT NULL DEFAULT 0,
                    raw_payload LONGTEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_processes_mac_address (mac_address),
                    KEY idx_processes_updated_at (updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `apps` (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    mac_address VARCHAR(64) NOT NULL,
                    apps LONGTEXT NULL,
                    app_count INT NOT NULL DEFAULT 0,
                    raw_payload LONGTEXT NULL,
                    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_apps_mac_address (mac_address),
                    KEY idx_apps_updated_at (updated_at)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                """);
    }

    private void ensureUserColumns(JdbcTemplate jdbcTemplate) {
        boolean userStatusAdded = ensureUserColumn(jdbcTemplate, "user_status", "VARCHAR(16) NOT NULL DEFAULT 'NORMAL'");
        ensureUserColumn(jdbcTemplate, "failed_attempts", "INT NOT NULL DEFAULT 0");
        ensureUserColumn(jdbcTemplate, "lock_time", "DATETIME NULL");

        if (userStatusAdded && hasUserColumn(jdbcTemplate, "status")) {
            jdbcTemplate.update("""
                    UPDATE `user`
                    SET user_status = CASE
                        WHEN status = 1 THEN 'NORMAL'
                        ELSE 'DISABLED'
                    END
                    """);
        }

    }

    private void normalizeExistingUsers(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("""
                UPDATE `user`
                SET user_status = 'NORMAL'
                WHERE user_status IS NULL
                   OR user_status = ''
                """);
    }

    private void ensureAdminUser(JdbcTemplate jdbcTemplate) {
        Integer totalAdminCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `user` WHERE user_name = ?", Integer.class, DEFAULT_ADMIN_USER_NAME);
        if (totalAdminCount != null && totalAdminCount > 0) {
            jdbcTemplate.update("""
                    UPDATE `user`
                    SET deleted = 0,
                        user_pwd = COALESCE(NULLIF(user_pwd, ''), ?),
                        user_status = ?,
                        failed_attempts = 0,
                        lock_time = NULL,
                        user_header = COALESCE(NULLIF(user_header, ''), '/images/default-avatar.svg'),
                        user_phone = COALESCE(NULLIF(user_phone, ''), '13800138000'),
                        user_email = COALESCE(NULLIF(user_email, ''), 'admin@threat-platform.local')
                    WHERE user_name = ?
                    """, DEFAULT_ADMIN_PASSWORD, UserStatus.NORMAL.name(), DEFAULT_ADMIN_USER_NAME);
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO `user` (
                    user_name, user_pwd, user_header, user_phone, user_email,
                    user_status, failed_attempts, lock_time, deleted, last_login_time
                ) VALUES (?, ?, ?, ?, ?, ?, 0, NULL, 0, NULL)
                """,
                DEFAULT_ADMIN_USER_NAME, DEFAULT_ADMIN_PASSWORD, "/images/default-avatar.svg",
                "13800138000", "admin@threat-platform.local", UserStatus.NORMAL.name());
    }

    private void initializePermissions(JdbcTemplate jdbcTemplate) {
        upsertPermission(jdbcTemplate, "后台主页", "dashboard:view", PermissionType.MENU, null, "/pages/dashboard/home.html", "/pages/dashboard/home.html", "layout-dashboard", 10, 1, "后台主页");
        Long threatRootId = upsertPermission(jdbcTemplate, "威胁监测", "threat", PermissionType.MENU, null, null, null, "shield-alert", 20, 1, "威胁监测菜单");
        upsertPermission(jdbcTemplate, "告警中心", "threat:alerts:view", PermissionType.MENU, threatRootId, "/pages/threat/alerts.html", "/pages/threat/alerts.html", "bell-ring", 21, 1, "告警中心页面");
        upsertPermission(jdbcTemplate, "攻击链分析", "threat:attack-chain:view", PermissionType.MENU, threatRootId, "/pages/threat/attack-chain.html", "/pages/threat/attack-chain.html", "waypoints", 22, 1, "攻击链分析页面");
        upsertPermission(jdbcTemplate, "风险事件", "threat:risk-events:view", PermissionType.MENU, threatRootId, "/pages/threat/risk-events.html", "/pages/threat/risk-events.html", "siren", 23, 1, "风险事件页面");

        Long assetRootId = upsertPermission(jdbcTemplate, "资产管理", "asset", PermissionType.MENU, null, null, null, "server", 30, 1, "资产管理菜单");
        upsertPermission(jdbcTemplate, "重点资产", "asset:key-assets:view", PermissionType.MENU, assetRootId, "/pages/assets/key-assets.html", "/pages/assets/key-assets.html", "database", 31, 1, "重点资产页面");
        upsertPermission(jdbcTemplate, "暴露面扫描", "asset:exposure-scan:view", PermissionType.MENU, assetRootId, "/pages/assets/exposure-scan.html", "/pages/assets/exposure-scan.html", "scan-search", 32, 1, "暴露面扫描页面");
        Long hostManageId = upsertPermission(jdbcTemplate, "主机管理", "asset:host:view", PermissionType.MENU, assetRootId, "/pages/assets/hosts.html", "/pages/assets/hosts.html", "monitor-smartphone", 33, 1, "主机管理页面");
        upsertPermission(jdbcTemplate, "资产详情", "asset:detail:view", PermissionType.MENU, assetRootId, "/pages/assets/asset-detail.html", "/pages/assets/asset-detail.html", "folder-search-2", 34, 0, "资产详情页面");

        Long systemRootId = upsertPermission(jdbcTemplate, "系统管理", "system", PermissionType.MENU, null, null, null, "settings", 40, 1, "系统管理菜单");
        Long userListId = upsertPermission(jdbcTemplate, "用户管理", "sys:user:view", PermissionType.MENU, systemRootId, "/pages/user/list.html", "/pages/user/list.html", "users", 41, 1, "用户管理页面");
        Long roleListId = upsertPermission(jdbcTemplate, "角色管理", "sys:role:view", PermissionType.MENU, systemRootId, "/pages/system/roles.html", "/pages/system/roles.html", "badge-check", 42, 1, "角色管理页面");
        Long permissionListId = upsertPermission(jdbcTemplate, "权限管理", "sys:permission:view", PermissionType.MENU, systemRootId, "/pages/system/permissions.html", "/pages/system/permissions.html", "key-round", 43, 1, "权限管理页面");
        upsertPermission(jdbcTemplate, "策略配置", "sys:strategy:view", PermissionType.MENU, systemRootId, "/pages/system/strategy.html", "/pages/system/strategy.html", "sliders-horizontal", 44, 1, "策略配置页面");
        upsertPermission(jdbcTemplate, "个人信息", "user:profile:view", PermissionType.MENU, systemRootId, "/pages/user/profile.html", "/pages/user/profile.html", "circle-user-round", 45, 1, "个人信息页面");

        Long logsRootId = upsertPermission(jdbcTemplate, "系统日志", "logs", PermissionType.MENU, null, null, null, "scroll-text", 50, 1, "系统日志菜单");
        upsertPermission(jdbcTemplate, "登录日志", "sys:login-log:view", PermissionType.MENU, logsRootId, "/pages/logs/login.html", "/pages/logs/login.html", "logs", 51, 1, "登录日志页面");

        upsertPermission(jdbcTemplate, "新增用户", "sys:user:create", PermissionType.ACTION, userListId, null, null, null, 4101, 0, "新增用户");
        upsertPermission(jdbcTemplate, "修改用户", "sys:user:update", PermissionType.ACTION, userListId, null, null, null, 4102, 0, "修改用户");
        upsertPermission(jdbcTemplate, "删除用户", "sys:user:delete", PermissionType.ACTION, userListId, null, null, null, 4103, 0, "删除用户");
        upsertPermission(jdbcTemplate, "新增角色", "sys:role:create", PermissionType.ACTION, roleListId, null, null, null, 4201, 0, "新增角色");
        upsertPermission(jdbcTemplate, "修改角色", "sys:role:update", PermissionType.ACTION, roleListId, null, null, null, 4202, 0, "修改角色");
        upsertPermission(jdbcTemplate, "删除角色", "sys:role:delete", PermissionType.ACTION, roleListId, null, null, null, 4203, 0, "删除角色");
        upsertPermission(jdbcTemplate, "新增权限", "sys:permission:create", PermissionType.ACTION, permissionListId, null, null, null, 4301, 0, "新增权限");
        upsertPermission(jdbcTemplate, "修改权限", "sys:permission:update", PermissionType.ACTION, permissionListId, null, null, null, 4302, 0, "修改权限");
        upsertPermission(jdbcTemplate, "删除权限", "sys:permission:delete", PermissionType.ACTION, permissionListId, null, null, null, 4303, 0, "删除权限");
        upsertPermission(jdbcTemplate, "新增主机", "asset:host:create", PermissionType.ACTION, hostManageId, null, null, null, 3301, 0, "新增主机");
        upsertPermission(jdbcTemplate, "修改主机", "asset:host:update", PermissionType.ACTION, hostManageId, null, null, null, 3302, 0, "修改主机");
        upsertPermission(jdbcTemplate, "删除主机", "asset:host:delete", PermissionType.ACTION, hostManageId, null, null, null, 3303, 0, "删除主机");
    }

    private void initializeRoles(JdbcTemplate jdbcTemplate) {
        upsertRole(jdbcTemplate, "超级管理员", SUPER_ADMIN_ROLE_CODE, "默认拥有全部权限");
        upsertRole(jdbcTemplate, "安全分析员", ANALYST_ROLE_CODE, "负责威胁监测与资产查看");
        upsertRole(jdbcTemplate, "审计员", AUDITOR_ROLE_CODE, "负责查看概览与日志");
    }

    private void initializeRolePermissions(JdbcTemplate jdbcTemplate) {
        Long superAdminRoleId = getRoleId(jdbcTemplate, SUPER_ADMIN_ROLE_CODE);
        Long analystRoleId = getRoleId(jdbcTemplate, ANALYST_ROLE_CODE);
        Long auditorRoleId = getRoleId(jdbcTemplate, AUDITOR_ROLE_CODE);

        jdbcTemplate.queryForList("SELECT id FROM `permission` WHERE deleted = 0", Long.class)
                .forEach(permissionId -> ensureRolePermission(jdbcTemplate, superAdminRoleId, permissionId));

        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "dashboard:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "threat:alerts:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "threat:attack-chain:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "threat:risk-events:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "asset:key-assets:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "asset:exposure-scan:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "asset:host:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "asset:detail:view"));
        ensureRolePermission(jdbcTemplate, analystRoleId, getPermissionId(jdbcTemplate, "user:profile:view"));

        ensureRolePermission(jdbcTemplate, auditorRoleId, getPermissionId(jdbcTemplate, "dashboard:view"));
        ensureRolePermission(jdbcTemplate, auditorRoleId, getPermissionId(jdbcTemplate, "sys:login-log:view"));
        ensureRolePermission(jdbcTemplate, auditorRoleId, getPermissionId(jdbcTemplate, "user:profile:view"));
    }

    private void initializeUserRoles(JdbcTemplate jdbcTemplate) {
        ensureUserRole(jdbcTemplate, getUserId(jdbcTemplate, DEFAULT_ADMIN_USER_NAME), getRoleId(jdbcTemplate, SUPER_ADMIN_ROLE_CODE));
    }

    private void initializeTestUsers(JdbcTemplate jdbcTemplate) {
        Long superAdminRoleId = getRoleId(jdbcTemplate, SUPER_ADMIN_ROLE_CODE);
        Long analystRoleId = getRoleId(jdbcTemplate, ANALYST_ROLE_CODE);
        Long auditorRoleId = getRoleId(jdbcTemplate, AUDITOR_ROLE_CODE);

        upsertUser(jdbcTemplate, "supervisor", "13900000001", "supervisor@test.local");
        upsertUser(jdbcTemplate, "analyst01", "13900000002", "analyst01@test.local");
        upsertUser(jdbcTemplate, "auditor01", "13900000003", "auditor01@test.local");
        upsertUser(jdbcTemplate, "assetops", "13900000004", "assetops@test.local");

        ensureUserRole(jdbcTemplate, getUserId(jdbcTemplate, "supervisor"), superAdminRoleId);
        ensureUserRole(jdbcTemplate, getUserId(jdbcTemplate, "analyst01"), analystRoleId);
        ensureUserRole(jdbcTemplate, getUserId(jdbcTemplate, "auditor01"), auditorRoleId);
        ensureUserRole(jdbcTemplate, getUserId(jdbcTemplate, "assetops"), analystRoleId);
    }

    private void upsertUser(JdbcTemplate jdbcTemplate, String userName, String userPhone, String userEmail) {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM `user` WHERE user_name = ?", Integer.class, userName);
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE `user`
                    SET user_pwd = ?,
                        user_header = COALESCE(NULLIF(user_header, ''), '/images/default-avatar.svg'),
                        user_phone = ?,
                        user_email = ?,
                        user_status = 'NORMAL',
                        failed_attempts = 0,
                        lock_time = NULL,
                        deleted = 0
                    WHERE user_name = ?
                    """, DEFAULT_TEST_PASSWORD, userPhone, userEmail, userName);
            return;
        }

        jdbcTemplate.update("""
                INSERT INTO `user` (
                    user_name, user_pwd, user_header, user_phone, user_email,
                    user_status, failed_attempts, lock_time, deleted, last_login_time
                ) VALUES (?, ?, ?, ?, ?, 'NORMAL', 0, NULL, 0, NULL)
                """, userName, DEFAULT_TEST_PASSWORD, "/images/default-avatar.svg", userPhone, userEmail);
    }

    private Long upsertPermission(JdbcTemplate jdbcTemplate, String permissionName, String permissionCode, PermissionType permissionType,
                                  Long parentId, String routePath, String componentPath, String icon, Integer sortNo, Integer visible,
                                  String description) {
        jdbcTemplate.update("""
                INSERT INTO `permission` (
                    permission_name, permission_code, permission_type, parent_id, route_path,
                    component_path, icon, sort_no, visible, description, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    permission_name = VALUES(permission_name),
                    permission_type = VALUES(permission_type),
                    parent_id = VALUES(parent_id),
                    route_path = VALUES(route_path),
                    component_path = VALUES(component_path),
                    icon = VALUES(icon),
                    sort_no = VALUES(sort_no),
                    visible = VALUES(visible),
                    description = VALUES(description),
                    deleted = 0
                """,
                permissionName, permissionCode, permissionType.name(), parentId, routePath, componentPath, icon, sortNo, visible, description);
        return getPermissionId(jdbcTemplate, permissionCode);
    }

    private void upsertRole(JdbcTemplate jdbcTemplate, String roleName, String roleCode, String description) {
        jdbcTemplate.update("""
                INSERT INTO `role` (role_name, role_code, description, deleted)
                VALUES (?, ?, ?, 0)
                ON DUPLICATE KEY UPDATE
                    role_name = VALUES(role_name),
                    description = VALUES(description),
                    deleted = 0
                """, roleName, roleCode, description);
    }

    private void ensureUserRole(JdbcTemplate jdbcTemplate, Long userId, Long roleId) {
        jdbcTemplate.update("INSERT IGNORE INTO `user_role` (user_id, role_id) VALUES (?, ?)", userId, roleId);
    }

    private void ensureRolePermission(JdbcTemplate jdbcTemplate, Long roleId, Long permissionId) {
        jdbcTemplate.update("INSERT IGNORE INTO `role_permission` (role_id, permission_id) VALUES (?, ?)", roleId, permissionId);
    }

    private Long getPermissionId(JdbcTemplate jdbcTemplate, String permissionCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM `permission` WHERE permission_code = ? AND deleted = 0 LIMIT 1", Long.class, permissionCode);
    }

    private Long getRoleId(JdbcTemplate jdbcTemplate, String roleCode) {
        return jdbcTemplate.queryForObject("SELECT id FROM `role` WHERE role_code = ? AND deleted = 0 LIMIT 1", Long.class, roleCode);
    }

    private Long getUserId(JdbcTemplate jdbcTemplate, String userName) {
        return jdbcTemplate.queryForObject("SELECT id FROM `user` WHERE user_name = ? AND deleted = 0 LIMIT 1", Long.class, userName);
    }

    private boolean ensureUserColumn(JdbcTemplate jdbcTemplate, String columnName, String columnDefinition) {
        if (hasUserColumn(jdbcTemplate, columnName)) {
            return false;
        }
        jdbcTemplate.execute("ALTER TABLE `user` ADD COLUMN " + columnName + " " + columnDefinition);
        return true;
    }

    private boolean hasUserColumn(JdbcTemplate jdbcTemplate, String columnName) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(1)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'user'
                  AND column_name = ?
                """, Integer.class, columnName);
        return count != null && count > 0;
    }

}
