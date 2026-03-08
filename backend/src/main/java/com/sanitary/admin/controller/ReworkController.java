package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Rework;
import com.sanitary.admin.service.ReworkService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/reworks")
@RequiredArgsConstructor
public class ReworkController {

    private final ReworkService reworkService;

    @GetMapping
    public Result<Page<Rework>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String reworkStatus) {
        return Result.success(reworkService.pageList(page, size, keyword, customerId, reworkStatus));
    }

    @GetMapping("/{id}")
    public Result<Rework> getById(@PathVariable Long id) {
        return Result.success(reworkService.getById(id));
    }

    @PostMapping
    public Result<Rework> create(@RequestBody @Valid Rework rework) {
        return Result.success(reworkService.createRework(rework));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Rework rework) {
        rework.setId(id);
        reworkService.updateRework(rework);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        reworkService.deleteRework(id);
        return Result.success();
    }
}
