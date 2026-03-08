package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.ShipmentItem;
import com.sanitary.admin.mapper.ShipmentItemMapper;
import com.sanitary.admin.service.ShipmentItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ShipmentItemServiceImpl extends ServiceImpl<ShipmentItemMapper, ShipmentItem> implements ShipmentItemService {

    @Override
    public List<ShipmentItem> listByShipmentId(Long shipmentId) {
        return list(new LambdaQueryWrapper<ShipmentItem>()
                .eq(ShipmentItem::getShipmentId, shipmentId)
                .orderByAsc(ShipmentItem::getId));
    }

    @Override
    @Transactional
    public void saveItems(Long shipmentId, String shipmentNo, List<ShipmentItem> items) {
        if (items == null || items.isEmpty()) return;
        for (ShipmentItem item : items) {
            item.setShipmentId(shipmentId);
            item.setShipmentNo(shipmentNo);
            if (item.getQuantity() == null) item.setQuantity(BigDecimal.ZERO);
            if (item.getUnitPrice() != null && item.getQuantity() != null) {
                item.setAmount(item.getQuantity().multiply(item.getUnitPrice()));
            }
        }
        saveBatch(items);
    }

    @Override
    @Transactional
    public void deleteByShipmentId(Long shipmentId) {
        remove(new LambdaQueryWrapper<ShipmentItem>().eq(ShipmentItem::getShipmentId, shipmentId));
    }
}