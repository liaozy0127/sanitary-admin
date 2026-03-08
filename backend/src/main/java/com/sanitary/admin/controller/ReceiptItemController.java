package com.sanitary.admin.controller;

import com.sanitary.admin.entity.ReceiptItem;
import com.sanitary.admin.service.ReceiptItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/receipt-items")
@RequiredArgsConstructor
public class ReceiptItemController {

    private final ReceiptItemService receiptItemService;

    @GetMapping
    public List<ReceiptItem> getReceiptItems(@RequestParam Long receiptId) {
        return receiptItemService.listByReceiptId(receiptId);
    }
}