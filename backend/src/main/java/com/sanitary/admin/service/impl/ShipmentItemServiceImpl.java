package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.ShipmentItem;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ShipmentItemMapper;
import com.sanitary.admin.service.ShipmentItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ShipmentItemServiceImpl extends ServiceImpl<ShipmentItemMapper, ShipmentItem> implements ShipmentItemService {

    private final MaterialMapper materialMapper;

    @Override
    public List<ShipmentItem> listByShipmentId(Long shipmentId) {
        List<ShipmentItem> items = list(new LambdaQueryWrapper<ShipmentItem>()
                .eq(ShipmentItem::getShipmentId, shipmentId)
                .orderByAsc(ShipmentItem::getId));
        fillLatestSpec(items);
        return items;
    }

    @Override
    public List<ShipmentItem> listByShipmentIds(List<Long> shipmentIds) {
        if (shipmentIds == null || shipmentIds.isEmpty()) return new java.util.ArrayList<>();
        List<ShipmentItem> items = this.list(new LambdaQueryWrapper<ShipmentItem>()
            .in(ShipmentItem::getShipmentId, shipmentIds)
            .orderByAsc(ShipmentItem::getId));
        fillLatestSpec(items);
        return items;
    }

    /** 批量回填物料最新规格，避免冗余字段与物料档案不同步。 */
    private void fillLatestSpec(List<ShipmentItem> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> materialIds = items.stream()
            .map(ShipmentItem::getMaterialId)
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());
        if (materialIds.isEmpty()) return;
        Map<Long, String> specMap = materialMapper.selectBatchIds(materialIds).stream()
            .collect(Collectors.toMap(Material::getId, m -> m.getSpec() != null ? m.getSpec() : ""));
        for (ShipmentItem item : items) {
            if (item.getMaterialId() != null && specMap.containsKey(item.getMaterialId())) {
                item.setSpec(specMap.get(item.getMaterialId()));
            }
        }
    }

    @Override
    @Transactional
    public void saveItems(Long shipmentId, String shipmentNo, List<ShipmentItem> items) {
        if (items == null || items.isEmpty()) return;
        for (ShipmentItem item : items) {
            item.setId(null);  // 清空旧 id，防止与软删除记录主键冲突
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

    @Override
    public ShipmentItem getLatestPrice(Long customerId, Long materialId, Long processId) {
        if (customerId == null || materialId == null || processId == null) return null;
        return getBaseMapper().selectOne(
            new LambdaQueryWrapper<ShipmentItem>()
                .eq(ShipmentItem::getMaterialId, materialId)
                .eq(ShipmentItem::getProcessId, processId)
                .isNotNull(ShipmentItem::getUnitPrice)
                .inSql(ShipmentItem::getShipmentId,
                    "SELECT id FROM shipment WHERE customer_id = " + customerId + " AND deleted = 0")
                .orderByDesc(ShipmentItem::getId)
                .last("LIMIT 1")
        );
    }
}