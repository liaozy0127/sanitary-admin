package com.sanitary.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sanitary.admin.entity.SysConfig;

import java.util.Map;

public interface SysConfigService extends IService<SysConfig> {
    Map<String, String> getPrintConfig();
    void savePrintConfig(String factoryName, String makerName);
}
