package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Payment;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

public interface PaymentService extends IService<Payment> {
    Page<Payment> pageList(int page, int size, Long customerId, String startDate, String endDate);
    Payment createPayment(Payment payment);
    Map<String, Object> importExcel(MultipartFile file);
    void exportExcel(HttpServletResponse response, Long customerId, String startDate, String endDate);
}
