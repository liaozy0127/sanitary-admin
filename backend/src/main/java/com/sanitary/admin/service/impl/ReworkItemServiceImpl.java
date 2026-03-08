package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.ReworkItem;
import com.sanitary.admin.mapper.ReworkItemMapper;
import com.sanitary.admin.service.ReworkItemService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ReworkItemServiceImpl extends ServiceImpl<ReworkItemMapper, ReworkItem> implements ReworkItemService {

    @Override
    public List<ReworkItem> getByReworkId(Long reworkId) {
        LambdaQueryWrapper<ReworkItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReworkItem::getReworkId, reworkId);
        return this.list(wrapper);
    }

    @Override
    public boolean saveItems(Long reworkId, String reworkNo, List<ReworkItem> items) {
        // 先删除原有明细
        this.deleteByReworkId(reworkId);

        // 批量插入新的明细
        for (ReworkItem item : items) {
            item.setReworkId(reworkId);
            item.setReworkNo(reworkNo);
            this.getBaseMapper().insert(item);
        }
        return true;
    }

    @Override
    public boolean deleteByReworkId(Long reworkId) {
        LambdaQueryWrapper<ReworkItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ReworkItem::getReworkId, reworkId);
        return this.remove(wrapper);
    }
}