package com.sanitary.admin.service;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Inventory;
import com.sanitary.admin.entity.InventoryLog;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface InventoryService extends IService<Inventory> {

    /**
     * 更新库存
     */
    void updateInventory(Long materialId, Long customerId, Long processId, String materialCode, String materialName,
                         String customerName, String spec, String processName,
                         BigDecimal changeQty, int changeType, String orderType,
                         Long orderId, String orderNo, LocalDate orderDate);

    /**
     * 库存分页查询
     */
    IPage<Inventory> pageList(int page, int size, Long customerId, String keyword);

    /**
     * 库存日志分页查询
     */
    IPage<InventoryLog> logPageList(int page, int size, Long materialId, Long customerId, Integer changeType,
                                    String startDate, String endDate);

    /**
     * 从对账单初始化库存
     */
    Map<String, Object> initFromStatement(MultipartFile file);
}