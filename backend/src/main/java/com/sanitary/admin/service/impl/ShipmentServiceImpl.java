package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Shipment;
import com.sanitary.admin.entity.ShipmentItem;
import com.sanitary.admin.mapper.ShipmentMapper;
import com.sanitary.admin.service.InventoryService;
import com.sanitary.admin.service.ShipmentItemService;
import com.sanitary.admin.service.ShipmentService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipmentServiceImpl extends ServiceImpl<ShipmentMapper, Shipment> implements ShipmentService {

    private final GenerateNoUtil generateNoUtil;
    private final InventoryService inventoryService;
    private final ShipmentItemService shipmentItemService;

    @Override
    public Page<Shipment> pageList(int page, int size, String keyword, Long customerId,
                                   String startDate, String endDate) {
        LambdaQueryWrapper<Shipment> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Shipment::getShipmentNo, keyword)
                    .or().like(Shipment::getCustomerName, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Shipment::getCustomerId, customerId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(Shipment::getShipmentDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(Shipment::getShipmentDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(Shipment::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Shipment createShipment(Shipment shipment) {
        shipment.setShipmentNo(generateNoUtil.generate("FH", "shipment", "shipment_no"));
        if (shipment.getStatus() == null) {
            shipment.setStatus(1);
        }
        save(shipment);

        if (shipment.getItems() != null && !shipment.getItems().isEmpty()) {
            shipmentItemService.saveItems(shipment.getId(), shipment.getShipmentNo(), shipment.getItems());
            
            // 更新库存 - 发货出库
            for (ShipmentItem item : shipment.getItems()) {
                inventoryService.updateInventory(
                    item.getMaterialId(),
                    shipment.getCustomerId(),
                    item.getProcessId(),
                    item.getMaterialCode(),
                    item.getMaterialName(),
                    shipment.getCustomerName(),
                    item.getSpec(),
                    item.getProcessName(),
                    item.getQuantity(),
                    2,  // changeType: 2=发货(出库)
                    "shipment",  // orderType
                    shipment.getId(),
                    shipment.getShipmentNo(),
                    shipment.getShipmentDate()
                );
            }
        }

        return shipment;
    }

    @Override
    @Transactional
    public Shipment updateShipment(Shipment shipment) {
        // 先查询旧的明细，用于冲销库存
        List<ShipmentItem> oldItems = shipmentItemService.listByShipmentId(shipment.getId());
        
        // 先删除旧的明细
        shipmentItemService.deleteByShipmentId(shipment.getId());
        
        // 更新主表
        updateById(shipment);
        
        // 保存新的明细
        if (shipment.getItems() != null && !shipment.getItems().isEmpty()) {
            shipmentItemService.saveItems(shipment.getId(), shipment.getShipmentNo(), shipment.getItems());
        }
        
        // 冲销旧库存（反向操作）
        for (ShipmentItem oldItem : oldItems) {
            inventoryService.updateInventory(
                oldItem.getMaterialId(),
                shipment.getCustomerId(),
                oldItem.getProcessId(),
                oldItem.getMaterialCode(),
                oldItem.getMaterialName(),
                shipment.getCustomerName(),
                oldItem.getSpec(),
                oldItem.getProcessName(),
                oldItem.getQuantity().negate(), // 反向冲销，数量取负
                2,  // changeType: 2=发货(出库)
                "shipment",  // orderType
                shipment.getId(),
                shipment.getShipmentNo(),
                shipment.getShipmentDate()
            );
        }
        
        // 更新新库存
        if (shipment.getItems() != null && !shipment.getItems().isEmpty()) {
            for (ShipmentItem item : shipment.getItems()) {
                inventoryService.updateInventory(
                    item.getMaterialId(),
                    shipment.getCustomerId(),
                    item.getProcessId(),
                    item.getMaterialCode(),
                    item.getMaterialName(),
                    shipment.getCustomerName(),
                    item.getSpec(),
                    item.getProcessName(),
                    item.getQuantity(),
                    2,  // changeType: 2=发货(出库)
                    "shipment",  // orderType
                    shipment.getId(),
                    shipment.getShipmentNo(),
                    shipment.getShipmentDate()
                );
            }
        }
        
        return shipment;
    }

    @Override
    @Transactional
    public boolean deleteShipment(Long id) {
        // 查询明细，用于冲销库存
        List<ShipmentItem> items = shipmentItemService.listByShipmentId(id);
        // 获取发货单信息
        Shipment shipment = getById(id);
        
        // 先删除明细
        shipmentItemService.deleteByShipmentId(id);
        
        // 冲销库存（反向操作）
        for (ShipmentItem item : items) {
            inventoryService.updateInventory(
                item.getMaterialId(),
                shipment.getCustomerId(),
                item.getProcessId(),
                item.getMaterialCode(),
                item.getMaterialName(),
                shipment.getCustomerName(),
                item.getSpec(),
                item.getProcessName(),
                item.getQuantity().negate(), // 反向冲销，数量取负
                2,  // changeType: 2=发货(出库)
                "shipment",  // orderType
                shipment.getId(),
                shipment.getShipmentNo(),
                shipment.getShipmentDate()
            );
        }
        
        // 再删除主表记录
        return removeById(id);
    }
}
