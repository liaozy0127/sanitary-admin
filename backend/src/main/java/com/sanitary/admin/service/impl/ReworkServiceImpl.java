package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Rework;
import com.sanitary.admin.entity.ReworkItem;
import com.sanitary.admin.mapper.ReworkMapper;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ReworkItemService;
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
    private final ReworkItemService reworkItemService;

    @Override
    public Page<Rework> pageList(int page, int size, String keyword, Long customerId, String reworkStatus) {
        LambdaQueryWrapper<Rework> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Rework::getReworkNo, keyword)
                    .or().like(Rework::getCustomerName, keyword));
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
    public Rework getById(Long id) {
        Rework rework = super.getById(id);
        if (rework != null) {
            rework.setItems(reworkItemService.getByReworkId(id));
        }
        return rework;
    }

    @Override
    @Transactional
    public Rework createRework(Rework rework) {
        rework.setReworkNo(generateNoUtil.generate("FG", "rework", "rework_no"));
        if (rework.getReworkStatus() == null) {
            rework.setReworkStatus("待返工");
        }
        save(rework);

        // 保存明细项
        if (rework.getItems() != null && !rework.getItems().isEmpty()) {
            reworkItemService.saveItems(rework.getId(), rework.getReworkNo(), rework.getItems());
        }

        return rework;
    }

    @Override
    @Transactional
    public Rework updateRework(Rework rework) {
        // 先删除原有的明细项
        reworkItemService.deleteByReworkId(rework.getId());

        updateById(rework);

        // 重新保存明细项
        if (rework.getItems() != null && !rework.getItems().isEmpty()) {
            reworkItemService.saveItems(rework.getId(), rework.getReworkNo(), rework.getItems());
        }

        return rework;
    }

    @Override
    @Transactional
    public boolean deleteRework(Long id) {
        // 先删除明细项
        reworkItemService.deleteByReworkId(id);
        // 再删除主单
        return removeById(id);
    }
}
