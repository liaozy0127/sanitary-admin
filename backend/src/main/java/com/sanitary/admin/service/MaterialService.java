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
    /** 新增物料，自动处理编码重复和软删除恢复。返回 null 表示成功，返回错误提示字符串表示失败 */
    String checkAndCreate(Material material);
    Map<String, Object> importFromExcel(MultipartFile file);
    void exportExcel(HttpServletResponse response, String keyword, Long customerId);
    /** 更新物料档案，并同步更新收货/排产/发货单明细中的冗余字段 */
    void updateMaterial(Material material);
}
