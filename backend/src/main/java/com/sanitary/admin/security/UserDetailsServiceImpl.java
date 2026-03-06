package com.sanitary.admin.security;

import com.sanitary.admin.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        com.sanitary.admin.entity.SysUser sysUser = sysUserMapper.selectOne(
                new LambdaQueryWrapper<com.sanitary.admin.entity.SysUser>()
                        .eq(com.sanitary.admin.entity.SysUser::getUsername, username)
                        .eq(com.sanitary.admin.entity.SysUser::getStatus, 1));
        if (sysUser == null) {
            throw new UsernameNotFoundException("用户不存在或已禁用: " + username);
        }
        return new User(sysUser.getUsername(), sysUser.getPassword(),
                List.of(new SimpleGrantedAuthority("ROLE_" + sysUser.getRole())));
    }
}
