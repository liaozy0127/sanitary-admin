package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Receipt;
import com.sanitary.admin.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/receipts")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @GetMapping
    public Result<Page<Receipt>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(receiptService.pageList(page, size, keyword, customerId, startDate, endDate));
    }

    @GetMapping("/{id}")
    public Result<Receipt> getById(@PathVariable Long id) {
        return Result.success(receiptService.getById(id));
    }

    @PostMapping
    public Result<Receipt> create(@RequestBody @Valid Receipt receipt) {
        return Result.success(receiptService.createReceipt(receipt));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Receipt receipt) {
        receipt.setId(id);
        // Recalculate amount
        if (receipt.getQuantity() != null && receipt.getUnitPrice() != null) {
            receipt.setAmount(receipt.getQuantity().multiply(receipt.getUnitPrice()));
        }
        receiptService.updateById(receipt);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        // Void (status=0)
        Receipt receipt = new Receipt();
        receipt.setId(id);
        receipt.setStatus(0);
        receiptService.updateById(receipt);
        return Result.success();
    }

    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        receiptService.exportTemplate(response);
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "normal") String mode) {
        return Result.success(receiptService.importExcel(file, mode));
    }
}
