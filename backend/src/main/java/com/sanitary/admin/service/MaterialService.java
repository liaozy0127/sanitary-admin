package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Material;

import java.util.List;
import java.util.Map;

public interface MaterialService extends IService<Material> {
    Page<Material> pageList(int page, int size, String keyword, Long customerId);
    List<Map<String, Object>> search(String keyword, Long customerId);
}
