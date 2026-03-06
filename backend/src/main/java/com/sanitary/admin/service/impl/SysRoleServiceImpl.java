package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysRole;
import com.sanitary.admin.mapper.SysRoleMapper;
import com.sanitary.admin.service.SysRoleService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
    @Override
    public List<SysRole> listRoles(String roleName) {
        LambdaQueryWrapper<SysRole> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(roleName)) {
            wrapper.like(SysRole::getRoleName, roleName);
        }
        wrapper.orderByAsc(SysRole::getId);
        return this.list(wrapper);
    }
}