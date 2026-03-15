package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysRole;
import com.sanitary.admin.entity.SysUser;
import com.sanitary.admin.mapper.SysRoleMapper;
import com.sanitary.admin.mapper.SysUserMapper;
import com.sanitary.admin.service.SysRoleService;
import com.sanitary.admin.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
    
    @Autowired
    private SysUserMapper sysUserMapper;

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
        
        // 检查是否有用户使用此角色
        LambdaQueryWrapper<SysUser> userWrapper = new LambdaQueryWrapper<>();
        userWrapper.eq(SysUser::getRole, existing.getRoleCode()); // 根据角色编码匹配
        long userCount = sysUserMapper.selectCount(userWrapper);
        if (userCount > 0) {
            throw new BusinessException("该角色已被 " + userCount + " 个用户使用，无法删除");
        }
        
        this.removeById(id);
    }
}