package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.MaterialProcessPrice;
import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.entity.ShipmentItem;
import com.sanitary.admin.service.MaterialProcessPriceService;
import com.sanitary.admin.service.ReceiptItemService;
import com.sanitary.admin.service.ShipmentItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/material-process-prices")
@RequiredArgsConstructor
public class MaterialProcessPriceController {

    private final MaterialProcessPriceService materialProcessPriceService;
    private final ReceiptItemService receiptItemService;
    private final ShipmentItemService shipmentItemService;

    @GetMapping
    public Result<Page<MaterialProcessPrice>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long materialId,
            @RequestParam(required = false) Long processId) {
        return Result.success(materialProcessPriceService.pageList(page, size, customerId, materialId, processId));
    }

    @GetMapping("/query")
    public Result<Map<String, Object>> query(
            @RequestParam Long customerId,
            @RequestParam Long materialId,
            @RequestParam Long processId) {
        Map<String, Object> result = new HashMap<>();

        // 1. 先查价格表
        BigDecimal priceTablePrice = materialProcessPriceService.getPrice(customerId, materialId, processId);
        if (priceTablePrice != null) {
            result.put("unitPrice", priceTablePrice);
            result.put("source", "price_table");
            return Result.success(result);
        }

        // 2. 查发货单历史
        ShipmentItem latestShipment = shipmentItemService.getLatestPrice(customerId, materialId, processId);
        // 3. 查收货单历史
        ReceiptItem latestReceipt = receiptItemService.getLatestPrice(customerId, materialId, processId);

        // 取时间较新的一条
        if (latestShipment != null && latestReceipt != null) {
            LocalDateTime shipTime = latestShipment.getUpdateTime() != null ? latestShipment.getUpdateTime() : latestShipment.getCreateTime();
            LocalDateTime recTime = latestReceipt.getUpdateTime() != null ? latestReceipt.getUpdateTime() : latestReceipt.getCreateTime();
            if (shipTime != null && recTime != null && shipTime.isAfter(recTime)) {
                result.put("unitPrice", latestShipment.getUnitPrice());
                result.put("source", "shipment_history");
            } else {
                result.put("unitPrice", latestReceipt.getUnitPrice());
                result.put("source", "receipt_history");
            }
        } else if (latestShipment != null) {
            result.put("unitPrice", latestShipment.getUnitPrice());
            result.put("source", "shipment_history");
        } else if (latestReceipt != null) {
            result.put("unitPrice", latestReceipt.getUnitPrice());
            result.put("source", "receipt_history");
        } else {
            result.put("unitPrice", null);
            result.put("source", null);
        }

        return Result.success(result);
    }

    @PostMapping
    public Result<Void> create(@RequestBody MaterialProcessPrice price) {
        materialProcessPriceService.saveOrUpdate(price);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MaterialProcessPrice price) {
        price.setId(id);
        materialProcessPriceService.updateById(price);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        materialProcessPriceService.removeById(id);
        return Result.success();
    }
}
