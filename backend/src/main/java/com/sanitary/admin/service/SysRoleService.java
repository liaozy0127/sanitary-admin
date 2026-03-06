package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.SysRole;

import java.util.List;

public interface SysRoleService extends IService<SysRole> {
    List<SysRole> listRoles(String roleName);
}