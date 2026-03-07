package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Payment;

public interface PaymentService extends IService<Payment> {
    Page<Payment> pageList(int page, int size, Long customerId, String startDate, String endDate);
    Payment createPayment(Payment payment);
}
