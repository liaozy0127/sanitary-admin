package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Customer;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public interface CustomerService extends IService<Customer> {
    Page<Customer> pageList(int page, int size, String keyword, String customerType);
    List<Map<String, Object>> listAll();
    List<Map<String, Object>> listAllByType(String customerType);
    /** 新增客户，自动处理编码重复和软删除恢复。返回 null 表示成功，返回错误提示字符串表示失败 */
    String checkAndCreate(Customer customer);
    Map<String, Object> importFromExcel(MultipartFile file);
    void exportExcel(HttpServletResponse response, String keyword, String customerType);
}
