CREATE TABLE IF NOT EXISTS `test` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(64) NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(64) NOT NULL,
    user_pwd VARCHAR(32) NOT NULL,
    user_header VARCHAR(255) NULL,
    user_phone VARCHAR(32) NULL,
    user_email VARCHAR(128) NULL,
    user_status VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    failed_attempts INT NOT NULL DEFAULT 0,
    lock_time DATETIME NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_time DATETIME NULL,
    UNIQUE KEY uk_user_user_name (user_name),
    UNIQUE KEY uk_user_user_phone (user_phone),
    UNIQUE KEY uk_user_user_email (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `role` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_name VARCHAR(64) NOT NULL,
    role_code VARCHAR(64) NOT NULL,
    description VARCHAR(255) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_role_code (role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `user_role` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role_user_role (user_id, role_id),
    KEY idx_user_role_role_id (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS `role_permission` (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_role_permission_role_permission (role_id, permission_id),
    KEY idx_role_permission_permission_id (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `user` (
    user_name,
    user_pwd,
    user_header,
    user_phone,
    user_email,
    user_status,
    failed_attempts,
    lock_time,
    deleted,
    last_login_time
)
SELECT
    'admin',
    '4297f44b13955235245b2497399d7a93',
    '/images/default-avatar.svg',
    '13800138000',
    'admin@threat-platform.local',
    'NORMAL',
    0,
    NULL,
    0,
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM `user` WHERE user_name = 'admin'
);
