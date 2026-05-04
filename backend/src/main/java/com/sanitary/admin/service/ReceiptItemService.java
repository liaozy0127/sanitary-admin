package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.ReceiptItem;

import java.util.List;
import java.util.Map;

public interface ReceiptItemService extends IService<ReceiptItem> {
    List<ReceiptItem> listByReceiptId(Long receiptId);
    List<ReceiptItem> listByReceiptIds(List<Long> receiptIds);
    void saveItems(Long receiptId, String receiptNo, List<ReceiptItem> items);
    void deleteByReceiptId(Long receiptId);
    /** 查询该客户+物料最新收货单里的工艺信息（processId + processName），没有则返回 null */
    ReceiptItem getLatestProcessByMaterial(Long customerId, Long materialId);
    /** 查询该客户+物料+工艺最新收货单里的单价，没有则返回 null */
    ReceiptItem getLatestPrice(Long customerId, Long materialId, Long processId);
    /** 按客户ID查询收货明细（按收货日期倒序），用于排产单快速填充 */
    List<Map<String, Object>> listByCustomerId(Long customerId);
}
