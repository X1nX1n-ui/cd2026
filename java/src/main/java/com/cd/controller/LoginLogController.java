package com.cd.controller;

import com.cd.entity.LoginLog;
import com.cd.entity.PageResult;
import com.cd.server.UserServer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/logs")
public class LoginLogController {

    private final UserServer userServer;

    public LoginLogController(UserServer userServer) {
        this.userServer = userServer;
    }

    @GetMapping("/login")
    @PreAuthorize("hasAuthority('sys:login-log:view')")
    public PageResult<LoginLog> page(@RequestParam(defaultValue = "1") int pageNo,
                                     @RequestParam(defaultValue = "10") int pageSize,
                                     @RequestParam(required = false) String userName) {
        return userServer.loginLogPage(pageNo, pageSize, userName);
    }
}
