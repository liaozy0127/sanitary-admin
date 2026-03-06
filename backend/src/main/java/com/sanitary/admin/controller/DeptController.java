package com.sanitary.admin.controller;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.SysDept;
import com.sanitary.admin.service.SysDeptService;
import lombok.RequiredArgsConstructor;
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
        return Result.success(sysDeptService.getById(id));
    }
    @PostMapping
    public Result<Void> add(@RequestBody SysDept dept) {
        sysDeptService.save(dept);
        return Result.success();
    }
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysDept dept) {
        dept.setId(id);
        sysDeptService.updateById(dept);
        return Result.success();
    }
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        sysDeptService.removeById(id);
        return Result.success();
    }
}
