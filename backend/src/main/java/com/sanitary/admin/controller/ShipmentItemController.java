package com.sanitary.admin.controller;

import com.sanitary.admin.entity.ShipmentItem;
import com.sanitary.admin.service.ShipmentItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipment-items")
@RequiredArgsConstructor
public class ShipmentItemController {

    private final ShipmentItemService shipmentItemService;

    @GetMapping
    public List<ShipmentItem> getShipmentItems(@RequestParam Long shipmentId) {
        return shipmentItemService.listByShipmentId(shipmentId);
    }
}