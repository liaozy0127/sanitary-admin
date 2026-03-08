package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.mapper.ReceiptItemMapper;
import com.sanitary.admin.service.ReceiptItemService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ReceiptItemServiceImpl extends ServiceImpl<ReceiptItemMapper, ReceiptItem> implements ReceiptItemService {

    @Override
    public List<ReceiptItem> listByReceiptId(Long receiptId) {
        return list(new LambdaQueryWrapper<ReceiptItem>()
                .eq(ReceiptItem::getReceiptId, receiptId)
                .orderByAsc(ReceiptItem::getId));
    }

    @Override
    @Transactional
    public void saveItems(Long receiptId, String receiptNo, List<ReceiptItem> items) {
        if (items == null || items.isEmpty()) return;
        for (ReceiptItem item : items) {
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
}
