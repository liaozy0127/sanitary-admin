package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Production;

public interface ProductionService extends IService<Production> {
    Page<Production> pageList(int page, int size, String keyword, Long customerId, String prodStatus);
    Production createProduction(Production production);
}
