package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.SysConfig;
import com.sanitary.admin.mapper.SysConfigMapper;
import com.sanitary.admin.service.SysConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SysConfigServiceImpl extends ServiceImpl<SysConfigMapper, SysConfig> implements SysConfigService {

    @Override
    public Map<String, String> getPrintConfig() {
        Map<String, String> result = new HashMap<>();
        result.put("factoryName", getConfigValue("print.factory_name", ""));
        result.put("makerName",   getConfigValue("print.maker_name", ""));
        return result;
    }

    @Override
    public void savePrintConfig(String factoryName, String makerName) {
        setConfigValue("print.factory_name", factoryName);
        setConfigValue("print.maker_name",   makerName);
    }

    private String getConfigValue(String key, String defaultValue) {
        SysConfig cfg = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key).last("LIMIT 1"));
        return cfg != null && cfg.getConfigValue() != null ? cfg.getConfigValue() : defaultValue;
    }

    private void setConfigValue(String key, String value) {
        SysConfig cfg = getOne(new LambdaQueryWrapper<SysConfig>().eq(SysConfig::getConfigKey, key).last("LIMIT 1"));
        if (cfg == null) {
            cfg = new SysConfig();
            cfg.setConfigKey(key);
        }
        cfg.setConfigValue(value);
        saveOrUpdate(cfg);
    }
}
