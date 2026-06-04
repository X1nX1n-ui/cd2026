package com.cd.server.impl;

import com.cd.entity.PageResult;
import com.cd.entity.Role;
import com.cd.entity.RoleOption;
import com.cd.entity.UserRoleRelation;
import com.cd.exception.BusinessException;
import com.cd.exception.ResourceNotFoundException;
import com.cd.mapper.RoleMapper;
import com.cd.server.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoleServiceImpl implements RoleService {

    private static final String SUPER_ADMIN_ROLE_CODE = "SUPER_ADMIN";

    private final RoleMapper roleMapper;

    public RoleServiceImpl(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
    }

    @Override
    public PageResult<Role> page(int pageNo, int pageSize, String roleName) {
        int validPageNo = Math.max(pageNo, 1);
        int validPageSize = Math.max(pageSize, 1);
        int offset = (validPageNo - 1) * validPageSize;

        PageResult<Role> pageResult = new PageResult<>();
        pageResult.setPageNo(validPageNo);
        pageResult.setPageSize(validPageSize);
        pageResult.setTotal(roleMapper.count(trimToNull(roleName)));
        List<Role> roles = roleMapper.selectPage(trimToNull(roleName), offset, validPageSize);
        batchEnrichPermissions(roles);
        pageResult.setRecords(roles);
        return pageResult;
    }

    @Override
    public Role getById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new ResourceNotFoundException("role not found, id=" + id);
        }
        return enrichPermissions(role);
    }

    @Override
    public List<RoleOption> options() {
        return roleMapper.selectOptions();
    }

    @Override
    @Transactional
    public Role create(Role role) {
        normalizeRole(role);
        validate(role, true, null);
        roleMapper.insert(role);
        replaceRolePermissions(role.getId(), role.getPermissionIds());
        return getById(role.getId());
    }

    @Override
    @Transactional
    public Role update(Role role) {
        if (role.getId() == null) {
            throw new BusinessException("角色 ID 不能为空");
        }
        Role existingRole = roleMapper.selectById(role.getId());
        if (existingRole == null) {
            throw new ResourceNotFoundException("role not found, id=" + role.getId());
        }

        normalizeRole(role);
        validate(role, false, existingRole);

        int rows = roleMapper.update(role);
        if (rows == 0) {
            throw new ResourceNotFoundException("role not found, id=" + role.getId());
        }
        if (role.getPermissionIds() != null) {
            replaceRolePermissions(role.getId(), role.getPermissionIds());
        }
        return getById(role.getId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        Role existingRole = roleMapper.selectById(id);
        if (existingRole == null) {
            throw new ResourceNotFoundException("role not found, id=" + id);
        }
        if (SUPER_ADMIN_ROLE_CODE.equals(existingRole.getRoleCode())) {
            throw new BusinessException("超级管理员角色不允许删除");
        }

        roleMapper.deleteRolePermissionsByRoleId(id);
        roleMapper.deleteUserRolesByRoleId(id);
        int rows = roleMapper.deleteById(id);
        if (rows == 0) {
            throw new ResourceNotFoundException("role not found, id=" + id);
        }
    }

    private void validate(Role role, boolean createMode, Role existingRole) {
        if (createMode && isBlank(role.getRoleName())) {
            throw new BusinessException("角色名称不能为空");
        }
        if (createMode && isBlank(role.getRoleCode())) {
            throw new BusinessException("角色编码不能为空");
        }
        if (!createMode) {
            if (role.getRoleName() != null && role.getRoleName().isBlank()) {
                throw new BusinessException("角色名称不能为空");
            }
            if (role.getRoleCode() != null && role.getRoleCode().isBlank()) {
                throw new BusinessException("角色编码不能为空");
            }
        }

        String roleCode = trimToNull(role.getRoleCode());
        if (roleCode != null) {
            Role duplicatedRole = roleMapper.selectByCode(roleCode);
            if (duplicatedRole != null && (createMode || !duplicatedRole.getId().equals(role.getId()))) {
                throw new BusinessException("角色编码已存在");
            }
        }

        if (!createMode && existingRole != null && SUPER_ADMIN_ROLE_CODE.equals(existingRole.getRoleCode())) {
            if (roleCode != null && !SUPER_ADMIN_ROLE_CODE.equals(roleCode)) {
                throw new BusinessException("超级管理员角色编码不允许修改");
            }
        }
    }

    private void normalizeRole(Role role) {
        if (role.getRoleName() != null) {
            role.setRoleName(role.getRoleName().trim());
        }
        if (role.getRoleCode() != null) {
            role.setRoleCode(role.getRoleCode().trim());
        }
        if (role.getDescription() != null) {
            role.setDescription(role.getDescription().trim());
        }
    }

    private void replaceRolePermissions(Long roleId, List<Long> permissionIds) {
        roleMapper.deleteRolePermissionsByRoleId(roleId);
        if (permissionIds != null && !permissionIds.isEmpty()) {
            roleMapper.insertRolePermissions(roleId, permissionIds);
        }
    }

    private Role enrichPermissions(Role role) {
        role.setPermissionIds(new ArrayList<>(roleMapper.selectPermissionIdsByRoleId(role.getId())));
        return role;
    }

    private void batchEnrichPermissions(List<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> permissionMap = new LinkedHashMap<>();
        for (Role role : roles) {
            permissionMap.put(role.getId(), new ArrayList<>());
        }
        List<UserRoleRelation> relations = roleMapper.selectRolePermissionRelations(roles.stream().map(Role::getId).toList());
        for (UserRoleRelation relation : relations) {
            permissionMap.computeIfAbsent(relation.getRoleId(), key -> new ArrayList<>()).add(relation.getPermissionId());
        }
        for (Role role : roles) {
            role.setPermissionIds(permissionMap.getOrDefault(role.getId(), new ArrayList<>()));
        }
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
