package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {

    @Override
    public Page<Material> pageList(int page, int size, String keyword, Long customerId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Material::getMaterialName, keyword)
                    .or().like(Material::getMaterialCode, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Material::getCustomerId, customerId);
        }
        wrapper.orderByDesc(Material::getCreateTime);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public List<Map<String, Object>> search(String keyword, Long customerId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Material::getStatus, 1);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Material::getMaterialName, keyword)
                    .or().like(Material::getMaterialCode, keyword));
        }
        if (customerId != null) {
            wrapper.eq(Material::getCustomerId, customerId);
        }
        wrapper.last("LIMIT 50");
        return list(wrapper).stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getId());
            map.put("name", m.getMaterialName());
            map.put("code", m.getMaterialCode());
            map.put("spec", m.getSpec());
            map.put("defaultPrice", m.getDefaultPrice());
            return map;
        }).collect(Collectors.toList());
    }
}
