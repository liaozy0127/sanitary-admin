package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Shipment;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

public interface ShipmentService extends IService<Shipment> {
    Page<Shipment> pageList(int page, int size, String keyword, Long customerId,
                            String startDate, String endDate);
    Shipment createShipment(Shipment shipment);
    Shipment updateShipment(Shipment shipment);
    boolean deleteShipment(Long id);
    Map<String, Object> importExcel(MultipartFile file, String mode);
    void exportExcel(HttpServletResponse response, String keyword, Long customerId, String startDate, String endDate);
}
