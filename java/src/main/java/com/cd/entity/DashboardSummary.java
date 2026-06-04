package com.cd.entity;

public class DashboardSummary {

    private long totalUsers;
    private long normalUsers;
    private long lockedUsers;
    private long disabledUsers;
    private long todayLoginCount;
    private long todayFailedLoginCount;

    public long getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(long totalUsers) {
        this.totalUsers = totalUsers;
    }

    public long getNormalUsers() {
        return normalUsers;
    }

    public void setNormalUsers(long normalUsers) {
        this.normalUsers = normalUsers;
    }

    public long getLockedUsers() {
        return lockedUsers;
    }

    public void setLockedUsers(long lockedUsers) {
        this.lockedUsers = lockedUsers;
    }

    public long getDisabledUsers() {
        return disabledUsers;
    }

    public void setDisabledUsers(long disabledUsers) {
        this.disabledUsers = disabledUsers;
    }

    public long getTodayLoginCount() {
        return todayLoginCount;
    }

    public void setTodayLoginCount(long todayLoginCount) {
        this.todayLoginCount = todayLoginCount;
    }

    public long getTodayFailedLoginCount() {
        return todayFailedLoginCount;
    }

    public void setTodayFailedLoginCount(long todayFailedLoginCount) {
        this.todayFailedLoginCount = todayFailedLoginCount;
    }
}
