package com.cd.controller;

import com.cd.entity.ChangePasswordRequest;
import com.cd.entity.PageResult;
import com.cd.entity.User;
import com.cd.entity.UserView;
import com.cd.exception.BusinessException;
import com.cd.security.SecurityUtils;
import com.cd.server.UserServer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/user")
public class UserController {

    private static final long AVATAR_MAX_SIZE = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final UserServer userServer;

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    public UserController(UserServer userServer) {
        this.userServer = userServer;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('sys:user:view')")
    public PageResult<UserView> page(@RequestParam(defaultValue = "1") int pageNo,
                                     @RequestParam(defaultValue = "10") int pageSize,
                                     @RequestParam(required = false) String userName) {
        return userServer.page(pageNo, pageSize, userName);
    }

    @GetMapping("/current")
    public UserView current() {
        return userServer.getCurrentUser(SecurityUtils.currentUserId());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:view')")
    public UserView getById(@PathVariable Long id) {
        return userServer.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sys:user:create')")
    public UserView create(@RequestBody User user) {
        return userServer.create(user);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:user:update')")
    public UserView update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        return userServer.update(user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('sys:user:delete')")
    public void delete(@PathVariable Long id) {
        userServer.deleteById(id);
    }

    @PostMapping("/current/password/send-code")
    public Map<String, Object> sendPasswordChangeCode() {
        userServer.sendPasswordChangeVerificationCode(SecurityUtils.currentUserId());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "??????????????5?????");
        return result;
    }

    @PostMapping("/current/password/change")
    public Map<String, Object> changePassword(@RequestBody ChangePasswordRequest request) {
        userServer.changePassword(
                SecurityUtils.currentUserId(),
                request.getOldPassword(),
                request.getNewPassword(),
                request.getVerificationCode()
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "???????????");
        return result;
    }

    @PostMapping("/current/avatar")
    public UserView uploadCurrentAvatar(@RequestParam("file") MultipartFile file) {
        String avatarUrl = storeAvatar(file, SecurityUtils.currentUserId());
        return userServer.updateUserHeader(SecurityUtils.currentUserId(), avatarUrl);
    }

    private String storeAvatar(MultipartFile file, Long userId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择头像图片");
        }
        if (file.getSize() > AVATAR_MAX_SIZE) {
            throw new BusinessException("头像图片不能超过 2MB");
        }

        String extension = getExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BusinessException("仅支持 jpg、jpeg、png、gif、webp 格式图片");
        }

        Path avatarDir = Paths.get(uploadDir, "avatars").toAbsolutePath().normalize();
        String fileName = userId + "-" + UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path target = avatarDir.resolve(fileName).normalize();
        if (!target.startsWith(avatarDir)) {
            throw new BusinessException("头像文件保存失败");
        }

        try {
            Files.createDirectories(avatarDir);
            file.transferTo(target);
        } catch (IOException exception) {
            throw new BusinessException("头像上传失败");
        }
        return "/uploads/avatars/" + fileName;
    }

    private String getExtension(String fileName) {
        String cleanedFileName = StringUtils.cleanPath(fileName == null ? "" : fileName);
        int index = cleanedFileName.lastIndexOf('.');
        if (index < 0 || index == cleanedFileName.length() - 1) {
            throw new BusinessException("头像文件格式不正确");
        }
        return cleanedFileName.substring(index + 1).toLowerCase();
    }
}
