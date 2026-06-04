package com.cd.mapper;

import com.cd.entity.Role;
import com.cd.entity.RoleOption;
import com.cd.entity.UserRoleRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RoleMapper {

    List<Role> selectPage(@Param("roleName") String roleName,
                          @Param("offset") int offset,
                          @Param("pageSize") int pageSize);

    long count(@Param("roleName") String roleName);

    Role selectById(@Param("id") Long id);

    Role selectByCode(@Param("roleCode") String roleCode);

    List<Role> selectByUserId(@Param("userId") Long userId);

    List<RoleOption> selectOptions();

    int insert(Role role);

    int update(Role role);

    int deleteById(@Param("id") Long id);

    List<Long> selectPermissionIdsByRoleId(@Param("roleId") Long roleId);

    List<UserRoleRelation> selectRolePermissionRelations(@Param("roleIds") List<Long> roleIds);

    int deleteRolePermissionsByRoleId(@Param("roleId") Long roleId);

    int deleteUserRolesByRoleId(@Param("roleId") Long roleId);

    int insertRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);
}
