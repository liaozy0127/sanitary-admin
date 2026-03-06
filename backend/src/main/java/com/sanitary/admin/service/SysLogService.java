package com.sanitary.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.SysLog;

public interface SysLogService extends IService<SysLog> {
    IPage<SysLog> pageLogs(int page, int size, String username, String operation);
}
