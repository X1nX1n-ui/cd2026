package com.cd.server;

import com.cd.entity.MenuNode;
import com.cd.entity.PageResult;
import com.cd.entity.Permission;

import java.util.List;

public interface PermissionService {

    PageResult<Permission> page(int pageNo, int pageSize, String permissionName);

    Permission getById(Long id);

    List<Permission> options();

    List<MenuNode> currentMenus(Long userId);

    Permission create(Permission permission);

    Permission update(Permission permission);

    void deleteById(Long id);
}
