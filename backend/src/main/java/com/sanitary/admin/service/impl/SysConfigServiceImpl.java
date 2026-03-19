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
        result.put("factoryName",            getConfigValue("print.factory_name", ""));
        result.put("makerName",              getConfigValue("print.maker_name", ""));
        result.put("printTitleProduction",   getConfigValue("print.title_production", "致恒（致越）金属表面加工厂生产安排表"));
        result.put("printTitleDelivery",     getConfigValue("print.title_delivery", "致恒（致越）金属表面加工厂送货单"));
        result.put("printCompanyName",       getConfigValue("print.company_name", "致恒（致越）金属表面加工厂"));
        result.put("printCompanyPhone",      getConfigValue("print.company_phone", "0750-2766036"));
        result.put("printCompanyAddress",    getConfigValue("print.company_address", "开平市，水口镇，唐良良兴村矮岗山"));
        result.put("printContact1",          getConfigValue("print.contact_1", "廖总：13536094788"));
        result.put("printContact2",          getConfigValue("print.contact_2", "仓管：13672842611"));
        result.put("printSignature1Label",   getConfigValue("print.signature_1_label", "生产班长"));
        result.put("printSignature2Label",   getConfigValue("print.signature_2_label", "仓管"));
        result.put("printSignature3Label",   getConfigValue("print.signature_3_label", "签名"));
        result.put("printMakerLabel",        getConfigValue("print.maker_label", "制单人"));
        result.put("printDeliveryRemark",    getConfigValue("print.delivery_remark", "1. 货到当场验收，签收后概不负责\n2. 如有质量问题，3天内退货\n3. 本单据一式三联（客户、财务、仓库各一联）"));
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
