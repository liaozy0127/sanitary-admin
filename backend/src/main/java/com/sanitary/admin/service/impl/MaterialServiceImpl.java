package com.sanitary.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sanitary.admin.entity.Material;
import com.sanitary.admin.entity.Customer;
import com.sanitary.admin.mapper.MaterialMapper;
import com.sanitary.admin.mapper.CustomerMapper;
import com.sanitary.admin.service.MaterialService;
import com.sanitary.admin.util.ExcelExportUtil;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
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
    public String checkAndCreate(Material material) {
        String code = material.getMaterialCode() == null ? "" : material.getMaterialCode().trim();
        // 查找所有（含软删除）相同编码的记录
        Material existing = baseMapper.findByCodeIgnoreDeleted(code);
        if (existing != null) {
            if (existing.getDeleted() == null || existing.getDeleted() == 0) {
                // 活跃记录已存在
                return "物料代码「" + code + "」已存在，请勿重复添加";
            } else {
                // 软删除记录，恢复并覆盖字段
                material.setId(existing.getId());
                material.setCreateTime(existing.getCreateTime());
                baseMapper.restoreAndUpdate(material);
                return null;
            }
        }
        material.setStatus(1);
        save(material);
        return null;
    }


    @Override
    public Map<String, Object> importFromExcel(MultipartFile file) {
        // 老系统物料档案列映射:
        // col 0: 物料代码, col 1: 物料名称, col 2: 规格型号,
        // col 3: 客户名称, col 4: 更新时间, col 5: 创建时间, col 6: 单价
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
                    String materialCode = getCellString(row, 0); // col 0: 物料代码
                    String materialName = getCellString(row, 1); // col 1: 物料名称
                    if (materialCode.isEmpty() && materialName.isEmpty()) {
                        skip++;
                        continue;
                    }
                    // 物料代码为空时跳过（无法去重）
                    if (materialCode.isEmpty()) {
                        fail++;
                        errors.add("第" + (i + 1) + "行[" + materialName + "]: 物料代码为空，跳过");
                        continue;
                    }
                    // 物料名称为空时用物料代码作为名称
                    if (materialName.isEmpty()) {
                        materialName = materialCode;
                    }

                    String customerName = getCellString(row, 3); // col 3: 客户名称
                    Long customerId = null;
                    if (!customerName.isEmpty()) {
                        Customer customer = customerMapper.selectOne(
                            new LambdaQueryWrapper<Customer>()
                                .eq(Customer::getCustomerName, customerName.trim())
                                .last("LIMIT 1")
                        );
                        if (customer != null) {
                            customerId = customer.getId();
                        } else {
                            // 客户不存在时记录警告，但仍导入物料（customer_id=null）
                            errors.add("警告 第" + (i + 1) + "行: 客户「" + customerName + "」在客户表中不存在，物料已导入但未关联客户");
                        }
                    }

                    Material material = new Material();
                    material.setMaterialCode(materialCode);
                    material.setMaterialName(materialName);
                    material.setSpec(getCellString(row, 2)); // col 2: 规格型号
                    material.setCustomerId(customerId);
                    material.setCustomerName(customerName);

                    String priceStr = getPriceCellString(row, 6); // col 6: 单价
                    if (!priceStr.isEmpty()) {
                        try {
                            material.setDefaultPrice(new BigDecimal(priceStr));
                        } catch (NumberFormatException e) {
                            material.setDefaultPrice(BigDecimal.ZERO);
                        }
                    }

                    // 去重：按 material_code 查找（物料代码全局唯一），存在则更新，不存在则新增
                    Material existingMaterial = this.getOne(
                        new LambdaQueryWrapper<Material>()
                            .eq(Material::getMaterialCode, materialCode)
                            .last("LIMIT 1")
                    );

                    if (existingMaterial != null) {
                        material.setId(existingMaterial.getId());
                        material.setCreateTime(existingMaterial.getCreateTime());
                        updateById(material);
                        success++;
                    } else {
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

    // 专用于读取单价列（保留小数，不强转 long）
    private String getPriceCellString(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> new java.math.BigDecimal(cell.getNumericCellValue())
                .setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
            case FORMULA -> {
                try {
                    yield new java.math.BigDecimal(cell.getNumericCellValue())
                        .setScale(4, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
                } catch (Exception e) { yield cell.getStringCellValue().trim(); }
            }
            default -> "";
        };
    }

    @Override
    public void exportExcel(HttpServletResponse response, String keyword, Long customerId) {
        LambdaQueryWrapper<Material> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like(Material::getMaterialName, keyword).or().like(Material::getMaterialCode, keyword));
        }
        if (customerId != null) wrapper.eq(Material::getCustomerId, customerId);
        wrapper.orderByDesc(Material::getCreateTime).last("LIMIT 50000");
        List<Material> list = this.list(wrapper);

        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("物料档案");
        String[] headers = {"物料代码","物料名称","规格型号","所属客户","默认单价","单位","状态"};
        ExcelExportUtil.writeTitleRow(sheet, wb, "物料档案", headers.length);
        ExcelExportUtil.writeHeaderRow(sheet, wb, headers);

        CellStyle s0 = ExcelExportUtil.dataStyle(wb, false);
        CellStyle s1 = ExcelExportUtil.dataStyle(wb, true);
        CellStyle n0 = ExcelExportUtil.numStyle(wb, false);
        CellStyle n1 = ExcelExportUtil.numStyle(wb, true);

        int rowIdx = 2;
        for (Material m : list) {
            boolean even = (rowIdx % 2 == 0);
            CellStyle s = even ? s1 : s0;
            CellStyle ns = even ? n1 : n0;
            Row row = sheet.createRow(rowIdx++);
            ExcelExportUtil.setCell(row, 0, m.getMaterialCode(), s);
            ExcelExportUtil.setCell(row, 1, m.getMaterialName(), s);
            ExcelExportUtil.setCell(row, 2, m.getSpec(), s);
            ExcelExportUtil.setCell(row, 3, m.getCustomerName(), s);
            ExcelExportUtil.setCell(row, 4, m.getDefaultPrice(), ns);
            ExcelExportUtil.setCell(row, 5, m.getUnit(), s);
            ExcelExportUtil.setCell(row, 6, m.getStatus() != null && m.getStatus() == 1 ? "启用" : "禁用", s);
        }

        sheet.createFreezePane(0, 2);
        ExcelExportUtil.autoSize(sheet, headers.length);
        try {
            String today = java.time.LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            ExcelExportUtil.writeResponse(wb, response, "物料档案_" + today + ".xlsx");
        } catch (IOException e) {
            throw new RuntimeException("导出失败: " + e.getMessage());
        }
    }
}
