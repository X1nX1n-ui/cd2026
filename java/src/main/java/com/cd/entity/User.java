package com.cd.entity;

import java.time.LocalDateTime;
import java.util.List;

public class User {

    private Long id;
    private String userName;
    private String userPwd;
    private String userHeader;
    private String userPhone;
    private String userEmail;
    private UserStatus userStatus;
    private Integer failedAttempts;
    private LocalDateTime lockTime;
    private String emailVerificationCode;
    private LocalDateTime emailVerificationExpire;
    private Integer passwordChangeFailures;
    private LocalDateTime passwordChangeLockedUntil;
    private Integer deleted;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginTime;
    private List<Long> roleIds;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getUserPwd() {
        return userPwd;
    }

    public void setUserPwd(String userPwd) {
        this.userPwd = userPwd;
    }

    public String getUserHeader() {
        return userHeader;
    }

    public void setUserHeader(String userHeader) {
        this.userHeader = userHeader;
    }

    public String getUserPhone() {
        return userPhone;
    }

    public void setUserPhone(String userPhone) {
        this.userPhone = userPhone;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public UserStatus getUserStatus() {
        return userStatus;
    }

    public void setUserStatus(UserStatus userStatus) {
        this.userStatus = userStatus;
    }

    public Integer getFailedAttempts() {
        return failedAttempts;
    }

    public void setFailedAttempts(Integer failedAttempts) {
        this.failedAttempts = failedAttempts;
    }

    public LocalDateTime getLockTime() {
        return lockTime;
    }

    public void setLockTime(LocalDateTime lockTime) {
        this.lockTime = lockTime;
    }

    public String getEmailVerificationCode() {
        return emailVerificationCode;
    }

    public void setEmailVerificationCode(String emailVerificationCode) {
        this.emailVerificationCode = emailVerificationCode;
    }

    public LocalDateTime getEmailVerificationExpire() {
        return emailVerificationExpire;
    }

    public void setEmailVerificationExpire(LocalDateTime emailVerificationExpire) {
        this.emailVerificationExpire = emailVerificationExpire;
    }

    public Integer getPasswordChangeFailures() {
        return passwordChangeFailures;
    }

    public void setPasswordChangeFailures(Integer passwordChangeFailures) {
        this.passwordChangeFailures = passwordChangeFailures;
    }

    public LocalDateTime getPasswordChangeLockedUntil() {
        return passwordChangeLockedUntil;
    }

    public void setPasswordChangeLockedUntil(LocalDateTime passwordChangeLockedUntil) {
        this.passwordChangeLockedUntil = passwordChangeLockedUntil;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(LocalDateTime lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public List<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<Long> roleIds) {
        this.roleIds = roleIds;
    }
}
