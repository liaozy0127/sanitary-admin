package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.ShipmentItem;

import java.util.List;

public interface ShipmentItemService extends IService<ShipmentItem> {
    List<ShipmentItem> listByShipmentId(Long shipmentId);
    List<ShipmentItem> listByShipmentIds(List<Long> shipmentIds);
    void saveItems(Long shipmentId, String shipmentNo, List<ShipmentItem> items);
    void deleteByShipmentId(Long shipmentId);
}