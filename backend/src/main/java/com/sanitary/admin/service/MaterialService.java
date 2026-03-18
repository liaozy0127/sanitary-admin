package com.sanitary.admin.service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Material;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public interface MaterialService extends IService<Material> {
    Page<Material> pageList(int page, int size, String keyword, Long customerId);
    List<Map<String, Object>> search(String keyword, Long customerId);
    Map<String, Object> importFromExcel(MultipartFile file);
    void exportExcel(HttpServletResponse response, String keyword, Long customerId, Integer status);
}
