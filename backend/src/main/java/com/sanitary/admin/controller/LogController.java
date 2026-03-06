package com.sanitary.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.sanitary.admin.common.Result;
import com.sanitary.admin.entity.SysLog;
import com.sanitary.admin.service.SysLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class LogController {
    private final SysLogService sysLogService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','LOG_VIEW')")
    public Result<IPage<SysLog>> page(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String operation) {
        return Result.success(sysLogService.pageLogs(page, size, username, operation));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LOG_VIEW')")
    public Result<SysLog> getById(@PathVariable Long id) {
        SysLog log = sysLogService.getById(id);
        if (log == null) return Result.error(404, "日志不存在");
        return Result.success(log);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','LOG_DELETE')")
    public Result<Void> delete(@PathVariable Long id) {
        sysLogService.removeById(id);
        return Result.success();
    }
}
