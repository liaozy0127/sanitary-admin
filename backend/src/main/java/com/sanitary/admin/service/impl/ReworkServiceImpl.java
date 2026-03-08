package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Rework;
import com.sanitary.admin.mapper.ReworkMapper;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ReworkService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class ReworkServiceImpl extends ServiceImpl<ReworkMapper, Rework> implements ReworkService {

    private final GenerateNoUtil generateNoUtil;
    private final InventoryService inventoryService;

    @Override
    public Page<Rework> pageList(int page, int size, String keyword, Long customerId, String reworkStatus) {
        LambdaQueryWrapper<Rework> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Rework::getReworkNo, keyword)
                    .or().like(Rework::getCustomerName, keyword)
                    .or().like(Rework::getMaterialName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Rework::getCustomerId, customerId);
        }
        if (StringUtils.hasText(reworkStatus)) {
            wrapper.eq(Rework::getReworkStatus, reworkStatus);
        }
        wrapper.orderByDesc(Rework::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Rework createRework(Rework rework) {
        rework.setReworkNo(generateNoUtil.generate("FG", "rework", "rework_no"));
        if (rework.getReworkStatus() == null) {
            rework.setReworkStatus("待处理");
        }
        if (rework.getUnitPrice() == null) {
            rework.setUnitPrice(BigDecimal.ZERO);
        }
        if (rework.getQuantity() != null && rework.getUnitPrice() != null) {
            rework.setAmount(rework.getQuantity().multiply(rework.getUnitPrice()));
        }
        save(rework);

        // Update inventory (for rework, typically increases inventory since it's defective items being reworked)
        inventoryService.updateInventory(
                rework.getMaterialId(),
                rework.getCustomerId(),
                rework.getProcessId(),
                rework.getMaterialCode(),
                rework.getMaterialName(),
                rework.getCustomerName(),
                rework.getSpec(),
                rework.getProcessName(),
                rework.getQuantity(), // Positive quantity for rework (may vary based on business logic)
                3,  // 返工
                "REWORK",
                rework.getId(),
                rework.getReworkNo(),
                LocalDate.now() // Assuming current date for rework orders
        );

        return rework;
    }
}
