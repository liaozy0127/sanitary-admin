package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Payment;
import com.sanitary.admin.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping
    public Result<Page<Payment>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return Result.success(paymentService.pageList(page, size, customerId, startDate, endDate));
    }

    @GetMapping("/{id}")
    public Result<Payment> getById(@PathVariable Long id) {
        return Result.success(paymentService.getById(id));
    }

    @PostMapping
    public Result<Payment> create(@RequestBody @Valid Payment payment) {
        return Result.success(paymentService.createPayment(payment));
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Payment payment) {
        payment.setId(id);
        paymentService.updateById(payment);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        paymentService.removeById(id);
        return Result.success();
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.success(paymentService.importExcel(file));
    }

    @GetMapping("/export")
    public void export(HttpServletResponse response,
                       @RequestParam(required = false) Long customerId,
                       @RequestParam(required = false) String startDate,
                       @RequestParam(required = false) String endDate) {
        paymentService.exportExcel(response, customerId, startDate, endDate);
    }
}
