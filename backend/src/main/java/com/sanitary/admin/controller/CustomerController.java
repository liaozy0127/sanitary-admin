package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping
    public Result<Page<Customer>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType) {
        return Result.success(customerService.pageList(page, size, keyword, customerType));
    }

    @GetMapping("/all")
    public Result<List<Map<String, Object>>> listAll(
            @RequestParam(required = false) String customerType) {
        if (customerType != null && !customerType.isEmpty()) {
            return Result.success(customerService.listAllByType(customerType));
        }
        return Result.success(customerService.listAll());
    }

    @PostMapping
    public Result<Void> create(@RequestBody @Valid Customer customer) {
        customerService.save(customer);
        return Result.success();
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Customer customer) {
        customer.setId(id);
        customerService.updateById(customer);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        customerService.removeById(id);
        return Result.success();
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        Customer customer = new Customer();
        customer.setId(id);
        customer.setStatus(status);
        customerService.updateById(customer);
        return Result.success();
    }

    @PostMapping("/import")
    public Result<Map<String, Object>> importExcel(@RequestParam("file") MultipartFile file) {
        return Result.success(customerService.importFromExcel(file));
    }
}
