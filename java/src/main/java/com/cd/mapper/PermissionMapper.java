package com.cd.mapper;

import com.cd.entity.Permission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface PermissionMapper {

    List<Permission> selectPage(@Param("permissionName") String permissionName,
                                @Param("offset") int offset,
                                @Param("pageSize") int pageSize);

    long count(@Param("permissionName") String permissionName);

    Permission selectById(@Param("id") Long id);

    Permission selectByCode(@Param("permissionCode") String permissionCode);

    List<Permission> selectAll();

    List<Permission> selectAllVisibleMenus();

    List<Permission> selectMenusByUserId(@Param("userId") Long userId);

    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

    long countChildrenByParentId(@Param("parentId") Long parentId);

    int insert(Permission permission);

    int update(Permission permission);

    int deleteById(@Param("id") Long id);

    int deleteRolePermissionsByPermissionId(@Param("permissionId") Long permissionId);
}
