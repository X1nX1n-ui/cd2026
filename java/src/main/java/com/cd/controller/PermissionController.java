package com.cd.controller;

import com.cd.entity.PageResult;
import com.cd.entity.Permission;
import com.cd.server.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
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

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public PageResult<Permission> page(@RequestParam(defaultValue = "1") int pageNo,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String permissionName) {
        return permissionService.page(pageNo, pageSize, permissionName);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('sys:permission:view','sys:permission:create','sys:permission:update','sys:role:view','sys:role:create','sys:role:update')")
    public List<Permission> options() {
        return permissionService.options();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:view')")
    public Permission getById(@PathVariable Long id) {
        return permissionService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sys:permission:create')")
    public Permission create(@RequestBody Permission permission) {
        return permissionService.create(permission);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:permission:update')")
    public Permission update(@PathVariable Long id, @RequestBody Permission permission) {
        permission.setId(id);
        return permissionService.update(permission);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('sys:permission:delete')")
    public void delete(@PathVariable Long id) {
        permissionService.deleteById(id);
    }
}
