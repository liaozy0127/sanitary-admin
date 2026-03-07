package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Production;
import com.sanitary.admin.service.ProductionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/productions")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionService productionService;

    @GetMapping
    public Result<Page<Production>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String prodStatus) {
        return Result.success(productionService.pageList(page, size, keyword, customerId, prodStatus));
    }

    @GetMapping("/{id}")
    public Result<Production> getById(@PathVariable Long id) {
        return Result.success(productionService.getById(id));
    }

    @PostMapping
    public Result<Production> create(@RequestBody @Valid Production production) {
        return Result.success(productionService.createProduction(production));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Production production) {
        production.setId(id);
        if (production.getPlannedQty() != null && production.getUnitPrice() != null) {
            production.setAmount(production.getPlannedQty().multiply(production.getUnitPrice()));
        }
        productionService.updateById(production);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productionService.removeById(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam String prodStatus) {
        Production production = new Production();
        production.setId(id);
        production.setProdStatus(prodStatus);
        productionService.updateById(production);
        return Result.success();
    }
}
