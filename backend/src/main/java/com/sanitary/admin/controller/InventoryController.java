package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Inventory;
import com.sanitary.admin.entity.InventoryLog;
import com.sanitary.admin.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    /** 按物料+客户+工艺精确查询当前库存，供发货单录入时参考 */
    @GetMapping("/query")
    public Result<Map<String, Object>> query(
            @RequestParam Long materialId,
            @RequestParam Long customerId,
            @RequestParam(required = false) Long processId) {
        Long effectiveProcessId = (processId != null) ? processId : 0L;
        Inventory inv = inventoryService.getOne(
            new LambdaQueryWrapper<Inventory>()
                .eq(Inventory::getMaterialId, materialId)
                .eq(Inventory::getCustomerId, customerId)
                .eq(Inventory::getProcessId, effectiveProcessId), false);
        Map<String, Object> result = new HashMap<>();
        result.put("quantity", inv != null ? inv.getQuantity() : BigDecimal.ZERO);
        result.put("reworkQty", inv != null ? inv.getReworkQty() : BigDecimal.ZERO);
        return Result.success(result);
    }

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

    @PostMapping("/init-from-statement")
    public Result<Map<String, Object>> initFromStatement(@RequestParam("file") MultipartFile file) {
        return Result.success(inventoryService.initFromStatement(file));
    }

    @PostMapping("/rebuild")
    public Result<Map<String, Object>> rebuild() {
        return Result.success(inventoryService.rebuildFromOrders());
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long customerId) {
        inventoryService.exportExcel(response, keyword, customerId);
    }
}
