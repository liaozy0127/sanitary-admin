package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Statement;
import com.sanitary.admin.service.StatementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
        return Result.success(statementService.getByIdWithItems(id));
    }

    @PostMapping("/generate")
    public Result<Statement> generate(@RequestBody Map<String, Object> params) {
        Long customerId = Long.valueOf(params.get("customerId").toString());
        String statementMonth = params.get("statementMonth").toString();
        return Result.success(statementService.generate(customerId, statementMonth));
    }

    /** 批量初始化：根据所有收发货数据生成对账单 */
    @PostMapping("/generate-all")
    public Result<Map<String, Object>> generateAll() {
        return Result.success(statementService.generateAll());
    }

    @PostMapping("/import")
    public Result<java.util.Map<String, Object>> importExcel(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam Long customerId,
            @RequestParam String statementMonth,
            @RequestParam(defaultValue = "false") Boolean initInventory) {
        return Result.success(statementService.importExcel(file, customerId, statementMonth, initInventory));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        statementService.removeById(id);
        return Result.success();
    }
}
