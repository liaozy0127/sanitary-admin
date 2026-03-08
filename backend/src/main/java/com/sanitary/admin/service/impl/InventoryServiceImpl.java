package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Inventory;
import com.sanitary.admin.entity.InventoryLog;
import com.sanitary.admin.mapper.InventoryLogMapper;
import com.sanitary.admin.mapper.InventoryMapper;
import com.sanitary.admin.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl extends ServiceImpl<InventoryMapper, Inventory> implements InventoryService {

    private final InventoryLogMapper inventoryLogMapper;

    @Override
    @Transactional
    public void updateInventory(Long materialId, Long customerId, Long processId, String materialCode, String materialName,
                                String customerName, String spec, String processName,
                                BigDecimal changeQty, int changeType, String orderType,
                                Long orderId, String orderNo, LocalDate orderDate) {
        // 查询现有库存记录
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Inventory::getMaterialId, materialId)
                .eq(Inventory::getCustomerId, customerId);

        if (processId != null) {
            queryWrapper.eq(Inventory::getProcessId, processId);
        } else {
            queryWrapper.isNull(Inventory::getProcessId);
        }

        Inventory inventory = this.getOne(queryWrapper, false);

        BigDecimal beforeQty = BigDecimal.ZERO;
        BigDecimal afterQty;

        if (inventory == null) {
            // 创建新的库存记录
            inventory = new Inventory();
            inventory.setMaterialId(materialId);
            inventory.setCustomerId(customerId);
            inventory.setProcessId(processId);
            inventory.setMaterialCode(materialCode);
            inventory.setMaterialName(materialName);
            inventory.setCustomerName(customerName);
            inventory.setSpec(spec);
            inventory.setProcessName(processName);
            inventory.setQuantity(changeQty);

            // 设置时间
            if (changeType == 1) { // 收货
                inventory.setLastReceiveTime(LocalDateTime.now());
            } else if (changeType == 2) { // 发货
                inventory.setLastShipTime(LocalDateTime.now());
            }

            this.save(inventory);
            beforeQty = BigDecimal.ZERO;
            afterQty = changeQty;
        } else {
            // 更新现有库存记录
            beforeQty = inventory.getQuantity() != null ? inventory.getQuantity() : BigDecimal.ZERO;
            afterQty = beforeQty.add(changeQty);
            inventory.setQuantity(afterQty);

            // 设置时间
            if (changeType == 1) { // 收货
                inventory.setLastReceiveTime(LocalDateTime.now());
            } else if (changeType == 2) { // 发货
                inventory.setLastShipTime(LocalDateTime.now());
            }

            this.updateById(inventory);
        }

        // 记录库存变动日志
        insertLog(materialId, customerId, processId, materialCode, materialName, customerName, spec, processName,
                changeType, changeQty, beforeQty, afterQty, orderType, orderId, orderNo, orderDate, null);
    }

    /**
     * 插入库存变动日志
     */
    private void insertLog(Long materialId, Long customerId, Long processId, String materialCode, String materialName,
                           String customerName, String spec, String processName, int changeType, BigDecimal changeQty,
                           BigDecimal beforeQty, BigDecimal afterQty, String orderType, Long orderId, String orderNo,
                           LocalDate orderDate, String remark) {
        InventoryLog log = new InventoryLog();
        log.setMaterialId(materialId);
        log.setCustomerId(customerId);
        log.setProcessId(processId);
        log.setMaterialCode(materialCode);
        log.setMaterialName(materialName);
        log.setCustomerName(customerName);
        log.setSpec(spec);
        log.setProcessName(processName);
        log.setChangeType(changeType);
        log.setChangeQty(changeQty);
        log.setBeforeQty(beforeQty);
        log.setAfterQty(afterQty);
        log.setOrderType(orderType);
        log.setOrderId(orderId);
        log.setOrderNo(orderNo);
        log.setOrderDate(orderDate);
        log.setRemark(remark);

        inventoryLogMapper.insert(log);
    }

    @Override
    public IPage<Inventory> pageList(int page, int size, Long customerId, String keyword) {
        LambdaQueryWrapper<Inventory> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.gt(Inventory::getQuantity, BigDecimal.ZERO); // 只显示有库存的

        if (customerId != null) {
            queryWrapper.eq(Inventory::getCustomerId, customerId);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper.like(Inventory::getMaterialCode, keyword)
                    .or()
                    .like(Inventory::getMaterialName, keyword)
                    .or()
                    .like(Inventory::getCustomerName, keyword));
        }

        queryWrapper.orderByDesc(Inventory::getCreateTime);

        return this.page(new Page<>(page, size), queryWrapper);
    }

    @Override
    public IPage<InventoryLog> logPageList(int page, int size, Long materialId, Long customerId, Integer changeType,
                                           String startDate, String endDate) {
        LambdaQueryWrapper<InventoryLog> queryWrapper = new LambdaQueryWrapper<>();

        if (materialId != null) {
            queryWrapper.eq(InventoryLog::getMaterialId, materialId);
        }

        if (customerId != null) {
            queryWrapper.eq(InventoryLog::getCustomerId, customerId);
        }

        if (changeType != null) {
            queryWrapper.eq(InventoryLog::getChangeType, changeType);
        }

        if (startDate != null && !startDate.isEmpty()) {
            queryWrapper.ge(InventoryLog::getCreateTime, startDate + " 00:00:00");
        }

        if (endDate != null && !endDate.isEmpty()) {
            queryWrapper.le(InventoryLog::getCreateTime, endDate + " 23:59:59");
        }

        queryWrapper.orderByDesc(InventoryLog::getCreateTime);

        return inventoryLogMapper.selectPage(new Page<>(page, size), queryWrapper);
    }
}