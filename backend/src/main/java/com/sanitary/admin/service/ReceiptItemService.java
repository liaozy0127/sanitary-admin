package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.ReceiptItem;

import java.util.List;

public interface ReceiptItemService extends IService<ReceiptItem> {
    List<ReceiptItem> listByReceiptId(Long receiptId);
    void saveItems(Long receiptId, String receiptNo, List<ReceiptItem> items);
    void deleteByReceiptId(Long receiptId);
    /** 查询该客户+物料最新收货单里的工艺信息（processId + processName），没有则返回 null */
    ReceiptItem getLatestProcessByMaterial(Long customerId, Long materialId);
}
