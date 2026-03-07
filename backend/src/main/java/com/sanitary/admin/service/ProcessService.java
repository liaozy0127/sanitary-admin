package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Process;

import java.util.List;
import java.util.Map;

public interface ProcessService extends IService<Process> {
    Page<Process> pageList(int page, int size, String keyword);
    List<Map<String, Object>> listAll();
}
