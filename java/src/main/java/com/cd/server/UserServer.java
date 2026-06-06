package com.cd.server;

import com.cd.entity.DashboardSummary;
import com.cd.entity.LoginUser;
import com.cd.entity.PageResult;
import com.cd.entity.User;
import com.cd.entity.UserView;

public interface UserServer {

    PageResult<UserView> page(int pageNo, int pageSize, String userName);

    UserView getById(Long id);

    UserView getCurrentUser(Long id);

    UserView create(User user);

    UserView update(User user);

    UserView updateUserHeader(Long id, String userHeader);

    void deleteById(Long id);

    LoginUser login(String userName, String password, String ipAddress);

    LoginUser getLoginUserById(Long id);

    PageResult<com.cd.entity.LoginLog> loginLogPage(int pageNo, int pageSize, String userName);

    DashboardSummary getDashboardSummary();

    /**
     * ???????????????????
     * @param userId ????ID
     */
    void sendPasswordChangeVerificationCode(Long userId);

    /**
     * ????
     * @param userId ????ID
     * @param oldPassword ???
     * @param newPassword ???
     * @param verificationCode ?????
     */
    void changePassword(Long userId, String oldPassword, String newPassword, String verificationCode);
}
