package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Statement;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

public interface StatementService extends IService<Statement> {
    Page<Statement> pageList(int page, int size, Long customerId, String statementMonth);
    Statement generate(Long customerId, String statementMonth);
    Statement getByIdWithItems(Long id);
    Map<String, Object> importExcel(MultipartFile file, Long customerId, String statementMonth, Boolean initInventory);
    /** 批量生成对账单：遍历所有有收货或发货数据的客户×月份组合，跳过已存在的 */
    Map<String, Object> generateAll();
    void exportExcel(HttpServletResponse response, Long customerId, String statementMonth);
}
