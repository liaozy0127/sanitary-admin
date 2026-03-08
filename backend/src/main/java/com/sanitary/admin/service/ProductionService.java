package com.sanitary.admin.service;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Production;

public interface ProductionService extends IService<Production> {
    Page<Production> pageList(int page, int size, String keyword, Long customerId, String prodStatus);
    // Note: prodStatus param retained for API compatibility but no longer stored on main entity
    Production createProduction(Production production);
    Production updateProduction(Production production);
    boolean deleteProduction(Long id);
    Map<String, Object> importFromExcel(MultipartFile file, String mode);
}
