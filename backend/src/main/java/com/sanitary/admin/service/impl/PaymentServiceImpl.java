package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Payment;
import com.sanitary.admin.mapper.PaymentMapper;
import com.sanitary.admin.service.PaymentService;
import com.sanitary.admin.util.GenerateNoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl extends ServiceImpl<PaymentMapper, Payment> implements PaymentService {

    private final GenerateNoUtil generateNoUtil;

    @Override
    public Page<Payment> pageList(int page, int size, Long customerId, String startDate, String endDate) {
        LambdaQueryWrapper<Payment> wrapper = new LambdaQueryWrapper<>();
        if (customerId != null) {
            wrapper.eq(Payment::getCustomerId, customerId);
        }
        if (StringUtils.hasText(startDate)) {
            wrapper.ge(Payment::getPaymentDate, LocalDate.parse(startDate));
        }
        if (StringUtils.hasText(endDate)) {
            wrapper.le(Payment::getPaymentDate, LocalDate.parse(endDate));
        }
        wrapper.orderByDesc(Payment::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional
    public Payment createPayment(Payment payment) {
        payment.setPaymentNo(generateNoUtil.generate("SK", "payment", "payment_no"));
        if (!StringUtils.hasText(payment.getPaymentMethod())) {
            payment.setPaymentMethod("银行转账");
        }
        save(payment);
        return payment;
    }
}
