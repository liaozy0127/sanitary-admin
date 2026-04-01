package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.MaterialProcessPrice;

import java.math.BigDecimal;

public interface MaterialProcessPriceService extends IService<MaterialProcessPrice> {
    Page<MaterialProcessPrice> pageList(int page, int size, Long customerId, Long materialId, Long processId);
    BigDecimal getPrice(Long customerId, Long materialId, Long processId);
    /** 按客户+物料+工艺 upsert 单价（若记录不存在则新增，存在则更新单价） */
    void upsertPrice(Long customerId, String customerName, Long materialId, String materialName,
                     String materialCode, String spec, Long processId, String processName, BigDecimal unitPrice);
}
