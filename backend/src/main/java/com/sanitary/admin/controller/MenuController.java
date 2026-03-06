package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.SysMenu;
import com.sanitary.admin.service.SysMenuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController {

    private final SysMenuService sysMenuService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MENU_VIEW')")
    public Result<List<SysMenu>> list(@RequestParam(required = false) String menuName) {
        return Result.success(sysMenuService.listMenus(menuName));
    }

    @GetMapping("/tree")
    @PreAuthorize("hasAnyRole('ADMIN','MENU_VIEW')")
    public Result<List<SysMenu>> tree() {
        return Result.success(sysMenuService.getMenuTree());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MENU_VIEW')")
    public Result<SysMenu> getById(@PathVariable Long id) {
        SysMenu menu = sysMenuService.getById(id);
        if (menu == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单不存在");
        }
        return Result.success(menu);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MENU_ADD')")
    public Result<Void> add(@Valid @RequestBody SysMenu menu) {
        sysMenuService.save(menu);
        return Result.success();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MENU_EDIT')")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysMenu menu) {
        // 验证菜单存在
        if (sysMenuService.getById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单不存在");
        }
        menu.setId(id);
        sysMenuService.updateById(menu);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MENU_DELETE')")
    public Result<Void> delete(@PathVariable Long id) {
        // 检查是否有子菜单，防止产生孤儿节点
        long childCount = sysMenuService.count(
            new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id)
        );
        if (childCount > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "该菜单下存在子菜单，请先删除子菜单");
        }
        // 检查菜单是否存在
        if (sysMenuService.getById(id) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "菜单不存在");
        }
        sysMenuService.removeById(id);
        return Result.success();
    }
}
