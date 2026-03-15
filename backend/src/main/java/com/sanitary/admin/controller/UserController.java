package com.sanitary.admin.controller;

import com.sanitary.admin.common.PageResult;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.dto.UserUpdateDTO;
import com.sanitary.admin.entity.SysUser;
import com.sanitary.admin.service.SysUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER_VIEW')")
    public Result<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username) {
        return Result.success(sysUserService.pageUsers(pageNum, pageSize, username));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER_VIEW')")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','USER_ADD')")
    public Result<Void> add(@Valid @RequestBody SysUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        sysUserService.save(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER_EDIT')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody UserUpdateDTO userDto) {
        // 获取现有用户信息
        SysUser existingUser = sysUserService.getById(id);
        if (existingUser == null) {
            return Result.error(404, "用户不存在");
        }
        
        // 只更新非空字段
        if (userDto.getEmail() != null) existingUser.setEmail(userDto.getEmail());
        if (userDto.getPhone() != null) existingUser.setPhone(userDto.getPhone());
        if (userDto.getRole() != null) existingUser.setRole(userDto.getRole());
        if (userDto.getStatus() != null) existingUser.setStatus(userDto.getStatus());
        
        // 如果提供了新密码，则加密存储
        if (userDto.getPassword() != null && !userDto.getPassword().isEmpty()) {
            existingUser.setPassword(passwordEncoder.encode(userDto.getPassword()));
        }
        
        sysUserService.updateById(existingUser);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','USER_DELETE')")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return Result.success();
    }
}
