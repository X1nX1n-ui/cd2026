package com.cd.server.impl;

import com.cd.entity.MenuNode;
import com.cd.entity.PageResult;
import com.cd.entity.Permission;
import com.cd.entity.PermissionType;
import com.cd.entity.Role;
import com.cd.exception.BusinessException;
import com.cd.exception.ResourceNotFoundException;
import com.cd.mapper.PermissionMapper;
import com.cd.mapper.RoleMapper;
import com.cd.server.PermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PermissionServiceImpl implements PermissionService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final PermissionMapper permissionMapper;
    private final RoleMapper roleMapper;

    public PermissionServiceImpl(PermissionMapper permissionMapper, RoleMapper roleMapper) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public PageResult<Permission> page(int pageNo, int pageSize, String permissionName) {
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;

        PageResult<Permission> pageResult = new PageResult<>();
        pageResult.setPageNo(validPageNo);
        pageResult.setPageSize(validPageSize);
        pageResult.setTotal(permissionMapper.count(trimToNull(permissionName)));
        pageResult.setRecords(permissionMapper.selectPage(trimToNull(permissionName), offset, validPageSize));
        return pageResult;
    }

    @Override
    public Permission getById(Long id) {
        Permission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new ResourceNotFoundException("permission not found, id=" + id);
        }
        return permission;
    }

    @Override
    public List<Permission> options() {
        return permissionMapper.selectAll();
    }

    @Override
    public List<MenuNode> currentMenus(Long userId) {
        List<Permission> allMenus = permissionMapper.selectAllVisibleMenus();
        List<Permission> ownedMenus = permissionMapper.selectMenusByUserId(userId);
        if (ownedMenus.isEmpty()) {
            return List.of();
        }

        Map<Long, Permission> permissionMap = new LinkedHashMap<>();
        for (Permission permission : allMenus) {
            permissionMap.put(permission.getId(), permission);
        }

        Map<Long, Permission> accessibleMap = new LinkedHashMap<>();
        for (Permission permission : ownedMenus) {
            accessibleMap.put(permission.getId(), permission);
            Long parentId = permission.getParentId();
            while (parentId != null && parentId > 0) {
                Permission parent = permissionMap.get(parentId);
                if (parent == null || accessibleMap.containsKey(parentId)) {
                    break;
                }
                accessibleMap.put(parentId, parent);
                parentId = parent.getParentId();
            }
        }

        Map<Long, MenuNode> nodeMap = new LinkedHashMap<>();
        for (Permission permission : accessibleMap.values()) {
            nodeMap.put(permission.getId(), toMenuNode(permission));
        }

        List<MenuNode> roots = new ArrayList<>();
        for (Permission permission : accessibleMap.values().stream()
                .sorted(Comparator.comparing(Permission::getSortNo, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Permission::getId))
                .toList()) {
            MenuNode current = nodeMap.get(permission.getId());
            Long parentId = permission.getParentId();
            if (parentId == null || parentId == 0 || !nodeMap.containsKey(parentId)) {
                roots.add(current);
            } else {
                nodeMap.get(parentId).getChildren().add(current);
            }
        }
        return roots;
    }

    @Override
    @Transactional
    public Permission create(Permission permission) {
        normalizePermission(permission);
        validate(permission, true, null);
        permissionMapper.insert(permission);
        attachToSuperAdmin(permission.getId());
        return getById(permission.getId());
    }

    @Override
    @Transactional
    public Permission update(Permission permission) {
        if (permission.getId() == null) {
            throw new BusinessException("权限 ID 不能为空");
        }
        Permission existingPermission = permissionMapper.selectById(permission.getId());
        if (existingPermission == null) {
            throw new ResourceNotFoundException("permission not found, id=" + permission.getId());
        }

        normalizePermission(permission);
        validate(permission, false, existingPermission);

        int rows = permissionMapper.update(permission);
        if (rows == 0) {
            throw new ResourceNotFoundException("permission not found, id=" + permission.getId());
        }
        return getById(permission.getId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Permission existingPermission = permissionMapper.selectById(id);
        if (existingPermission == null) {
            throw new ResourceNotFoundException("permission not found, id=" + id);
        }
        if (permissionMapper.countChildrenByParentId(id) > 0) {
            throw new BusinessException("当前权限下仍存在子权限，无法删除");
        }

        permissionMapper.deleteRolePermissionsByPermissionId(id);
        int rows = permissionMapper.deleteById(id);
        if (rows == 0) {
            throw new ResourceNotFoundException("permission not found, id=" + id);
        }
    }

    private void validate(Permission permission, boolean createMode, Permission existingPermission) {
        if (createMode && isBlank(permission.getPermissionName())) {
            throw new BusinessException("权限名称不能为空");
        }
        if (createMode && isBlank(permission.getPermissionCode())) {
            throw new BusinessException("权限编码不能为空");
        }
        if (createMode && permission.getPermissionType() == null) {
            throw new BusinessException("权限类型不能为空");
        }
        if (!createMode) {
            if (permission.getPermissionName() != null && permission.getPermissionName().isBlank()) {
                throw new BusinessException("权限名称不能为空");
            }
            if (permission.getPermissionCode() != null && permission.getPermissionCode().isBlank()) {
                throw new BusinessException("权限编码不能为空");
            }
            if (permission.getPermissionType() == null) {
                throw new BusinessException("权限类型不能为空");
            }
        }

        String permissionCode = trimToNull(permission.getPermissionCode());
        if (permissionCode != null) {
            Permission duplicatedPermission = permissionMapper.selectByCode(permissionCode);
            if (duplicatedPermission != null && (createMode || !duplicatedPermission.getId().equals(permission.getId()))) {
                throw new BusinessException("权限编码已存在");
            }
        }

        if (permission.getParentId() != null && permission.getId() != null && permission.getParentId().equals(permission.getId())) {
            throw new BusinessException("上级权限不能选择自己");
        }

        if (!createMode
                && existingPermission != null
                && permission.getPermissionType() == PermissionType.ACTION
                && existingPermission.getPermissionType() == PermissionType.MENU
                && permissionMapper.countChildrenByParentId(permission.getId()) > 0) {
            throw new BusinessException("存在子权限的菜单不能直接改为操作权限");
        }
    }

    private void normalizePermission(Permission permission) {
        if (permission.getPermissionName() != null) {
            permission.setPermissionName(permission.getPermissionName().trim());
        }
        if (permission.getPermissionCode() != null) {
            permission.setPermissionCode(permission.getPermissionCode().trim());
        }
        if (permission.getDescription() != null) {
            permission.setDescription(permission.getDescription().trim());
        }
        if (permission.getRoutePath() != null) {
            permission.setRoutePath(trimToNull(permission.getRoutePath()));
        }
        if (permission.getComponentPath() != null) {
            permission.setComponentPath(trimToNull(permission.getComponentPath()));
        }
        if (permission.getIcon() != null) {
            permission.setIcon(trimToNull(permission.getIcon()));
        }
        if (permission.getParentId() != null && permission.getParentId() <= 0) {
            permission.setParentId(null);
        }
        if (permission.getSortNo() == null) {
            permission.setSortNo(0);
        }
        if (permission.getPermissionType() == PermissionType.ACTION) {
            permission.setVisible(0);
            permission.setIcon(null);
            permission.setRoutePath(null);
            permission.setComponentPath(null);
        } else if (permission.getVisible() == null) {
            permission.setVisible(1);
        }
    }

    private void attachToSuperAdmin(Long permissionId) {
        Role superAdminRole = roleMapper.selectByCode(SUPER_ADMIN_ROLE_CODE);
        if (superAdminRole != null) {
            roleMapper.insertRolePermissions(superAdminRole.getId(), List.of(permissionId));
        }
    }

    private MenuNode toMenuNode(Permission permission) {
        MenuNode menuNode = new MenuNode();
        menuNode.setId(permission.getId());
        menuNode.setTitle(permission.getPermissionName());
        menuNode.setPermissionCode(permission.getPermissionCode());
        menuNode.setTabId("menu-" + permission.getId());
        menuNode.setUrl(permission.getRoutePath());
        menuNode.setIcon(permission.getIcon());
        return menuNode;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
