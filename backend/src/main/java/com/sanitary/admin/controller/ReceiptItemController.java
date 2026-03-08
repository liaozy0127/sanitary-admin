package com.sanitary.admin.controller;

import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.service.ReceiptItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/receipt-items")
@RequiredArgsConstructor
public class ReceiptItemController {

    private final ReceiptItemService receiptItemService;

    @GetMapping
    public List<ReceiptItem> getReceiptItems(@RequestParam Long receiptId) {
        return receiptItemService.listByReceiptId(receiptId);
    }

    /**
     * 查询某客户+物料最近收货单里的工艺，用于新建单据时自动带出工艺
     */
    @GetMapping("/latest-process")
    public Map<String, Object> getLatestProcess(@RequestParam Long customerId,
                                                 @RequestParam Long materialId) {
        Map<String, Object> result = new HashMap<>();
        ReceiptItem item = receiptItemService.getLatestProcessByMaterial(customerId, materialId);
        if (item != null && item.getProcessId() != null && item.getProcessId() != 0) {
            result.put("processId", item.getProcessId());
            result.put("processName", item.getProcessName());
        }
        return result;
    }
}