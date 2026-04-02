package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.ProductionItem;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ProductionItemMapper;
import com.sanitary.admin.service.ProductionItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductionItemServiceImpl extends ServiceImpl<ProductionItemMapper, ProductionItem> implements ProductionItemService {

    private final MaterialMapper materialMapper;

    @Override
    public List<ProductionItem> listByProductionId(Long productionId) {
        List<ProductionItem> items = list(new LambdaQueryWrapper<ProductionItem>()
                .eq(ProductionItem::getProductionId, productionId)
                .orderByAsc(ProductionItem::getId));
        fillLatestSpec(items);
        return items;
    }

    @Override
    public List<ProductionItem> listByProductionIds(List<Long> productionIds) {
        if (productionIds == null || productionIds.isEmpty()) return new java.util.ArrayList<>();
        List<ProductionItem> items = this.list(new LambdaQueryWrapper<ProductionItem>()
            .in(ProductionItem::getProductionId, productionIds)
            .orderByAsc(ProductionItem::getId));
        fillLatestSpec(items);
        return items;
    }

    /** 批量回填物料最新规格，避免冗余字段与物料档案不同步。 */
    private void fillLatestSpec(List<ProductionItem> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> materialIds = items.stream()
            .map(ProductionItem::getMaterialId)
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());
        if (materialIds.isEmpty()) return;
        Map<Long, String> specMap = materialMapper.selectBatchIds(materialIds).stream()
            .collect(Collectors.toMap(Material::getId, m -> m.getSpec() != null ? m.getSpec() : ""));
        for (ProductionItem item : items) {
            if (item.getMaterialId() != null && specMap.containsKey(item.getMaterialId())) {
                item.setSpec(specMap.get(item.getMaterialId()));
            }
        }
    }

    @Override
    @Transactional
    public void saveItems(Long productionId, String productionNo, List<ProductionItem> items) {
        if (items == null || items.isEmpty()) return;
        for (ProductionItem item : items) {
            item.setId(null);  // 清空旧 id，防止与软删除记录主键冲突
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