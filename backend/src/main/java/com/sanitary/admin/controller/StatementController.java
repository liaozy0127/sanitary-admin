package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Statement;
import com.sanitary.admin.service.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/statements")
@RequiredArgsConstructor
public class StatementController {

    private final StatementService statementService;

    @GetMapping
    public Result<Page<Statement>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String statementMonth) {
        return Result.success(statementService.pageList(page, size, customerId, statementMonth));
    }

    @GetMapping("/{id}")
    public Result<Statement> getById(@PathVariable Long id) {
        return Result.success(statementService.getById(id));
    }

    @PostMapping("/generate")
    public Result<Statement> generate(@RequestBody Map<String, Object> params) {
        Long customerId = Long.valueOf(params.get("customerId").toString());
        String statementMonth = params.get("statementMonth").toString();
        return Result.success(statementService.generate(customerId, statementMonth));
    }

    @PutMapping("/{id}/confirm")
    public Result<Void> confirm(@PathVariable Long id) {
        statementService.confirm(id);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        statementService.removeById(id);
        return Result.success();
    }
}
