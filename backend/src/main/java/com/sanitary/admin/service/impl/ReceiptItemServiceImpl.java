package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.ReceiptItemMapper;
import com.sanitary.admin.mapper.ReceiptMapper;
import com.sanitary.admin.service.ReceiptItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReceiptItemServiceImpl extends ServiceImpl<ReceiptItemMapper, ReceiptItem> implements ReceiptItemService {

    private final MaterialMapper materialMapper;
    private final ReceiptMapper receiptMapper;

    @Override
    public List<ReceiptItem> listByReceiptId(Long receiptId) {
        List<ReceiptItem> items = list(new LambdaQueryWrapper<ReceiptItem>()
                .eq(ReceiptItem::getReceiptId, receiptId)
                .orderByAsc(ReceiptItem::getId));
        fillLatestSpec(items);
        return items;
    }

    @Override
    public List<ReceiptItem> listByReceiptIds(List<Long> receiptIds) {
        if (receiptIds == null || receiptIds.isEmpty()) return new java.util.ArrayList<>();
        List<ReceiptItem> items = this.list(new LambdaQueryWrapper<ReceiptItem>()
            .in(ReceiptItem::getReceiptId, receiptIds)
            .orderByAsc(ReceiptItem::getId));
        fillLatestSpec(items);
        return items;
    }

    /** 批量回填物料最新规格，避免冗余字段与物料档案不同步。 */
    private void fillLatestSpec(List<ReceiptItem> items) {
        if (items == null || items.isEmpty()) return;
        List<Long> materialIds = items.stream()
            .map(ReceiptItem::getMaterialId)
            .filter(id -> id != null)
            .distinct()
            .collect(Collectors.toList());
        if (materialIds.isEmpty()) return;
        Map<Long, String> specMap = materialMapper.selectBatchIds(materialIds).stream()
            .collect(Collectors.toMap(Material::getId, m -> m.getSpec() != null ? m.getSpec() : ""));
        for (ReceiptItem item : items) {
            if (item.getMaterialId() != null && specMap.containsKey(item.getMaterialId())) {
                item.setSpec(specMap.get(item.getMaterialId()));
            }
        }
    }

    @Override
    @Transactional
    public void saveItems(Long receiptId, String receiptNo, List<ReceiptItem> items) {
        if (items == null || items.isEmpty()) return;
        for (ReceiptItem item : items) {
            item.setId(null);  // 清空旧 id，防止与软删除记录主键冲突
            item.setReceiptId(receiptId);
            item.setReceiptNo(receiptNo);
            if (item.getQuantity() == null) item.setQuantity(BigDecimal.ZERO);
            if (item.getUnitPrice() != null && item.getQuantity() != null) {
                item.setAmount(item.getQuantity().multiply(item.getUnitPrice()));
            }
        }
        saveBatch(items);
    }

    @Override
    @Transactional
    public void deleteByReceiptId(Long receiptId) {
        remove(new LambdaQueryWrapper<ReceiptItem>().eq(ReceiptItem::getReceiptId, receiptId));
    }

    @Override
    public ReceiptItem getLatestProcessByMaterial(Long customerId, Long materialId) {
        return getBaseMapper().selectOne(
            new LambdaQueryWrapper<ReceiptItem>()
                .eq(ReceiptItem::getMaterialId, materialId)
                .isNotNull(ReceiptItem::getProcessId)
                .ne(ReceiptItem::getProcessId, 0L)
                .inSql(ReceiptItem::getReceiptId,
                    "SELECT id FROM receipt WHERE customer_id = " + customerId + " AND deleted = 0")
                .orderByDesc(ReceiptItem::getId)
                .last("LIMIT 1")
        );
    }

    @Override
    public ReceiptItem getLatestPrice(Long customerId, Long materialId, Long processId) {
        if (customerId == null || materialId == null || processId == null) return null;
        return getBaseMapper().selectOne(
            new LambdaQueryWrapper<ReceiptItem>()
                .eq(ReceiptItem::getMaterialId, materialId)
                .eq(ReceiptItem::getProcessId, processId)
                .isNotNull(ReceiptItem::getUnitPrice)
                .inSql(ReceiptItem::getReceiptId,
                    "SELECT id FROM receipt WHERE customer_id = " + customerId + " AND deleted = 0")
                .orderByDesc(ReceiptItem::getId)
                .last("LIMIT 1")
        );
    }

    @Override
    public List<Map<String, Object>> listByCustomerId(Long customerId) {
        // 查询该客户所有收货明细
        List<ReceiptItem> items = getBaseMapper().selectList(
            new LambdaQueryWrapper<ReceiptItem>()
                .inSql(ReceiptItem::getReceiptId,
                    "SELECT id FROM receipt WHERE customer_id = " + customerId
                    + " AND deleted = 0 AND status = 1 ORDER BY receipt_date DESC")
                .orderByDesc(ReceiptItem::getId)
                .last("LIMIT 500")
        );
        // 查询关联的收货单以获取日期和备注
        List<Long> receiptIds = items.stream()
            .map(ReceiptItem::getReceiptId)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, Receipt> receiptMap = new HashMap<>();
        if (!receiptIds.isEmpty()) {
            for (Receipt r : receiptMapper.selectBatchIds(receiptIds)) {
                receiptMap.put(r.getId(), r);
            }
        }
        // 按 receiptId 分组
        Map<Long, List<ReceiptItem>> grouped = items.stream()
            .collect(Collectors.groupingBy(ReceiptItem::getReceiptId, java.util.LinkedHashMap::new, Collectors.toList()));
        // 组装分组结果：每个收货单一个对象，包含 items 子数组
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<ReceiptItem>> entry : grouped.entrySet()) {
            Long rid = entry.getKey();
            List<ReceiptItem> rItems = entry.getValue();
            Receipt receipt = receiptMap.get(rid);
            Map<String, Object> group = new HashMap<>();
            group.put("receiptId", rid);
            group.put("receiptNo", rItems.get(0).getReceiptNo());
            group.put("receiptDate", receipt != null && receipt.getReceiptDate() != null ? receipt.getReceiptDate().toString() : null);
            group.put("remark", receipt != null ? receipt.getRemark() : null);
            List<Map<String, Object>> itemMaps = new ArrayList<>();
            for (ReceiptItem item : rItems) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", item.getId());
                m.put("materialId", item.getMaterialId());
                m.put("materialName", item.getMaterialName());
                m.put("materialCode", item.getMaterialCode());
                m.put("spec", item.getSpec());
                m.put("processId", item.getProcessId());
                m.put("processName", item.getProcessName());
                m.put("receiptSource", item.getReceiptSource());
                m.put("quantity", item.getQuantity());
                m.put("unshippedQty", item.getUnshippedQty());
                m.put("unitPrice", item.getUnitPrice());
                m.put("customerOrderNo", item.getCustomerOrderNo());
                m.put("detailRemark", item.getDetailRemark());
                itemMaps.add(m);
            }
            group.put("items", itemMaps);
            result.add(group);
        }
        // 按收货日期倒序排序
        result.sort((a, b) -> {
            String da = (String) a.get("receiptDate");
            String db = (String) b.get("receiptDate");
            if (da == null && db == null) return 0;
            if (da == null) return 1;
            if (db == null) return -1;
            return db.compareTo(da);
        });
        return result;
    }
}
