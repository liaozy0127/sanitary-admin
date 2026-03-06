package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysRole;
import com.sanitary.admin.mapper.SysRoleMapper;
import com.sanitary.admin.service.SysRoleService;
import com.sanitary.admin.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveRole(SysRole role) {
        // roleCode 唯一性校验
        long count = this.count(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode()));
        if (count > 0) {
            throw new BusinessException("角色编码已存在：" + role.getRoleCode());
        }
        this.save(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(SysRole role) {
        // 检查角色是否存在
        SysRole existing = this.getById(role.getId());
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        
        // roleCode 唯一性校验（排除当前角色）
        long count = this.count(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, role.getRoleCode())
                .ne(SysRole::getId, role.getId()));
        if (count > 0) {
            throw new BusinessException("角色编码已存在：" + role.getRoleCode());
        }
        
        this.updateById(role);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        // 检查角色是否存在
        SysRole existing = this.getById(id);
        if (existing == null) {
            throw new BusinessException("角色不存在");
        }
        
        // 检查是否有用户关联此角色（暂时只抛出提示，不做实际关联查询）
        // TODO: 实际关联查询应在 user-role 关联表建立后实现
        // long userCount = sysUserRoleMapper.selectCount(
        //         new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getRoleId, id));
        // if (userCount > 0) {
        //     throw new BusinessException("该角色已分配给 " + userCount + " 个用户，无法删除");
        // }
        
        this.removeById(id);
    }
}