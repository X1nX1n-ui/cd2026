package com.cd.entity;

public class LoginResponse {

    private boolean success;
    private String message;
    private boolean remembered;
    private String token;
    private LoginUser user;

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRemembered() {
        return remembered;
    }

    public void setRemembered(boolean remembered) {
        this.remembered = remembered;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public LoginUser getUser() {
        return user;
    }

    public void setUser(LoginUser user) {
        this.user = user;
    }
}
