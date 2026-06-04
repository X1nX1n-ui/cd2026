package com.cd.controller;

import com.cd.entity.PageResult;
import com.cd.entity.Role;
import com.cd.entity.RoleOption;
import com.cd.server.RoleService;
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
@RequestMapping("/api/roles")
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('sys:role:view')")
    public PageResult<Role> page(@RequestParam(defaultValue = "1") int pageNo,
                                 @RequestParam(defaultValue = "10") int pageSize,
                                 @RequestParam(required = false) String roleName) {
        return roleService.page(pageNo, pageSize, roleName);
    }

    @GetMapping("/options")
    @PreAuthorize("hasAnyAuthority('sys:user:view','sys:user:create','sys:user:update','sys:role:view','sys:role:create','sys:role:update')")
    public List<RoleOption> options() {
        return roleService.options();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:view')")
    public Role getById(@PathVariable Long id) {
        return roleService.getById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('sys:role:create')")
    public Role create(@RequestBody Role role) {
        return roleService.create(role);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:role:update')")
    public Role update(@PathVariable Long id, @RequestBody Role role) {
        role.setId(id);
        return roleService.update(role);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('sys:role:delete')")
    public void delete(@PathVariable Long id) {
        roleService.deleteById(id);
    }
}
