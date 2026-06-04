package com.cd.controller;

import com.cd.entity.DashboardSummary;
import com.cd.server.UserServer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final UserServer userServer;

    public DashboardController(UserServer userServer) {
        this.userServer = userServer;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('dashboard:view')")
    public DashboardSummary summary() {
        return userServer.getDashboardSummary();
    }
}
