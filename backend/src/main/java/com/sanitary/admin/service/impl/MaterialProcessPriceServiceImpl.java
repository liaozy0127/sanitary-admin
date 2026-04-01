package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.MaterialProcessPrice;
import com.sanitary.admin.mapper.MaterialProcessPriceMapper;
import com.sanitary.admin.service.MaterialProcessPriceService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class MaterialProcessPriceServiceImpl extends ServiceImpl<MaterialProcessPriceMapper, MaterialProcessPrice>
        implements MaterialProcessPriceService {

    @Override
    public Page<MaterialProcessPrice> pageList(int page, int size, Long customerId, String materialKeyword, Long processId) {
        LambdaQueryWrapper<MaterialProcessPrice> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) wrapper.eq(MaterialProcessPrice::getCustomerId, customerId);
        if (materialKeyword != null && !materialKeyword.trim().isEmpty()) {
            wrapper.and(w -> w.like(MaterialProcessPrice::getMaterialName, materialKeyword.trim())
                              .or().like(MaterialProcessPrice::getMaterialCode, materialKeyword.trim()));
        }
        if (processId != null) wrapper.eq(MaterialProcessPrice::getProcessId, processId);
        wrapper.orderByDesc(MaterialProcessPrice::getUpdateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public BigDecimal getPrice(Long customerId, Long materialId, Long processId) {
        if (customerId == null || materialId == null || processId == null) return null;
        MaterialProcessPrice record = getOne(new LambdaQueryWrapper<MaterialProcessPrice>()
                .eq(MaterialProcessPrice::getCustomerId, customerId)
                .eq(MaterialProcessPrice::getMaterialId, materialId)
                .eq(MaterialProcessPrice::getProcessId, processId)
                .last("LIMIT 1"));
        return record != null ? record.getUnitPrice() : null;
    }
}
