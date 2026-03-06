package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysLog;
import com.sanitary.admin.mapper.SysLogMapper;
import com.sanitary.admin.service.SysLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysLogServiceImpl extends ServiceImpl<SysLogMapper, SysLog> implements SysLogService {
    @Override
    public IPage<SysLog> pageLogs(int page, int size, String username, String operation) {
        LambdaQueryWrapper<SysLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) wrapper.like(SysLog::getUsername, username);
        if (StringUtils.hasText(operation)) wrapper.like(SysLog::getOperation, operation);
        wrapper.orderByDesc(SysLog::getCreateTime);
        return this.page(new Page<>(page, size), wrapper);
    }
}
