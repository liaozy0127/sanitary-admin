package com.sanitary.admin.controller;

import com.sanitary.admin.entity.ProductionItem;
import com.sanitary.admin.service.ProductionItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/production-items")
@RequiredArgsConstructor
public class ProductionItemController {

    private final ProductionItemService productionItemService;

    @GetMapping
    public List<ProductionItem> getProductionItems(@RequestParam Long productionId) {
        return productionItemService.listByProductionId(productionId);
    }
}