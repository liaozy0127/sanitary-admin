package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.common.PageResult;
import com.sanitary.admin.entity.SysUser;
import com.sanitary.admin.mapper.SysUserMapper;
import com.sanitary.admin.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    @Override
    public PageResult<SysUser> pageUsers(Integer pageNum, Integer pageSize, String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SysUser::getUsername, username);
        }
        wrapper.orderByDesc(SysUser::getCreateTime);
        Page<SysUser> page = this.page(new Page<>(pageNum, pageSize), wrapper);
        return PageResult.of(page.getTotal(), page.getRecords());
    }

    @Override
    public SysUser getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
    }
}
