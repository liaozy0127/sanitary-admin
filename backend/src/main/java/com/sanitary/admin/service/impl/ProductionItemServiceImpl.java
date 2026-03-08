package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.ProductionItem;
import com.sanitary.admin.mapper.ProductionItemMapper;
import com.sanitary.admin.service.ProductionItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ProductionItemServiceImpl extends ServiceImpl<ProductionItemMapper, ProductionItem> implements ProductionItemService {

    @Override
    public List<ProductionItem> listByProductionId(Long productionId) {
        return list(new LambdaQueryWrapper<ProductionItem>()
                .eq(ProductionItem::getProductionId, productionId)
                .orderByAsc(ProductionItem::getId));
    }

    @Override
    @Transactional
    public void saveItems(Long productionId, String productionNo, List<ProductionItem> items) {
        if (items == null || items.isEmpty()) return;
        for (ProductionItem item : items) {
            item.setProductionId(productionId);
            item.setProductionNo(productionNo);
            if (item.getPlannedQty() == null) item.setPlannedQty(BigDecimal.ZERO);
            if (item.getActualQty() == null) item.setActualQty(BigDecimal.ZERO);
            if (item.getOutsourcePrice() == null) item.setOutsourcePrice(BigDecimal.ZERO);
            if (item.getPlatingPrice() == null) item.setPlatingPrice(BigDecimal.ZERO);
            if (item.getPlatingAmount() == null) item.setPlatingAmount(BigDecimal.ZERO);
        }
        saveBatch(items);
    }

    @Override
    @Transactional
    public void deleteByProductionId(Long productionId) {
        remove(new LambdaQueryWrapper<ProductionItem>().eq(ProductionItem::getProductionId, productionId));
    }
}