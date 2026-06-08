package com.cd.controller;

import com.cd.entity.LoginRequest;
import com.cd.entity.LoginResponse;
import com.cd.entity.LoginUser;
import com.cd.entity.MenuNode;
import com.cd.security.JwtTokenService;
import com.cd.security.SecurityUtils;
import com.cd.server.PermissionService;
import com.cd.server.UserServer;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserServer userServer;
    private final PermissionService permissionService;
    private final JwtTokenService jwtTokenService;

    public AuthController(UserServer userServer,
                          PermissionService permissionService,
                          JwtTokenService jwtTokenService) {
        this.userServer = userServer;
        this.permissionService = permissionService;
        this.jwtTokenService = jwtTokenService;
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request, HttpServletRequest httpServletRequest) {
        LoginUser loginUser = userServer.login(request.getUserName(), request.getPassword(), resolveClientIp(httpServletRequest));

        LoginResponse response = new LoginResponse();
        response.setSuccess(true);
        response.setMessage("登录成功");
        response.setRemembered(Boolean.TRUE.equals(request.getRememberMe()));
        response.setToken(jwtTokenService.createToken(loginUser.getId(), loginUser.getUserName()));
        response.setUser(loginUser);
        return response;
    }

    @GetMapping("/me")
    public LoginResponse me() {
        LoginUser loginUser = userServer.getLoginUserById(SecurityUtils.currentUserId());
        LoginResponse response = new LoginResponse();
        response.setSuccess(true);
        response.setMessage("已登录");
        response.setUser(loginUser);
        return response;
    }

    @PostMapping("/send-reset-code")
    public Map<String, Object> sendResetCode(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return Map.of("success", false, "message", "??????");
        }
        try {
            userServer.sendPasswordResetCode(username.trim());
            return Map.of("success", true, "message", "???????????");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @PostMapping("/reset-password")
    public Map<String, Object> resetPassword(@RequestBody Map<String, Object> body) {
        String username = (String) body.get("username");
        String code = (String) body.get("code");
        String newPassword = (String) body.get("newPassword");
        if (username == null || username.isBlank()) {
            return Map.of("success", false, "message", "??????");
        }
        if (code == null || code.isBlank()) {
            return Map.of("success", false, "message", "??????");
        }
        if (newPassword == null || newPassword.isBlank()) {
            return Map.of("success", false, "message", "??????");
        }
        try {
            userServer.resetPassword(username.trim(), code.trim(), newPassword);
            return Map.of("success", true, "message", "????????????");
        } catch (Exception e) {
            return Map.of("success", false, "message", e.getMessage());
        }
    }

    @GetMapping("/menus")
    public List<MenuNode> menus() {
        return permissionService.currentMenus(SecurityUtils.currentUserId());
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
