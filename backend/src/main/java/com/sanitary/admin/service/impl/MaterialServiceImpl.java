package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MaterialServiceImpl extends ServiceImpl<MaterialMapper, Material> implements MaterialService {

    private final CustomerMapper customerMapper;

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
            // 有关键词时限制100条
            wrapper.last("LIMIT 100");
        } else {
            // 无关键词：默认加载前100条（前端用于初始展示）
            wrapper.last("LIMIT 100");
        }
        if (customerId != null) {
            wrapper.eq(Material::getCustomerId, customerId);
        }
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

    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        int success = 0;
        int fail = 0;
        int skip = 0;
        List<String> errors = new ArrayList<>();

        try (java.io.InputStream is = file.getInputStream();
             Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String materialCode = getCellString(row, 0); // 物料代码
                    if (materialCode.isEmpty()) {
                        fail++;
                        errors.add("第" + (i + 1) + "行: 物料代码不能为空");
                        continue;
                    }

                    String customerName = getCellString(row, 3); // 客户名称
                    Long customerId = null;
                    if (!customerName.isEmpty()) {
                        Customer customer = customerMapper.selectOne(
                            new LambdaQueryWrapper<Customer>()
                                .eq(Customer::getCustomerName, customerName.trim())
                        );
                        if (customer != null) {
                            customerId = customer.getId();
                        } else {
                            fail++;
                            errors.add("第" + (i + 1) + "行: 客户「" + customerName + "」不存在");
                            continue;
                        }
                    }

                    Material material = new Material();
                    material.setMaterialCode(materialCode);
                    material.setMaterialName(getCellString(row, 1)); // 物料名称
                    material.setSpec(getCellString(row, 2)); // 规格型号
                    material.setCustomerId(customerId);
                    material.setCustomerName(customerName);

                    String priceStr = getCellString(row, 4); // 单价
                    if (!priceStr.isEmpty()) {
                        try {
                            material.setDefaultPrice(new BigDecimal(priceStr));
                        } catch (NumberFormatException e) {
                            material.setDefaultPrice(BigDecimal.ZERO);
                        }
                    }

                    // Check for duplicates (idempotency) - if exists, update instead of skipping
                    Material existingMaterial = this.getOne(
                        new LambdaQueryWrapper<Material>()
                            .eq(Material::getMaterialCode, materialCode)
                    );

                    if (existingMaterial != null) {
                        // Update existing material (not skip)
                        material.setId(existingMaterial.getId());
                        material.setCreateTime(existingMaterial.getCreateTime()); // Preserve creation time
                        updateById(material);
                        success++; // Count as success for updating
                    } else {
                        // Create new material
                        material.setStatus(1);
                        material.setCreateTime(LocalDateTime.now());
                        material.setUpdateTime(LocalDateTime.now());
                        save(material);
                        success++;
                    }
                } catch (Exception e) {
                    fail++;
                    errors.add("第" + (i + 1) + "行: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Excel解析失败: " + e.getMessage());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("success", success);
        result.put("fail", fail);
        result.put("skip", skip);
        result.put("errors", errors);
        return result;
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                ? cell.getLocalDateTimeCellValue().toLocalDate().toString()
                : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield String.valueOf((long) cell.getNumericCellValue()); }
                catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }
}
