package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.ProductionItem;

import java.util.List;

public interface ProductionItemService extends IService<ProductionItem> {
    List<ProductionItem> listByProductionId(Long productionId);
    void saveItems(Long productionId, String productionNo, List<ProductionItem> items);
    void deleteByProductionId(Long productionId);
}