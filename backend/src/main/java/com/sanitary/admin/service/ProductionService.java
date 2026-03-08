package com.sanitary.admin.service;
import java.util.Map;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Production;

public interface ProductionService extends IService<Production> {
    Page<Production> pageList(int page, int size, String keyword, Long customerId, String prodStatus);
    Production createProduction(Production production);
    Map<String, Object> importFromExcel(MultipartFile file, String mode);
}
