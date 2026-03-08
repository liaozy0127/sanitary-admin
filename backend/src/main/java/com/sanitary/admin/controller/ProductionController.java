package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Production;
import com.sanitary.admin.service.ProductionItemService;
import com.sanitary.admin.service.ProductionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/productions")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionService productionService;
    private final ProductionItemService productionItemService;

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
        Production production = productionService.getById(id);
        if (production != null) {
            production.setItems(productionItemService.listByProductionId(id));
        }
        return Result.success(production);
    }

    @PostMapping
    public Result<Production> create(@RequestBody @Valid Production production) {
        return Result.success(productionService.createProduction(production));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Production production) {
        production.setId(id);
        productionService.updateById(production);
        if (production.getItems() != null) {
            productionItemService.deleteByProductionId(id);
            productionItemService.saveItems(id, production.getProductionNo(), production.getItems());
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productionService.removeById(id);
        return Result.success();
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "normal") String mode) {
        return Result.success(productionService.importFromExcel(file, mode));
    }
}
