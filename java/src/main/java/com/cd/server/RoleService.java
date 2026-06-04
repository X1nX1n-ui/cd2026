package com.cd.server;

import com.cd.entity.PageResult;
import com.cd.entity.Role;
import com.cd.entity.RoleOption;

import java.util.List;

public interface RoleService {

    PageResult<Role> page(int pageNo, int pageSize, String roleName);

    Role getById(Long id);

    List<RoleOption> options();

    Role create(Role role);

    Role update(Role role);

    void deleteById(Long id);
}
