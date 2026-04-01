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
    public Page<MaterialProcessPrice> pageList(int page, int size, Long customerId, Long materialId, Long processId) {
        LambdaQueryWrapper<MaterialProcessPrice> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) wrapper.eq(MaterialProcessPrice::getCustomerId, customerId);
        if (materialId != null) wrapper.eq(MaterialProcessPrice::getMaterialId, materialId);
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

    @Override
    public void upsertPrice(Long customerId, String customerName, Long materialId, String materialName,
                            String materialCode, String spec, Long processId, String processName, BigDecimal unitPrice) {
        if (customerId == null || materialId == null || processId == null) return;
        if (unitPrice == null || unitPrice.compareTo(java.math.BigDecimal.ZERO) <= 0) return;
        MaterialProcessPrice record = getOne(new LambdaQueryWrapper<MaterialProcessPrice>()
                .eq(MaterialProcessPrice::getCustomerId, customerId)
                .eq(MaterialProcessPrice::getMaterialId, materialId)
                .eq(MaterialProcessPrice::getProcessId, processId)
                .last("LIMIT 1"));
        if (record == null) {
            record = new MaterialProcessPrice();
            record.setCustomerId(customerId);
            record.setCustomerName(customerName);
            record.setMaterialId(materialId);
            record.setMaterialName(materialName);
            record.setMaterialCode(materialCode);
            record.setSpec(spec);
            record.setProcessId(processId);
            record.setProcessName(processName);
            record.setUnitPrice(unitPrice);
            save(record);
        } else {
            record.setUnitPrice(unitPrice);
            updateById(record);
        }
    }
}
