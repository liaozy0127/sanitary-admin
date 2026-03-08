package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.ReceiptItem;

import java.util.List;

public interface ReceiptItemService extends IService<ReceiptItem> {
    List<ReceiptItem> listByReceiptId(Long receiptId);
    void saveItems(Long receiptId, String receiptNo, List<ReceiptItem> items);
    void deleteByReceiptId(Long receiptId);
}
