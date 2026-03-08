package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Customer;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface CustomerService extends IService<Customer> {
    Page<Customer> pageList(int page, int size, String keyword, String customerType);
    List<Map<String, Object>> listAll();
    List<Map<String, Object>> listAllByType(String customerType);
    Map<String, Object> importFromExcel(MultipartFile file);
}
