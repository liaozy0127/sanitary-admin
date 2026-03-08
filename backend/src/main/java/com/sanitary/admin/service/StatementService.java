package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Statement;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface StatementService extends IService<Statement> {
    Page<Statement> pageList(int page, int size, Long customerId, String statementMonth);
    Statement generate(Long customerId, String statementMonth);
    void confirm(Long id);
    Statement getByIdWithItems(Long id);
    Map<String, Object> importExcel(MultipartFile file, Long customerId, String statementMonth, Boolean initInventory);
}
