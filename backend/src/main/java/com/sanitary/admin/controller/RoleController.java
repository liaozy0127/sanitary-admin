package com.sanitary.admin.controller;

import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.SysRole;
import com.sanitary.admin.service.SysRoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {
    private final SysRoleService sysRoleService;

    @GetMapping
    public Result<Map<String, Object>> list(@RequestParam(required = false) String roleName) {
        List<SysRole> roles = sysRoleService.listRoles(roleName);
        Map<String, Object> result = new HashMap<>();
        result.put("records", roles);
        result.put("total", roles.size());
        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<SysRole> getById(@PathVariable Long id) {
        SysRole role = sysRoleService.getById(id);
        if (role == null) return Result.error(404, "角色不存在");
        return Result.success(role);
    }

    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysRole role) {
        sysRoleService.saveRole(role);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Validated @RequestBody SysRole role) {
        // 检查角色是否存在
        SysRole existing = sysRoleService.getById(id);
        if (existing == null) return Result.error(404, "角色不存在");
        
        role.setId(id);
        sysRoleService.updateRole(role);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // 检查角色是否存在
        SysRole existing = sysRoleService.getById(id);
        if (existing == null) return Result.error(404, "角色不存在");
        
        sysRoleService.deleteRole(id);
        return Result.success();
    }
}