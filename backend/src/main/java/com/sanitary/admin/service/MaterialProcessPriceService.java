package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.MaterialProcessPrice;

import java.math.BigDecimal;

public interface MaterialProcessPriceService extends IService<MaterialProcessPrice> {
    Page<MaterialProcessPrice> pageList(int page, int size, Long customerId, String materialKeyword, Long processId);
    BigDecimal getPrice(Long customerId, Long materialId, Long processId);
}
