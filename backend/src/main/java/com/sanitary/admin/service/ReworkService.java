package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.Rework;

public interface ReworkService extends IService<Rework> {
    Page<Rework> pageList(int page, int size, String keyword, Long customerId, String reworkStatus);
    Rework createRework(Rework rework);
}
