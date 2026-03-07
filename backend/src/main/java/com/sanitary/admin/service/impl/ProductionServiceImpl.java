package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Production;
import com.sanitary.admin.mapper.ProductionMapper;
import com.sanitary.admin.service.ProductionService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ProductionServiceImpl extends ServiceImpl<ProductionMapper, Production> implements ProductionService {

    private final GenerateNoUtil generateNoUtil;

    @Override
    public Page<Production> pageList(int page, int size, String keyword, Long customerId, String prodStatus) {
        LambdaQueryWrapper<Production> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Production::getProductionNo, keyword)
                    .or().like(Production::getCustomerName, keyword)
                    .or().like(Production::getMaterialName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Production::getCustomerId, customerId);
        }
        if (StringUtils.hasText(prodStatus)) {
            wrapper.eq(Production::getProdStatus, prodStatus);
        }
        wrapper.orderByDesc(Production::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Production createProduction(Production production) {
        production.setProductionNo(generateNoUtil.generate("PC", "production", "production_no"));
        if (production.getProdStatus() == null) {
            production.setProdStatus("待生产");
        }
        if (production.getActualQty() == null) {
            production.setActualQty(BigDecimal.ZERO);
        }
        if (production.getPlannedQty() != null && production.getUnitPrice() != null) {
            production.setAmount(production.getPlannedQty().multiply(production.getUnitPrice()));
        }
        save(production);
        return production;
    }
}
