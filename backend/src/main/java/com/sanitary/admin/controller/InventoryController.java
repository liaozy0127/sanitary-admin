package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Inventory;
import com.sanitary.admin.entity.InventoryLog;
import com.sanitary.admin.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping
    public Result<IPage<Inventory>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String keyword) {

        IPage<Inventory> inventoryPage = inventoryService.pageList(page, size, customerId, keyword);

        return Result.success(inventoryPage);
    }

    @GetMapping("/log")
    public Result<IPage<InventoryLog>> logList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer changeType,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        IPage<InventoryLog> logPage = inventoryService.logPageList(page, size, materialId, customerId, changeType, startDate, endDate);

        return Result.success(logPage);
    }
}
