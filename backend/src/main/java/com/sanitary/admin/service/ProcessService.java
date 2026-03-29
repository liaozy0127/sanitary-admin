package com.sanitary.admin.service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Process;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

public interface ProcessService extends IService<Process> {
    Page<Process> pageList(int page, int size, String keyword);
    List<Map<String, Object>> listAll();
    /** 新增工艺，自动处理编码重复和软删除恢复。返回 null 表示成功，返回错误提示字符串表示失败 */
    String checkAndCreate(Process process);
    Map<String, Object> importFromExcel(MultipartFile file);
    void exportExcel(HttpServletResponse response, String keyword);
}
