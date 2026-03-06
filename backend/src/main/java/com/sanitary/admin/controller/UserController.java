package com.sanitary.admin.controller;

import com.sanitary.admin.common.PageResult;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.SysUser;
import com.sanitary.admin.service.SysUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public Result<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String username) {
        return Result.success(sysUserService.pageUsers(pageNum, pageSize, username));
    }

    @GetMapping("/{id}")
    public Result<SysUser> getById(@PathVariable Long id) {
        return Result.success(sysUserService.getById(id));
    }

    @PostMapping
    public Result<Void> add(@RequestBody SysUser user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setStatus(1);
        sysUserService.save(user);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser user) {
        user.setId(id);
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        } else {
            user.setPassword(null);
        }
        sysUserService.updateById(user);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysUserService.removeById(id);
        return Result.success();
    }
}
