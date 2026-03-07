package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerServiceImpl extends ServiceImpl<CustomerMapper, Customer> implements CustomerService {

    @Override
    public Page<Customer> pageList(int page, int size, String keyword, String customerType) {
        LambdaQueryWrapper<Customer> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Customer::getCustomerName, keyword)
                    .or().like(Customer::getCustomerCode, keyword));
        }
        if (StringUtils.hasText(customerType)) {
            wrapper.eq(Customer::getCustomerType, customerType);
        }
        wrapper.orderByDesc(Customer::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Map<String, Object>> listAll() {
        return list(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, 1)
                .select(Customer::getId, Customer::getCustomerName)
                .orderByAsc(Customer::getCustomerCode))
                .stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getCustomerName());
                    return m;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> listAllByType(String customerType) {
        return list(new LambdaQueryWrapper<Customer>()
                .eq(Customer::getStatus, 1)
                .eq(StringUtils.hasText(customerType), Customer::getCustomerType, customerType)
                .select(Customer::getId, Customer::getCustomerName)
                .orderByAsc(Customer::getCustomerCode))
                .stream()
                .map(c -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", c.getId());
                    m.put("name", c.getCustomerName());
                    return m;
                })
                .collect(Collectors.toList());
    }
}
