package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Shipment;
import com.sanitary.admin.service.ShipmentItemService;
import com.sanitary.admin.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final ShipmentItemService shipmentItemService;

    @GetMapping
    public Result<Page<Shipment>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(shipmentService.pageList(page, size, keyword, customerId, startDate, endDate));
    }

    @GetMapping("/{id}")
    public Result<Shipment> getById(@PathVariable Long id) {
        Shipment shipment = shipmentService.getById(id);
        if (shipment != null) {
            shipment.setItems(shipmentItemService.listByShipmentId(id));
        }
        return Result.success(shipment);
    }

    @PostMapping
    public Result<Shipment> create(@RequestBody @Valid Shipment shipment) {
        return Result.success(shipmentService.createShipment(shipment));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Shipment shipment) {
        shipment.setId(id);
        // 若请求未传 shipmentNo，从数据库补充（避免明细插入时 NOT NULL 约束报错）
        if (shipment.getShipmentNo() == null) {
            Shipment existing = shipmentService.getById(id);
            if (existing != null) {
                shipment.setShipmentNo(existing.getShipmentNo());
            }
        }
        shipmentService.updateById(shipment);
        if (shipment.getItems() != null) {
            shipmentItemService.deleteByShipmentId(id);
            shipmentItemService.saveItems(id, shipment.getShipmentNo(), shipment.getItems());
        }
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Shipment shipment = new Shipment();
        shipment.setId(id);
        shipment.setStatus(0);
        shipmentService.updateById(shipment);
        return Result.success();
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mode", defaultValue = "history") String mode) {
        return Result.success(shipmentService.importExcel(file, mode));
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) String keyword,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        shipmentService.exportExcel(response, keyword, customerId, startDate, endDate);
    }
}
