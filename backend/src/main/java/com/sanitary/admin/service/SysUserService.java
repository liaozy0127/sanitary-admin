package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.common.PageResult;
import com.sanitary.admin.entity.SysUser;

public interface SysUserService extends IService<SysUser> {
    PageResult<SysUser> pageUsers(Integer pageNum, Integer pageSize, String username);
    SysUser getByUsername(String username);
}
