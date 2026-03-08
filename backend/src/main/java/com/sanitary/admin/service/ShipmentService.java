package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Shipment;

public interface ShipmentService extends IService<Shipment> {
    Page<Shipment> pageList(int page, int size, String keyword, Long customerId,
                            String startDate, String endDate);
    Shipment createShipment(Shipment shipment);
    Shipment updateShipment(Shipment shipment);
    boolean deleteShipment(Long id);
}
