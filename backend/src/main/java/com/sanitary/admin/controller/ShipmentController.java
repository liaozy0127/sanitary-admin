package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Shipment;
import com.sanitary.admin.service.ShipmentItemService;
import com.sanitary.admin.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

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
}
