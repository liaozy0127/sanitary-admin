package com.sanitary.admin.controller;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.SysDept;
import com.sanitary.admin.service.SysDeptService;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/depts")
@RequiredArgsConstructor
public class DeptController {
    private final SysDeptService sysDeptService;

    @GetMapping
    public Result<List<SysDept>> list(@RequestParam(required = false) String deptName) {
        return Result.success(sysDeptService.listDepts(deptName));
    }

    @GetMapping("/{id}")
    public Result<SysDept> getById(@PathVariable Long id) {
        SysDept dept = sysDeptService.getById(id);
        if (dept == null) {
            return Result.error(404, "部门不存在");
        }
        return Result.success(dept);
    }

    @PostMapping
    public Result<Void> add(@Validated @RequestBody SysDept dept) {
        sysDeptService.save(dept);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Validated @RequestBody SysDept dept) {
        dept.setId(id);
        sysDeptService.updateById(dept);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sysDeptService.removeDeptById(id);
        } catch (RuntimeException e) {
            return Result.error(400, e.getMessage());
        }
        return Result.success();
    }
}
