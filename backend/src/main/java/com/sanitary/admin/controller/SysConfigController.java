package com.sanitary.admin.controller;

import com.sanitary.admin.common.Result;
import com.sanitary.admin.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigService sysConfigService;

    @GetMapping("/print")
    public Result<Map<String, String>> getPrintConfig() {
        return Result.success(sysConfigService.getPrintConfig());
    }

    @PutMapping("/print")
    public Result<Void> savePrintConfig(@RequestBody Map<String, String> body) {
        String factoryName = body.getOrDefault("factoryName", "");
        String makerName   = body.getOrDefault("makerName", "");
        sysConfigService.savePrintConfig(factoryName, makerName);
        return Result.success();
    }
}
